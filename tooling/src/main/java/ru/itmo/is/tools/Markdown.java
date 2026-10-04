package ru.itmo.is.tools;

import java.util.*;
import java.util.regex.Pattern;

final class Markdown {
    record Block(String kind, String text, int level, List<List<String>> rows) {
        Block(String kind, String text) { this(kind, text, 0, List.of()); }
    }
    static List<Block> parse(String text) {
        String[] lines = text.split("\\R"); var result = new ArrayList<Block>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip(); if (line.isEmpty() || line.startsWith("<!--")) continue;
            if (line.startsWith("```")) {
                var code = new StringBuilder();
                while (++i < lines.length && !lines[i].strip().startsWith("```")) code.append(lines[i]).append('\n');
                result.add(new Block("code", code.toString())); continue;
            }
            if (line.startsWith("|")) {
                var rows = new ArrayList<List<String>>();
                do {
                    String row = lines[i].strip(); row = row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length());
                    var cells = Arrays.stream(row.split("\\|", -1)).map(String::strip).toList();
                    if (!cells.stream().allMatch(c -> c.matches("[-: ]*"))) rows.add(cells);
                } while (++i < lines.length && lines[i].strip().startsWith("|"));
                i--; result.add(new Block("table", "", 0, rows)); continue;
            }
            if (line.startsWith("#")) {
                int level = 0; while (level < line.length() && line.charAt(level) == '#') level++;
                result.add(new Block("heading", line.substring(level).strip(), level, List.of())); continue;
            }
            if (line.startsWith("![")) { result.add(new Block("image", line)); continue; }
            if (line.matches("^Таблица \\d+ — .*")) { result.add(new Block("caption", line)); continue; }
            if (line.startsWith("- ")) { result.add(new Block("list", "• " + line.substring(2))); continue; }
            if (line.matches("^\\d+\\. .*")) { result.add(new Block("list", line)); continue; }
            var paragraph = new StringBuilder(line);
            while (i + 1 < lines.length && !lines[i + 1].isBlank() && !Pattern.compile("^(#|\\||!\\[|- |\\d+\\. |```)" ).matcher(lines[i + 1].strip()).find())
                paragraph.append(' ').append(lines[++i].strip());
            result.add(new Block("paragraph", paragraph.toString()));
        }
        return result;
    }
}
