package ru.itmo.is.tools;

import java.util.*;

/** PostgreSQL pgpass format: ordered matches, wildcards and escaped separators. */
public final class PgPass {
    private PgPass() {}
    public static List<String> fields(String line) {
        var result = new ArrayList<String>(); var part = new StringBuilder(); boolean escaped = false;
        for (char c : line.toCharArray()) {
            if (escaped) { part.append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == ':') { result.add(part.toString()); part.setLength(0); }
            else part.append(c);
        }
        if (escaped) part.append('\\');
        result.add(part.toString()); return result;
    }
    public static String find(String contents, String host, int port, String database, String user) {
        List<String> values = List.of(host, Integer.toString(port), database, user);
        for (String line : contents.lines().toList()) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var fields = fields(line);
            if (fields.size() != 5) continue;
            boolean matches = true;
            for (int i = 0; i < 4; i++)
                matches &= fields.get(i).equals("*") || fields.get(i).equals(values.get(i));
            if (matches) return fields.get(4);
        }
        throw new IllegalStateException("No matching pgpass entry; set DB_PASSWORD");
    }
}
