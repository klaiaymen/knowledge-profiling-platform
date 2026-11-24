package dev.knowledgeprofiling.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(-1)
public class PdfDocumentSource implements DocumentSource {
    @Override public boolean supports(String filename, String contentType) {
        return filename.toLowerCase().endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType);
    }
    @Override public String sourceType() { return "pdf"; }
    @Override public List<ExtractedPage> extract(byte[] content) throws IOException {
        try (var pdf = Loader.loadPDF(content)) {
            List<ExtractedPage> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedPage(page, stripper.getText(pdf)));
            }
            return List.copyOf(pages);
        }
    }
}
