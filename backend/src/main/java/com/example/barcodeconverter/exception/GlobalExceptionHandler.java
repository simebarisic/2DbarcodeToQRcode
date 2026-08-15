package com.example.barcodeconverter.exception;

import com.example.barcodeconverter.dto.ErrorResponse;
import com.google.zxing.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Centralizes error -> HTTP response mapping so controllers stay focused
 * on the happy path, and so every failure mode returns the same
 * {"error": "..."} JSON shape the frontend already expects.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBarcodeNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("Barkod nije prepoznat na slici. Pokušaj s jasnijom, ravnijom "
                        + "i bolje osvijetljenom slikom, ili slikaj iz veće blizine. Ako je barkod "
                        + "prikazan na ekranu, napravi screenshot umjesto fotografije ekrana kamerom - "
                        + "fotografiranje monitora stvara moiré uzorak koji trajno uništava sitne "
                        + "detalje gustih 2D barkodova (npr. PDF417) i to se ne može softverski ispraviti."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("Datoteka je preveća. Maksimalna veličina je 10MB."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Neočekivana greška prilikom konverzije", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Neočekivana greška na serveru. Pokušaj ponovno."));
    }
}
