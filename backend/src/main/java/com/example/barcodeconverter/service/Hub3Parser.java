package com.example.barcodeconverter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the Croatian HUB-3 (HUB 3.0 / 3.02) payment slip barcode content
 * — the format used in the PDF417 barcode printed on Croatian "uplatnice"
 * — and converts it into an EPC QR Code (EPC069-12, a.k.a. SEPA Credit
 * Transfer / GiroCode) text, which is the format banking apps such as
 * Revolut, N26, ING, etc. actually recognize.
 * <p>
 * HUB-3 field order (each field on its own line):
 * 0. HRVHUB30 (fixed header)
 * 1. Currency (e.g. EUR)
 * 2. Amount - 15 numeric chars, no separators, last 2 digits are decimals
 * 3. Payer name
 * 4. Payer address
 * 5. Payer city
 * 6. Recipient name
 * 7. Recipient address
 * 8. Recipient city
 * 9. Recipient IBAN
 * 10. Model (e.g. HR01)
 * 11. Poziv na broj primatelja (reference number)
 * 12. Šifra namjene (purpose code)
 * 13. Opis plaćanja (description)
 */
public final class Hub3Parser {

    private static final String HUB3_HEADER = "HRVHUB30";

    /**
     * Official HNB (Croatian National Bank) bank-code -> BIC table.
     * https://www.hnb.hr/en/most-viewed/-/most_viewed_assets/fHw5lCdsj12P/content/vodeci-brojevi-banaka
     * The bank code is IBAN characters 5-11 (0-indexed 4..10, 7 digits)
     * for Croatian IBANs (HR + 2 check digits + 7-digit bank code + 10-digit account number).
     */
    private static final Map<String, String> BANK_CODE_TO_BIC = Map.ofEntries(
            Map.entry("2500009", "HAABHR22"), // Addiko Bank
            Map.entry("2481000", "KREZHR2X"), // Agram banka
            Map.entry("4133006", "SKOVHR22"), // Banka Kovanica
            Map.entry("2488001", "BFKKHR22"), // BKS Bank
            Map.entry("2485003", "CROAHR2X"), // Croatia banka
            Map.entry("2402006", "ESBCHR22"), // Erste & Steiermärkische Bank
            Map.entry("1001005", "NBHRHR2X"), // Hrvatska narodna banka
            Map.entry("2390001", "HPBZHR2X"), // Hrvatska poštanska banka
            Map.entry("2492008", "IMXXHR22"), // Imex banka
            Map.entry("2380006", "ISKBHR2X"), // Istarska kreditna banka Umag
            Map.entry("2489004", "VBVZHR22"), // J&T banka
            Map.entry("2400008", "KALCHR2X"), // Karlovačka banka
            Map.entry("4124003", "KENBHR22"), // KentBank
            Map.entry("2407000", "OTPVHR2X"), // OTP banka
            Map.entry("2408002", "PAZGHR2X"), // Partner banka
            Map.entry("2386002", "PDKCHR2X"), // Podravska banka
            Map.entry("2340009", "PBZGHR2X"), // Privredna banka Zagreb
            Map.entry("2484008", "RZBHHR2X"), // Raiffeisenbank Austria
            Map.entry("2403009", "SMBRHR22"), // Samoborska banka
            Map.entry("2412009", "SBSLHR2X"), // Slatinska banka
            Map.entry("2360000", "ZABAHR2X")  // Zagrebačka banka
    );

    private Hub3Parser() {
    }

    public static boolean isHub3(String text) {
        return text != null && text.trim().startsWith(HUB3_HEADER);
    }

    public static Hub3Data parse(String text) {
        String[] lines = text.split("\r\n|\n|\r");

        String iban = field(lines, 9).replaceAll("\\s", "").toUpperCase();

        return new Hub3Data(
                field(lines, 1),
                parseAmount(field(lines, 2)),
                field(lines, 3),
                field(lines, 4),
                field(lines, 5),
                field(lines, 6),
                field(lines, 7),
                field(lines, 8),
                iban,
                lookupBic(iban),
                field(lines, 10),
                field(lines, 11),
                field(lines, 12),
                field(lines, 13)
        );
    }

