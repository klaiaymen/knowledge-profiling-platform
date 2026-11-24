package dev.knowledgeprofiling.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSourceTest {
    @Test void extractsPdfPageAndText() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Synthetic policy on risk horizon");
                stream.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        List<ExtractedPage> pages = new PdfDocumentSource().extract(pdf);
        assertEquals(1, pages.size());
        assertEquals(1, pages.getFirst().page());
        assertTrue(pages.getFirst().text().contains("Synthetic policy on risk horizon"));
    }

    @Test void selectsMarkdownTextAndCodeSourcesByFormat() {
        assertTrue(new MarkdownDocumentSource().supports("guide.md", "text/plain"));
        assertTrue(new TextDocumentSource().supports("notes.txt", "application/octet-stream"));
        assertTrue(new CodeDocumentSource().supports("Rule.java", "text/plain"));
    }
}
