package com.example.barcodeconverter.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class BarcodeService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeService.class);
    private static final int QR_SIZE = 400;
    private static final int MAX_DECODE_DIMENSION = 1800; // downscale huge photos for speed
    private static final int MAX_PDF_PAGES = 10; // cap for performance on long statements
    private static final float PDF_RENDER_DPI = 300f; // enough resolution for dense PDF417

    /**
     * Decodes an arbitrary 1D/2D barcode image (Code128, EAN, DataMatrix,
     * PDF417, Aztec, etc.). Real-world input is often a phone photo of a
     * screen or printed slip - skewed, low-contrast, moiré from screen
     * pixels, or upside down - so a single HybridBinarizer pass often
     * fails where a human eye would still read it fine. This runs a
     * small battery of preprocessing + binarizer + rotation combinations
     * and returns the first one that decodes successfully.
     */
    public DecodedResult decode(byte[] imageBytes) throws IOException, NotFoundException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (original == null) {
            throw new IllegalArgumentException("Datoteka nije prepoznata kao valjana slika.");
        }
        return decodeBufferedImage(original, null);
    }

    /**
     * Renders each page of a PDF to an image (PDFBox, 300 DPI) and runs
     * the same decode pipeline on every page in turn, returning the
     * first barcode found. Handles the common case of a HUB-3 uplatnica
     * embedded on some page of a bank statement / invoice PDF, like the
     * "NALOG ZA NACIONALNA PLAĆANJA" slip at the bottom of a PBZ Card
     * statement.
     */
    public DecodedResult decodeFromPdf(byte[] pdfBytes) throws IOException, NotFoundException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);
            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF nema stranica.");
            }

            PDFRenderer renderer = new PDFRenderer(document);
            NotFoundException lastFailure = null;

            for (int page = 0; page < pageCount; page++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(page, PDF_RENDER_DPI, ImageType.RGB);
                try {
                    DecodedResult result = decodeBufferedImage(pageImage, page + 1);
                    log.info("Barkod pronađen na stranici {}/{} PDF-a", page + 1, document.getNumberOfPages());
                    return result;
                } catch (NotFoundException e) {
                    lastFailure = e;
                }
            }

            throw lastFailure != null ? lastFailure : NotFoundException.getNotFoundInstance();
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("PDF je zaštićen lozinkom - ne mogu ga otvoriti.");
        }
    }

    /**
     * Runs the rotation + preprocessing + binarizer battery against a
     * single already-loaded image (shared by both the direct-image and
     * PDF-page-rendering entry points).
     */
    private DecodedResult decodeBufferedImage(BufferedImage original, Integer sourcePage) throws NotFoundException {
        BufferedImage normalized = downscaleIfHuge(original);

        List<Function<BufferedImage, BufferedImage>> preprocessors = List.of(
                img -> img,                       // as-is
                BarcodeService::toGrayscale,
                BarcodeService::increaseContrast,
                BarcodeService::denoiseAndThreshold
        );

        List<Integer> rotations = List.of(0, 90, 180, 270);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);

        NotFoundException lastFailure = null;

        for (int rotation : rotations) {
            BufferedImage rotated = rotation == 0 ? normalized : rotate(normalized, rotation);

            for (Function<BufferedImage, BufferedImage> preprocess : preprocessors) {
                BufferedImage candidate = preprocess.apply(rotated);
                LuminanceSource source = new BufferedImageLuminanceSource(candidate);

                for (Binarizer binarizer : List.of(new HybridBinarizer(source), new GlobalHistogramBinarizer(source))) {
                    try {
                        Result result = reader.decode(new BinaryBitmap(binarizer));
                        log.info("Barkod dekodiran (rotacija={}, format={})", rotation, result.getBarcodeFormat());
                        return new DecodedResult(result.getText(), result.getBarcodeFormat().toString(), sourcePage);
                    } catch (NotFoundException e) {
                        lastFailure = e;
                    } finally {
                        reader.reset();
                    }
                }
            }
        }

        throw lastFailure != null ? lastFailure : NotFoundException.getNotFoundInstance();
    }

    private static BufferedImage downscaleIfHuge(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int maxDim = Math.max(w, h);
        if (maxDim <= MAX_DECODE_DIMENSION) return image;

        double scale = (double) MAX_DECODE_DIMENSION / maxDim;
        int newW = (int) Math.round(w * scale);
        int newH = (int) Math.round(h * scale);

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();
        return scaled;
    }

    private static BufferedImage rotate(BufferedImage image, int degrees) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swapDims = degrees == 90 || degrees == 270;
        BufferedImage rotated = new BufferedImage(swapDims ? h : w, swapDims ? w : h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = rotated.createGraphics();
        AffineTransform transform = new AffineTransform();
        switch (degrees) {
            case 90 -> {
                transform.translate(h, 0);
                transform.rotate(Math.PI / 2);
            }
            case 180 -> {
                transform.translate(w, h);
                transform.rotate(Math.PI);
            }
            case 270 -> {
                transform.translate(0, w);
                transform.rotate(3 * Math.PI / 2);
            }
            default -> { /* 0 degrees, identity */ }
        }
        g.setTransform(transform);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rotated;
    }

    private static BufferedImage toGrayscale(BufferedImage image) {
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return gray;
    }

    /**
     * Stretches contrast on a grayscale copy - helps with the washed-out,
     * moiré-heavy look of a phone photo of a screen or a faded printout.
     */
    private static BufferedImage increaseContrast(BufferedImage image) {
        BufferedImage gray = toGrayscale(image);
        BufferedImageOp op = new RescaleOp(1.6f, -40f, null);
        BufferedImage result = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        op.filter(gray, result);
        return result;
    }

    /**
     * Mild blur (kills fine speckle/moiré-scale noise) followed by a
     * global Otsu threshold. Confirmed experimentally to help on
     * moderately noisy photos; won't recover data that's genuinely
     * destroyed (e.g. severe moiré from photographing a screen up
     * close) - there's a hard information-theoretic limit blur can't
     * cross, but it's a cheap extra net to cast before giving up.
     */
    private static BufferedImage denoiseAndThreshold(BufferedImage image) {
        BufferedImage gray = toGrayscale(image);

        float[] kernel = {
                1 / 9f, 1 / 9f, 1 / 9f,
                1 / 9f, 1 / 9f, 1 / 9f,
                1 / 9f, 1 / 9f, 1 / 9f
        };
        BufferedImageOp blurOp = new ConvolveOp(new Kernel(3, 3, kernel), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        blurOp.filter(gray, blurred);

        int threshold = otsuThreshold(blurred);

        BufferedImage result = new BufferedImage(blurred.getWidth(), blurred.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < blurred.getHeight(); y++) {
            for (int x = 0; x < blurred.getWidth(); x++) {
                int gray8 = blurred.getRaster().getSample(x, y, 0);
                result.getRaster().setSample(x, y, 0, gray8 < threshold ? 0 : 255);
            }
        }
        return result;
    }

    /** Standard Otsu's method: picks the threshold maximizing between-class variance. */
    private static int otsuThreshold(BufferedImage grayImage) {
        int[] histogram = new int[256];
        int w = grayImage.getWidth();
        int h = grayImage.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                histogram[grayImage.getRaster().getSample(x, y, 0)]++;
            }
        }

        int total = w * h;
        double sumAll = 0;
        for (int i = 0; i < 256; i++) sumAll += i * (long) histogram[i];

        double sumBackground = 0;
        int weightBackground = 0;
        double bestVariance = -1;
        int bestThreshold = 128;

        for (int t = 0; t < 256; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) continue;
            int weightForeground = total - weightBackground;
            if (weightForeground == 0) break;

            sumBackground += t * (long) histogram[t];
            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sumAll - sumBackground) / weightForeground;

            double variance = (double) weightBackground * weightForeground
                    * Math.pow(meanBackground - meanForeground, 2);

            if (variance > bestVariance) {
                bestVariance = variance;
                bestThreshold = t;
            }
        }
        return bestThreshold;
    }

    /**
     * Generates a QR code PNG (as base64 data URL) encoding the given text.
     */
    public String generateQrCodeBase64(String text) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix matrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
        return "data:image/png;base64," + base64;
    }

    public record DecodedResult(String text, String format, Integer sourcePage) {
    }
}