    private static String lookupBic(String iban) {
        if (iban == null || !iban.startsWith("HR") || iban.length() < 11) return null;
        String bankCode = iban.substring(4, 11); // 7-digit bank code
        return BANK_CODE_TO_BIC.get(bankCode);
    }

    private static String field(String[] lines, int index) {
        if (index < 0 || index >= lines.length) return "";
        return lines[index] == null ? "" : lines[index].trim();
    }

    /**
     * HUB-3 amount is a zero-padded numeric string where the last two
     * digits represent the decimals, e.g. "000000000012350" -> 123.50
     */
    private static double parseAmount(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0.0;
        while (digits.length() < 3) digits = "0" + digits;
        String whole = digits.substring(0, digits.length() - 2);
        String cents = digits.substring(digits.length() - 2);
        whole = whole.replaceFirst("^0+(?=\\d)", "");
        return Double.parseDouble(whole + "." + cents);
    }

    public record Hub3Data(
            String currency,
            double amount,
            String payerName,
            String payerAddress,
            String payerCity,
            String recipientName,
            String recipientAddress,
            String recipientCity,
            String iban,
            String bic,       // resolved from IBAN via the HNB bank-code table, or null if unknown
            String model,
            String reference,
            String purposeCode,
            String description
    ) {

        public boolean isEur() {
            return "EUR".equalsIgnoreCase(currency);
        }

        public boolean hasIban() {
            return iban != null && !iban.isBlank();
        }

        public boolean hasBic() {
            return bic != null && !bic.isBlank();
        }

        /**
         * Builds the EPC QR Code (EPC069-12) text. Lines, in order:
         * BCD / version / charset / SCT / BIC / name / IBAN / amount /
         * purpose / structured remittance / unstructured remittance.
         * <p>
         * Uses version "001" (BIC required) when the recipient's bank
         * code resolves to a known BIC via the HNB table - this is the
         * variant confirmed to scan correctly in Revolut. Falls back to
         * version "002" (BIC optional) with an empty BIC line if the
         * bank code isn't in the table.
         * <p>
         * Purpose is left empty - the Croatian "šifra namjene" codeset
         * doesn't map 1:1 onto the ISO 20022 purpose codes EPC expects.
         * Model + poziv na broj + opis plaćanja are folded into the
         * unstructured remittance line instead, so the reference isn't
         * silently lost.
         */
        public String toEpcQrText() {
            StringBuilder sb = new StringBuilder();
            sb.append("BCD").append('\n');
            sb.append(hasBic() ? "001" : "002").append('\n');
            sb.append("1").append('\n'); // 1 = UTF-8
            sb.append("SCT").append('\n');
            sb.append(hasBic() ? bic : "").append('\n');
            sb.append(truncate(recipientName, 70)).append('\n');
            sb.append(iban).append('\n');
            sb.append(amount > 0 ? String.format(Locale.US, "EUR%.2f", amount) : "").append('\n');
            sb.append('\n'); // purpose - no reliable HUB-3 -> ISO 20022 mapping
            sb.append('\n'); // structured remittance (ISO 11649) - not used by HUB-3
            sb.append(truncate(buildUnstructuredRemittance(), 140));
            return sb.toString();
        }

        private String buildUnstructuredRemittance() {
            List<String> parts = new ArrayList<>();
            if (reference != null && !reference.isBlank()) {
                String prefix = (model != null && !model.isBlank()) ? model + " " : "";
                parts.add(prefix + reference);
            }
            if (description != null && !description.isBlank()) {
                parts.add(description);
            }
            return String.join(" - ", parts);
        }

        private static String truncate(String value, int maxLen) {
            if (value == null) return "";
            return value.length() <= maxLen ? value : value.substring(0, maxLen);
        }
    }
}
