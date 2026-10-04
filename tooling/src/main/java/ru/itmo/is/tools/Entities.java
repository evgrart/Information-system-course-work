package ru.itmo.is.tools;

import java.nio.file.*;
import java.util.*;

final class Entities {
    private static final Map<String, String> NAMES = Map.of("lf_users", "User", "lf_user_roles", "UserRole",
            "lf_categories", "Category", "lf_roles", "Role", "lf_auction_permissions", "AuctionPermission", "lf_audit_entries", "AuditEntry");
    static String type(String sql) {
        return switch (sql) {
            case "bigint" -> "Long"; case "integer" -> "Integer"; case "smallint" -> "Short";
            case "boolean" -> "Boolean"; case "uuid" -> "UUID"; case "date" -> "LocalDate";
            case "timestamp with time zone" -> "OffsetDateTime";
            default -> sql.startsWith("numeric") ? "BigDecimal" : "String";
        };
    }
    static void generate(Path root) throws Exception {
        Path output = root.resolve("backend/src/main/java/ru/itmo/is/poteryashki/domain"); Files.createDirectories(output);
        var catalog = new Catalog(root);
        for (var table : catalog.tables) {
            String name = table.path("name").asText();
            String cls = NAMES.getOrDefault(name, Catalog.camel(name.substring(3, name.endsWith("s") ? name.length() - 1 : name.length())));
            var keys = Catalog.keys(table, "p");
            var source = new StringBuilder("""
                    package ru.itmo.is.poteryashki.domain;

                    import jakarta.persistence.*;
                    import lombok.*;
                    import java.math.BigDecimal;
                    import java.time.*;
                    import java.util.UUID;
                    import org.hibernate.annotations.Immutable;
                    import org.hibernate.annotations.JdbcTypeCode;
                    import org.hibernate.type.SqlTypes;

                    @Entity @Immutable @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
                    """);
            source.append("@Table(name = \"").append(name).append("\")\n");
            if (keys.size() > 1) source.append("@IdClass(").append(cls).append("Key.class)\n");
            source.append("public class ").append(cls).append(" {\n");
            var keyFields = new StringBuilder();
            for (var column : table.path("columns")) {
                String col = column.path("name").asText(), sql = column.path("type").asText(), field = Catalog.field(col);
                if (sql.equals("tsvector")) { source.append("    @Transient\n    private String ").append(field).append(";\n"); continue; }
                if (keys.contains(col)) { source.append("    @Id\n"); keyFields.append("    private ").append(type(sql)).append(' ').append(field).append(";\n"); }
                if (sql.startsWith("character(")) source.append("    @JdbcTypeCode(SqlTypes.CHAR)\n");
                if (sql.equals("jsonb")) source.append("    @JdbcTypeCode(SqlTypes.JSON)\n");
                source.append("    @Column(name = \"").append(col).append("\", columnDefinition = \"").append(sql).append("\")\n")
                        .append("    private ").append(type(sql)).append(' ').append(field).append(";\n");
            }
            Files.writeString(output.resolve(cls + ".java"), source.append("}\n"));
            if (keys.size() > 1) Files.writeString(output.resolve(cls + "Key.java"),
                    "package ru.itmo.is.poteryashki.domain;\nimport lombok.*;\nimport java.io.Serializable;\n@Data @NoArgsConstructor\npublic class " + cls + "Key implements Serializable {\n" + keyFields + "}\n");
        }
        System.out.println("Generated " + catalog.tables.size() + " immutable JPA entities");
    }
}
