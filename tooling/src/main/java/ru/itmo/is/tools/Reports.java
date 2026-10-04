package ru.itmo.is.tools;

import org.apache.fop.apps.*;
import org.apache.fop.events.model.EventSeverity;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Markdown to XSL-FO, with embedded fonts, repeated headers and live page citations. */
final class Reports {
    private static final String SECOND_TASK = """
            Второй этап курсовой работы включает:

            1. ER-модель не менее чем с 10 сущностями и отношением «многие ко многим».
            2. Построение даталогической модели на основе ER-модели.
            3. Реализацию модели в PostgreSQL.
            4. Обеспечение целостности средствами DDL и триггеров.
            5. Скрипты создания, удаления и заполнения тестовыми данными.
            6. Функции и процедуры PL/pgSQL для критически важных запросов.
            7. Индексы по сценариям первого этапа и обоснование их полезности.
            8. Подготовку отчёта.

            """;
    static void build(Path root) throws Exception {
        String first = Files.readString(root.resolve("docs/part1/report.md"));
        String second = Files.readString(root.resolve("docs/part2/report.md"))
                .replace("<!-- DICTIONARY -->", Files.readString(root.resolve("docs/part2/model/dictionary.md")));
        String combined = first.replace("Отчёт по первому этапу курсовой работы: анализ предметной области и проектирование системы.",
                "Отчёт по этапам 1–2 курсовой работы: анализ предметной области, проектирование системы и реализация модели данных.");
        combined = combined.replaceFirst(Pattern.quote("## 1. Предметная область"), Matcher.quoteReplacement(SECOND_TASK + "## 1. Предметная область"));
        combined += "\n" + skipTitle(second) + "\n";
        combined = combined.replace("(uml/", "(../part1/uml/").replace("(model/", "(../part2/model/");
        Files.writeString(root.resolve("docs/part1-2/report.md"), combined);
        String third = Files.readString(root.resolve("docs/part3/report.md"));
        Files.createDirectories(root.resolve("docs/part1-3"));
        Files.writeString(root.resolve("docs/part1-3/report.md"),
                combined.replaceFirst("Отчёт по этапам 1–2 курсовой работы:", "Отчёт по этапам 1–3 курсовой работы:")
                        + "\n" + skipTitle(third).replace("(uml/", "(../part3/uml/") + "\n");
        String fourth=Files.readString(root.resolve("docs/part4/report.md"));
        Files.createDirectories(root.resolve("docs/part1-4"));
        Files.writeString(root.resolve("docs/part1-4/report.md"),Files.readString(root.resolve("docs/part1-3/report.md"))
                .replaceFirst("Отчёт по этапам 1–3 курсовой работы:[^\\n]+","Итоговый отчёт по этапам 1–4 курсовой работы: анализ предметной области, реализация базы данных, уровней хранения, бизнес-логики и представления.")
                + "\n" + skipTitle(fourth).replace("(uml/","(../part4/uml/").replace("(screenshots/","(../part4/screenshots/")+"\n");
        var data = Catalog.JSON.readTree(root.resolve("docs/report_data.json").toFile());
        var factory = factory(root);
        for (var entry : Map.of("part1", "Этап 1", "part1-2", "Этапы 1–2", "part3", "Этап 3", "part1-3", "Этапы 1–3","part4","Этап 4","part1-4","Этапы 1–4").entrySet()) {
            Path folder = root.resolve("docs/" + entry.getKey());
            String fo = document(folder, entry.getValue(), data.path("student").asText(), data.path("group").asText(), data.path("teacher").asText());
            try (var out = Files.newOutputStream(folder.resolve("report.pdf"))) {
                var agent = factory.newFOUserAgent(); agent.setTitle("ИС «Потеряшки». " + entry.getValue());
                agent.setAuthor(data.path("student").asText()); agent.setCreator(""); agent.setProducer("");
                agent.getEventBroadcaster().addEventListener(event -> {
                    if (event.getSeverity() == EventSeverity.ERROR || event.getSeverity() == EventSeverity.FATAL)
                        throw new IllegalStateException("PDF layout: " + event.getEventID() + " " + event.getParams());
                    if (event.getEventID().contains("overflow") || event.getEventID().contains("glyph"))
                        System.err.println("PDF layout: " + event.getEventID() + " " + event.getParams());
                });
                var fop = factory.newFop(MimeConstants.MIME_PDF, agent, out);
                var transformer = TransformerFactory.newInstance(); transformer.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                transformer.newTransformer().transform(new StreamSource(new StringReader(fo)), new SAXResult(fop.getDefaultHandler()));
                System.out.println(entry.getKey() + ": PDF, " + fop.getResults().getPageCount() + " pages");
            }
        }
    }
    static String skipTitle(String text) { return String.join("\n", text.lines().skip(2).toList()); }
    static FopFactory factory(Path root) throws Exception {
        Path fonts = Path.of(System.getenv().getOrDefault("COURSE_FONTS", "C:/Windows/Fonts"));
        var config = new StringBuilder("<fop version=\"1.0\"><strict-validation>true</strict-validation><renderers><renderer mime=\"application/pdf\"><fonts>");
        String[][] specs = {{"times.ttf", "Times New Roman", "normal", "normal"}, {"timesbd.ttf", "Times New Roman", "normal", "bold"}, {"timesi.ttf", "Times New Roman", "italic", "normal"}, {"timesbi.ttf", "Times New Roman", "italic", "bold"}, {"consola.ttf", "Consolas", "normal", "normal"}};
        for (var spec : specs) {
            Path file = fonts.resolve(spec[0]); if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing font " + file + "; set COURSE_FONTS");
            config.append("<font embed-url=\"").append(file.toUri()).append("\"><font-triplet name=\"").append(spec[1]).append("\" style=\"").append(spec[2]).append("\" weight=\"").append(spec[3]).append("\"/></font>");
        }
        config.append("</fonts></renderer></renderers></fop>");
        return new FopConfParser(new ByteArrayInputStream(config.toString().getBytes(StandardCharsets.UTF_8)), root.toUri()).getFopFactoryBuilder().build();
    }
    static String document(Path folder, String label, String student, String group, String teacher) throws Exception {
        var blocks = Markdown.parse(Files.readString(folder.resolve("report.md")));
        int start = 0; while (start < blocks.size() && !(blocks.get(start).kind().equals("heading") && blocks.get(start).level() == 2)) start++;
        blocks = blocks.subList(start, blocks.size());
        var fo = new StringBuilder("""
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format" font-family="Times New Roman" font-size="14pt" line-height="21pt">
                <fo:layout-master-set>
                <fo:simple-page-master master-name="cover" page-width="210mm" page-height="297mm" margin-left="30mm" margin-right="15mm" margin-top="20mm" margin-bottom="20mm"><fo:region-body/></fo:simple-page-master>
                <fo:simple-page-master master-name="body" page-width="210mm" page-height="297mm" margin-left="30mm" margin-right="15mm" margin-top="20mm" margin-bottom="20mm"><fo:region-body/><fo:region-after extent="10mm"/></fo:simple-page-master>
                </fo:layout-master-set>
                """);
        fo.append("<fo:bookmark-tree>"); int heading = 0;
        for (var b : blocks) if (b.kind().equals("heading") && b.level() == 2)
            fo.append("<fo:bookmark internal-destination=\"h").append(++heading).append("\"><fo:bookmark-title>").append(Models.xml(b.text())).append("</fo:bookmark-title></fo:bookmark>");
        fo.append("</fo:bookmark-tree><fo:page-sequence master-reference=\"cover\" force-page-count=\"no-force\"><fo:flow flow-name=\"xsl-region-body\">");
        String[] cover = {"Университет ИТМО", "Факультет программной инженерии и компьютерной техники\nОбразовательная программа\n«Системное и прикладное программное обеспечение»", "Курсовая работа\nПо дисциплине «Информационные системы»", "Информационная система для поиска\nи возврата потерянных вещей «Потеряшки»", label, "Выполнил студент группы " + group + "\n" + student, "Проверил:\n" + teacher, "Санкт-Петербург 2026"};
        for (int i = 0; i < cover.length; i++) {
            String space = i == 2 ? "40mm" : i == 5 ? "20mm" : i == 7 ? "34mm" : "0mm";
            fo.append("<fo:block font-size=\"").append(i == 0 ? "18pt" : "16pt").append("\" line-height=\"22pt\" text-align=\"").append(i == 5 || i == 6 ? "right" : "center").append("\" space-before=\"").append(space).append("\" space-after=\"16pt\"");
            if (i == 2 || i == 3) fo.append(" font-weight=\"bold\"");
            fo.append('>'); for (String line : cover[i].split("\n")) fo.append("<fo:block>").append(Models.xml(line)).append("</fo:block>"); fo.append("</fo:block>");
        }
        fo.append("</fo:flow></fo:page-sequence><fo:page-sequence master-reference=\"body\" initial-page-number=\"1\" force-page-count=\"no-force\"><fo:static-content flow-name=\"xsl-region-after\"><fo:block text-align=\"center\" font-size=\"12pt\" padding-top=\"4mm\"><fo:page-number/></fo:block></fo:static-content><fo:flow flow-name=\"xsl-region-body\"><fo:block font-weight=\"bold\" space-after=\"12pt\">Содержание</fo:block>");
        heading = 0;
        for (var b : blocks) if (b.kind().equals("heading") && b.level() == 2) {
            fo.append("<fo:block text-align-last=\"justify\" line-height=\"18pt\" space-after=\"5pt\"><fo:basic-link internal-destination=\"h").append(++heading).append("\">").append(inline(b.text(), false)).append("<fo:leader leader-pattern=\"dots\"/><fo:page-number-citation ref-id=\"h").append(heading).append("\"/></fo:basic-link></fo:block>");
        }
        heading = 0;
        for (var b : blocks) switch (b.kind()) {
            case "heading" -> {
                if (b.level() == 1) break;
                fo.append("<fo:block font-weight=\"bold\" keep-with-next.within-page=\"always\" space-before=\"12pt\" space-after=\"8pt\"");
                if (b.level() == 2) fo.append(" id=\"h").append(++heading).append("\"");
                if (b.text().startsWith("Задание")) fo.append(" break-before=\"page\"");
                fo.append('>').append(inline(b.text(), false)).append("</fo:block>");
            }
            case "paragraph", "list", "caption" -> {
                fo.append("<fo:block space-after=\"6pt\" text-align=\"").append(b.kind().equals("paragraph") ? "justify" : "left").append("\" text-indent=\"").append(b.kind().equals("paragraph") ? "12.5mm" : "0mm").append("\"");
                if (b.kind().equals("caption")) fo.append(" font-size=\"12pt\" line-height=\"16pt\" keep-with-next.within-page=\"always\"");
                fo.append('>').append(inline(b.text(), false)).append("</fo:block>");
            }
            case "code" -> fo.append("<fo:block font-family=\"Consolas\" font-size=\"9pt\" line-height=\"11pt\" white-space-collapse=\"false\" linefeed-treatment=\"preserve\" wrap-option=\"wrap\" space-after=\"8pt\">").append(Models.xml(wrapCode(b.text()))).append("</fo:block>");
            case "table" -> table(fo, b.rows());
            case "image" -> {
                var m = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)").matcher(b.text()); if (!m.find()) throw new IllegalArgumentException("Invalid image");
                Path image = folder.resolve(m.group(2)).normalize(); var dimensions = ImageIO.read(image.toFile());
                double width = 164, height = width * dimensions.getHeight() / dimensions.getWidth();
                if (height > 218) { width *= 218 / height; height = 218; }
                fo.append("<fo:block keep-together.within-page=\"always\" space-before=\"6pt\" space-after=\"10pt\"><fo:block text-align=\"center\"><fo:external-graphic src=\"url('").append(image.toUri()).append("')\" content-width=\"").append(width).append("mm\" content-height=\"").append(height).append("mm\"/></fo:block><fo:block font-size=\"12pt\" line-height=\"16pt\">").append(inline(m.group(1), false)).append("</fo:block></fo:block>");
            }
            default -> throw new IllegalArgumentException("Unknown Markdown block");
        }
        return fo.append("</fo:flow></fo:page-sequence></fo:root>").toString();
    }
    private static void table(StringBuilder fo, List<List<String>> rows) {
        int count = rows.get(0).size(); boolean id = rows.get(0).get(0).equals("ID");
        double[] weights = count == 4 ? (id ? new double[]{.10,.31,.43,.16} : new double[]{.25,.25,.25,.25}) : count == 3 ? (id ? new double[]{.12,.36,.52} : new double[]{.28,.32,.40}) : new double[]{.30,.70};
        fo.append("<fo:table table-layout=\"fixed\" width=\"165mm\" border-collapse=\"collapse\" space-after=\"5mm\" font-size=\"11pt\" line-height=\"14pt\">");
        for (int i = 0; i < count; i++) fo.append("<fo:table-column column-width=\"").append((count <= 4 ? weights[i] : 1.0 / count) * 165).append("mm\"/>");
        for (int r = 0; r < rows.size(); r++) {
            if (r == 0) fo.append("<fo:table-header>"); else if (r == 1) fo.append("<fo:table-body>");
            fo.append("<fo:table-row>");
            for (String cell : rows.get(r)) { fo.append("<fo:table-cell border=\"0.5pt solid #6c7a85\" padding=\"5pt\""); if (r == 0) fo.append(" background-color=\"#edf3f8\" font-weight=\"bold\""); fo.append("><fo:block>").append(inline(cell, true)).append("</fo:block></fo:table-cell>"); }
            fo.append("</fo:table-row>"); if (r == 0) fo.append("</fo:table-header>");
        }
        if (rows.size() == 1) fo.append("<fo:table-body><fo:table-row><fo:table-cell number-columns-spanned=\"").append(count).append("\"><fo:block/></fo:table-cell></fo:table-row></fo:table-body>"); else fo.append("</fo:table-body>");
        fo.append("</fo:table>");
    }
    static String inline(String text, boolean breaks) {
        var pattern = Pattern.compile("\\*\\*(.*?)\\*\\*|\\[([^]]+)\\]\\((https?://[^)]+)\\)|`([^`]+)`|<br\\s*/?>");
        var match = pattern.matcher(text); var result = new StringBuilder(); int end = 0;
        while (match.find()) {
            result.append(escaped(text.substring(end, match.start()), breaks));
            if (match.group(1) != null) result.append("<fo:inline font-weight=\"bold\">").append(escaped(match.group(1), breaks)).append("</fo:inline>");
            else if (match.group(2) != null) result.append("<fo:basic-link external-destination=\"url('").append(Models.xml(match.group(3))).append("')\">").append(escaped(match.group(2), breaks)).append("</fo:basic-link>");
            else if (match.group(4) != null) result.append(escaped(match.group(4), breaks));
            else result.append("<fo:inline linefeed-treatment=\"preserve\">&#10;</fo:inline>");
            end = match.end();
        }
        return result.append(escaped(text.substring(end), breaks)).toString();
    }
    private static String escaped(String text, boolean breaks) { return Models.xml(breaks ? text.replace("_", "_\u200b").replace("/", "/\u200b") : text); }
    private static String wrapCode(String code) { var result = new StringBuilder(); for (String line : code.split("\n", -1)) { while (line.length() > 88) { result.append(line, 0, 88).append('\n'); line = line.substring(88); } result.append(line).append('\n'); } return result.toString(); }
}
