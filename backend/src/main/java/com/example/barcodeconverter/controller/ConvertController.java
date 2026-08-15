package com.example.barcodeconverter.controller;

import com.example.barcodeconverter.dto.ConvertResponse;
import com.example.barcodeconverter.dto.ErrorResponse;
import com.example.barcodeconverter.service.BarcodeService;
import com.example.barcodeconverter.service.Hub3Parser;
import com.google.zxing.NotFoundException;
import com.google.zxing.WriterException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ConvertController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/bmp", "image/gif",
            "application/pdf"
    );

    private final BarcodeService barcodeService;

    public ConvertController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    @PostMapping(value = "/convert", consumes = "multipart/form-data")
    public ResponseEntity<?> convert(@RequestParam("file") MultipartFile file) throws IOException, NotFoundException, WriterException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Datoteka je prazna."));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    "Nepodržan tip datoteke (" + contentType + "). Podržani formati: PNG, JPEG, WEBP, BMP, GIF, PDF."));
        }

        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType);
        BarcodeService.DecodedResult decoded = isPdf
                ? barcodeService.decodeFromPdf(file.getBytes())
                : barcodeService.decode(file.getBytes());

        ConvertResponse response = new ConvertResponse();
        response.setDecodedText(decoded.text());
        response.setSourceFormat(decoded.format());
        response.setSourcePage(decoded.sourcePage());

        String qrContent = decoded.text();
        response.setDetectedType("GENERIC");

        if (Hub3Parser.isHub3(decoded.text())) {
            Hub3Parser.Hub3Data hub3 = Hub3Parser.parse(decoded.text());
            response.setDetectedType("HUB3");
            response.setRecipientName(hub3.recipientName());
            response.setIban(hub3.iban());
            response.setBic(hub3.bic());
            response.setAmount(hub3.amount());
            response.setCurrency(hub3.currency());
            response.setReference(hub3.reference());
            response.setDescription(hub3.description());

            if (!hub3.hasIban()) {
                response.setWarning("Nedostaje IBAN primatelja u barkodu - ne mogu generirati "
                        + "EPC QR kod. Generiran je QR sa sirovim HUB-3 sadržajem, koji vjerojatno "
                        + "neće prepoznati bankovne aplikacije.");
            } else if (!hub3.isEur()) {
                response.setWarning("EPC QR kod (kojeg prepoznaju Revolut, N26 i slične app-ove) "
                        + "podržava samo EUR, a ovaj barkod je u valuti " + hub3.currency()
                        + ". Generiran je QR sa sirovim HUB-3 sadržajem umjesto EPC formata.");
            } else {
                qrContent = hub3.toEpcQrText();
                if (!hub3.hasBic()) {
                    response.setWarning("Banka primatelja nije prepoznata iz IBAN-a, pa je EPC "
                            + "QR generiran bez BIC-a (verzija 002). Neke bankovne app-ove to može "
                            + "spriječiti da učitaju kod - provjeri je li IBAN ispravan.");
                }
            }
        }

        response.setQrContent(qrContent);
        response.setQrCodeBase64(barcodeService.generateQrCodeBase64(qrContent));

        return ResponseEntity.ok(response);
    }
}
