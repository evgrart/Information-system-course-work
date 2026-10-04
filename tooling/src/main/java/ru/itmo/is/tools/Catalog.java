package ru.itmo.is.tools;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

final class Catalog {
    static final ObjectMapper JSON = new ObjectMapper();
    final List<JsonNode> tables = new ArrayList<>();
    Catalog(Path root) throws Exception {
        JSON.readTree(root.resolve("docs/part2/model/catalog.json").toFile()).forEach(tables::add);
        Verification.require(tables.size() >= 10, "Catalog must contain at least ten entities");
    }
    static Set<String> keys(JsonNode table, String kind) {
        var keys = new LinkedHashSet<String>();
        for (JsonNode c : table.path("constraints")) if (c.path("type").asText().equals(kind)) {
            var match = Pattern.compile("\\(([^)]+)\\)").matcher(c.path("definition").asText());
            if (match.find()) for (String value : match.group(1).split(",")) keys.add(value.strip());
        }
        return keys;
    }
    static String shortType(String type) {
        return type.replace("character varying", "varchar").replace("timestamp with time zone", "timestamptz").replace("character(", "char(");
    }
    static String camel(String name) {
        var result = new StringBuilder();
        for (String word : name.split("_")) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return result.toString();
    }
    static String field(String name) { String c = camel(name); return Character.toLowerCase(c.charAt(0)) + c.substring(1); }
}
