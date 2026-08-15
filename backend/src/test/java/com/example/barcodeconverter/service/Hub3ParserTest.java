package com.example.barcodeconverter.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Hub3ParserTest {

    // Real-world example: an Aircash top-up slip with an open (zero) amount.
    private static final String AIRCASH_HUB3 = String.join("\n",
            "HRVHUB30",
            "EUR",
            "000000000000000",
            "SIME BARISIC",
            "Vinkovacka ulica 29",
            "23000 Zadar, ZADAR",
            "Aircash d.o.o",
            "Ulica grada Vukovara 271",
            "Zagreb, 10000",
            "HR6423600001300206618",
            "HR00",
            "685191-7300",
            "",
            "Uplata na Aircash novcanik"
    );

    @Test
    void isHub3_recognizesHeader() {
        assertTrue(Hub3Parser.isHub3(AIRCASH_HUB3));
        assertTrue(Hub3Parser.isHub3("  " + AIRCASH_HUB3)); // leading whitespace tolerated
        assertFalse(Hub3Parser.isHub3("nešto sasvim drugo"));
        assertFalse(Hub3Parser.isHub3(null));
    }

    @Test
    void parse_extractsAllFields() {
        Hub3Parser.Hub3Data data = Hub3Parser.parse(AIRCASH_HUB3);

        assertEquals("EUR", data.currency());
        assertEquals(0.0, data.amount());
        assertEquals("Aircash d.o.o", data.recipientName());
        assertEquals("HR6423600001300206618", data.iban());
        assertEquals("HR00", data.model());
        assertEquals("685191-7300", data.reference());
        assertEquals("Uplata na Aircash novcanik", data.description());
        assertTrue(data.isEur());
        assertTrue(data.hasIban());
    }

    @Test
    void parse_resolvesBicFromOfficialHnbTable() {
        // HR64 23600 00130 0206618 -> bank code 2360000 -> Zagrebačka banka
        Hub3Parser.Hub3Data data = Hub3Parser.parse(AIRCASH_HUB3);
        assertEquals("ZABAHR2X", data.bic());
        assertTrue(data.hasBic());
    }

    @Test
    void toEpcQrText_leavesAmountBlankForOpenAmountSlips() {
        Hub3Parser.Hub3Data data = Hub3Parser.parse(AIRCASH_HUB3);
        String epc = data.toEpcQrText();
        String[] lines = epc.split("\n");

        assertEquals("BCD", lines[0]);
        assertEquals("001", lines[1]); // BIC resolved -> version 001
        assertEquals("SCT", lines[3]);
        assertEquals("ZABAHR2X", lines[4]);
        assertEquals("Aircash d.o.o", lines[5]);
        assertEquals("HR6423600001300206618", lines[6]);
        assertEquals("", lines[7]); // open amount -> blank, never "EUR0.00"
        assertTrue(epc.contains("HR00 685191-7300 - Uplata na Aircash novcanik"));
    }

    @Test
    void toEpcQrText_usesDotDecimalSeparatorRegardlessOfDefaultLocale() {
        Hub3Parser.Hub3Data withAmount = new Hub3Parser.Hub3Data(
                "EUR", 123.5, "", "", "",
                "Test primatelj", "", "",
                "HR6423600001300206618", "ZABAHR2X",
                "", "", "", ""
        );
        String epc = withAmount.toEpcQrText();
        assertTrue(epc.contains("EUR123.50"), "Amount must use '.' as decimal separator for EPC compliance");
        assertFalse(epc.contains("EUR123,50"), "Must never emit a comma decimal separator (e.g. under hr_HR locale)");
    }

    @Test
    void toEpcQrText_fallsBackToVersion002WhenBankUnknown() {
        Hub3Parser.Hub3Data foreignIban = new Hub3Parser.Hub3Data(
                "EUR", 10.0, "", "", "",
                "Neki primatelj", "", "",
                "DE89370400440532013000", null, // unresolved BIC
                "", "", "", ""
        );
        String epc = foreignIban.toEpcQrText();
        String[] lines = epc.split("\n");
        assertEquals("002", lines[1]);
        assertEquals("", lines[4]); // empty BIC line
    }
}
