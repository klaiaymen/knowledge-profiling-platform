package dev.knowledgeprofiling.profiling;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProfilePdfExporter {
    public byte[] export(ProfilingSessionService.ProfileReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> lines = new ArrayList<>(List.of(
                    "ConviFin - profil pedagogique (donnees synthetiques)",
                    "Reponses: " + report.answers(),
                    "Score: " + report.score() + "/100    Categorie: " + report.category(),
                    "Justification: " + report.justification(),
                    "Limites: " + report.limitations(),
                    "Avertissement: " + report.warning(),
                    "Sources: " + report.citations().stream().map( c -> c.authority() + " " + c.url()).distinct().reduce((a,b) -> a + " | " + b).orElse("")));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 9);
                content.newLineAtOffset(48, 750);
                for (String line : lines) {
                    for (String wrapped : wrap(ascii(line), 90)) {
                        content.showText(wrapped);
                        content.newLineAtOffset(0, -14);
                    }
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Could not create profile PDF", e); }
    }
    private List<String> wrap(String text, int width) {
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length(); start += width) result.add(text.substring(start, Math.min(text.length(), start + width)));
        if (result.isEmpty()) result.add("");
        return result;
    }
    private String ascii(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
