package com.example.barcodeconverter.dto;

public class ConvertResponse {

    private String decodedText;
    private String sourceFormat;
    private String qrCodeBase64; // "data:image/png;base64,...."
    private Integer sourcePage; // set when the barcode was found on a page of an uploaded PDF

    private String detectedType;   // "HUB3" or "GENERIC"
    private String qrContent;      // the exact text that was encoded into the QR
    private String warning;        // non-null if something couldn't be mapped cleanly

    // Populated only when detectedType == "HUB3"
    private String recipientName;
    private String iban;
    private String bic;
    private Double amount;
    private String currency;
    private String reference;
    private String description;

    public ConvertResponse() {
    }

    public ConvertResponse(String decodedText, String sourceFormat, String qrCodeBase64) {
        this.decodedText = decodedText;
        this.sourceFormat = sourceFormat;
        this.qrCodeBase64 = qrCodeBase64;
    }

    public String getDecodedText() {
        return decodedText;
    }

    public void setDecodedText(String decodedText) {
        this.decodedText = decodedText;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public String getQrCodeBase64() {
        return qrCodeBase64;
    }

    public void setQrCodeBase64(String qrCodeBase64) {
        this.qrCodeBase64 = qrCodeBase64;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public String getDetectedType() {
        return detectedType;
    }

    public void setDetectedType(String detectedType) {
        this.detectedType = detectedType;
    }

    public String getQrContent() {
        return qrContent;
    }

    public void setQrContent(String qrContent) {
        this.qrContent = qrContent;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
