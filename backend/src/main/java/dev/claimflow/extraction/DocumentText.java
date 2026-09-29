package dev.claimflow.extraction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Turns an uploaded claim document into plain text. Only PDF and plain text are accepted. */
public final class DocumentText {

    public static final String PDF = "application/pdf";
    public static final String TEXT = "text/plain";

    private DocumentText() {
    }

    public static boolean isSupported(String contentType) {
        return PDF.equals(contentType) || TEXT.equals(contentType);
    }

    public static String extract(String contentType, byte[] content) {
        if (PDF.equals(contentType)) {
            try (PDDocument pdf = Loader.loadPDF(content)) {
                return new PDFTextStripper().getText(pdf);
            } catch (IOException e) {
                throw new IllegalArgumentException("The PDF could not be read", e);
            }
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
