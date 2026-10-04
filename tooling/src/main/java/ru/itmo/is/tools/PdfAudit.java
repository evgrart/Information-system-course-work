package ru.itmo.is.tools;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class PdfAudit {
    static void run(Path root) throws Exception {
        Path previews = root.resolve(".tools/previews"); Files.createDirectories(previews);
        var log = new StringBuilder();
        for (String name : List.of("part1", "part1-2", "part3", "part1-3")) {
            try (var pdf = Loader.loadPDF(root.resolve("docs/" + name + "/report.pdf").toFile())) {
                var stripper = new BoundsStripper(); var pages = new ArrayList<String>();
                for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                    stripper.setStartPage(i); stripper.setEndPage(i); String text = stripper.getText(pdf);
                    Verification.require(text.strip().length() > 5, "Empty report page: " + name + " " + i); pages.add(text);
                }
                Verification.require(pages.get(0).contains("Университет ИТМО"), "Cover text missing");
                Verification.require(pages.get(1).contains("Содержание"), "Table of contents missing");
                var outline = pdf.getDocumentCatalog().getDocumentOutline(); Verification.require(outline != null, "Bookmarks missing");
                int bookmarks = 0;
                for (var entry : outline.children()) {
                    int index = pdf.getPages().indexOf(entry.findDestinationPage(pdf));
                    Verification.require(index >= 2 && normalize(pages.get(index)).contains(normalize(entry.getTitle())), "Bookmark destination mismatch: " + entry.getTitle()); bookmarks++;
                }
                Verification.require(bookmarks >= 5, "Incomplete bookmarks");
                var renderer = new PDFRenderer(pdf);
                // Covers, table of contents and representative content pages for visual review.
                for (int index : new TreeSet<>(List.of(0, 1, Math.min(5, pdf.getNumberOfPages() - 1), pdf.getNumberOfPages() - 1)))
                    ImageIO.write(renderer.renderImageWithDPI(index, 90), "PNG", previews.resolve(name + "-" + (index + 1) + ".png").toFile());
                String line = name + ": " + pages.size() + " pages, " + bookmarks + " bookmarks, text within page bounds";
                System.out.println(line); log.append(line).append('\n');
            }
        }
        Files.writeString(root.resolve("docs/part3/validation/reports.txt"), log);
    }
    static String normalize(String text) { return text.replace("\u200b", "").replaceAll("\\s+", " ").strip(); }
    private static final class BoundsStripper extends PDFTextStripper {
        BoundsStripper() throws IOException { super(); }
        @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (var p : positions) if (!p.getUnicode().isBlank()) {
                if (p.getXDirAdj() < 15 || p.getXDirAdj() + p.getWidthDirAdj() > p.getPageWidth() - 10 || p.getYDirAdj() < 10 || p.getYDirAdj() > p.getPageHeight() - 10)
                    throw new IOException("Text outside page bounds on page " + getCurrentPageNo() + ": " + text);
            }
            super.writeString(text, positions);
        }
    }
}
