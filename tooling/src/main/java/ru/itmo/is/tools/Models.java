package ru.itmo.is.tools;

import com.fasterxml.jackson.databind.JsonNode;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

final class Models {
    private static final String[] NAMES = {"users", "roles", "user_roles", "refresh_tokens", "verifications", "verification_tokens", "tariffs", "payment_orders", "payment_events", "subscriptions", "organizations", "locations", "categories", "listings", "listing_images", "private_attributes", "claims", "conversations", "messages", "transfers", "complaints", "auction_permissions", "auctions", "bids", "notifications", "outbox_events", "audit_entries"};
    private static final String[] LABELS = {"Пользователь", "Роль", "Назначение роли", "Токен обновления", "Проверка профиля", "Одноразовый токен", "Тариф", "Заказ оплаты", "Событие оплаты", "Период подписки", "Организация", "Место", "Категория", "Объявление", "Изображение", "Контрольный признак", "Заявка владельца", "Диалог", "Сообщение", "Передача", "Жалоба", "Допуск к продаже", "Аукцион", "Ставка", "Уведомление", "Событие доставки", "Запись аудита"};
    static String label(String name) { return LABELS[Arrays.asList(NAMES).indexOf(name.replaceFirst("^lf_", ""))]; }
    static void generate(Path root) throws Exception {
        var catalog = new Catalog(root); Path output = root.resolve("docs/part2/model");
        var logical = new StringBuilder("erDiagram\n"); var conceptual = new StringBuilder("erDiagram\n");
        var dictionary = new StringBuilder("### 8.2. Словарь отношений\n"); int number = 0, relations = 0;
        var byName = new HashMap<String, JsonNode>();
        for (var table : catalog.tables) {
            String name = table.path("name").asText(); byName.put(name, table);
            var pk = Catalog.keys(table, "p"); var fk = Catalog.keys(table, "f");
            logical.append("    ").append(name).append(" {\n");
            dictionary.append("\n#### ").append(++number).append(". ").append(label(name)).append(": ").append(name)
                    .append("\n\n| Поле | Тип PostgreSQL | NULL | Ключ |\n| --- | --- | --- | --- |\n");
            for (var column : table.path("columns")) {
                String col = column.path("name").asText(), type = Catalog.shortType(column.path("type").asText());
                String keys = String.join(", ", List.of(pk.contains(col) ? "PK" : "", fk.contains(col) ? "FK" : "").stream().filter(s -> !s.isEmpty()).toList());
                logical.append(("        " + type.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("^_+|_+$", "") + " " + col + " " + keys).stripTrailing()).append('\n');
                dictionary.append("| ").append(col).append(" | ").append(type).append(" | ").append(column.path("nullable").asBoolean() ? "Да" : "Нет").append(" | ").append(keys.isEmpty() ? "—" : keys).append(" |\n");
            }
            logical.append("    }\n");
            for (var constraint : table.path("constraints")) {
                String kind = constraint.path("type").asText(), definition = constraint.path("definition").asText();
                if (Set.of("p", "f", "u").contains(kind)) dictionary.append('\n').append(definition).append(".\n");
                if (!kind.equals("f")) continue;
                var match = Pattern.compile("FOREIGN KEY \\(([^)]+)\\) REFERENCES ([^(]+)\\(").matcher(definition);
                if (!match.find()) throw new IllegalStateException("Unrecognized FK: " + definition);
                var fields = Set.of(match.group(1).split(", "));
                String target = match.group(2).strip().replaceFirst("^[^.]+\\.", "");
                boolean optional = false, unique = false;
                for (var col : table.path("columns")) if (fields.contains(col.path("name").asText()) && col.path("nullable").asBoolean()) optional = true;
                for (var con : table.path("constraints")) if (Set.of("p", "u").contains(con.path("type").asText())) {
                    var m = Pattern.compile("\\(([^)]+)\\)").matcher(con.path("definition").asText());
                    if (m.find() && fields.containsAll(Arrays.asList(m.group(1).split(", ")))) unique = true;
                }
                String middle = " " + (optional ? "|o" : "||") + "--" + (unique ? "o|" : "o{") + " ";
                String suffix = " : \"" + match.group(1).replace(", ", ",") + "\"\n";
                logical.append("    ").append(target).append(middle).append(name).append(suffix);
                conceptual.append("    ").append(target.substring(3)).append(middle).append(name.substring(3)).append(suffix); relations++;
            }
        }
        Files.writeString(output.resolve("logical.mmd"), logical); Files.writeString(output.resolve("er.mmd"), conceptual);
        Files.writeString(output.resolve("dictionary.md"), dictionary);
        String[][] groups = {
            {"01_accounts", "Аккаунты и подписка", "roles,user_roles,users,verifications,refresh_tokens,subscriptions,tariffs,payment_orders,payment_events", "roles:user_roles:1:0..N,users:user_roles:1:0..N,users:refresh_tokens:1:0..N,users:subscriptions:1:0..N,users:verifications:1:0..N,tariffs:payment_orders:1:0..N,payment_orders:payment_events:1:0..N,payment_orders:subscriptions:1:0..1"},
            {"02_listings", "Объявления и справочники", "organizations,locations,categories,users,listings,listing_images,,private_attributes,", "organizations:locations:0..1:0..N,locations:listings:1:0..N,categories:listings:1:0..N,users:listings:1:0..N,listings:listing_images:1:0..5,listings:private_attributes:1:0..N"},
            {"03_returns", "Возврат и обращения", "users,listings,complaints,messages,claims,transfers,,conversations,", "listings:claims:1:0..N,users:claims:1:0..N,claims:transfers:1:0..1,claims:conversations:1:0..1,conversations:messages:1:0..N,listings:complaints:0..1:0..N"},
            {"04_auctions", "Допуск к продаже и аукцион", "categories,listings,users,,auction_permissions,,bids,auctions,", "categories:listings:1:0..N,users:auction_permissions:1:0..N,listings:auction_permissions:1:0..N,auction_permissions:auctions:1:0..N,auctions:bids:1:0..N,users:bids:1:0..N"},
            {"05_events", "Уведомления, доставка и аудит", ",users,,notifications,audit_entries,outbox_events", "users:notifications:1:0..N,users:audit_entries:0..1:0..N"},
            {"06_tokens", "Верификация и восстановление доступа", ",users,,verifications,verification_tokens,refresh_tokens", "users:verifications:1:0..N,users:verification_tokens:1:0..N,users:refresh_tokens:1:0..N"}};
        for (String[] group : groups) draw(output, group, byName);
        System.out.println(number + " tables, " + relations + " foreign keys, six ER panels");
    }
    private static void draw(Path output, String[] group, Map<String, JsonNode> byName) throws Exception {
        var canvas = new Canvas(); canvas.text(630, 35, group[1], 29, true);
        var coords = new HashMap<String, Point>(); String[] names = group[2].split(",", -1);
        for (int i = 0; i < names.length; i++) if (!names[i].isEmpty()) coords.put(names[i], new Point(220 + i % 3 * 410, 200 + i / 3 * 270));
        for (String edge : group[3].split(",")) {
            String[] e = edge.split(":"); Point a = coords.get(e[0]), b = coords.get(e[1]);
            int sx, sy, ex, ey;
            if (a.y == b.y) {
                int sign = Integer.signum(b.x - a.x); sx = a.x + sign * 175; sy = a.y; ex = b.x - sign * 175; ey = b.y;
                canvas.line(sx, sy, ex, ey); canvas.text(sx + sign * 20, sy - 23, e[2], 20, false); canvas.text(ex - sign * 20, ey + 23, e[3], 20, false);
            } else {
                int sign = Integer.signum(b.y - a.y); sx = a.x; sy = a.y + sign * 78; ex = b.x; ey = b.y - sign * 78;
                int middle = (a.y + b.y) / 2; canvas.line(sx, sy, sx, middle, ex, middle, ex, ey);
                canvas.text(sx + 28, sy + sign * 20, e[2], 20, false); canvas.text(ex + 28, ey - sign * 20, e[3], 20, false);
            }
        }
        for (var entry : coords.entrySet()) {
            String name = entry.getKey(); Point p = entry.getValue();
            canvas.box(p.x - 175, p.y - 78, 350, 156);
            canvas.text(p.x, p.y - 37, label(name), 26, true); canvas.text(p.x, p.y, "lf_" + name, 23, false);
            var keys = new TreeSet<>(Catalog.keys(byName.get("lf_" + name), "p")); String text = "PK: " + String.join(", ", keys);
            if (text.length() > 27) text = text.replace(", ", ",\n"); canvas.text(p.x, p.y + 40, text, 20, false);
        }
        canvas.text(630, 900, "1 — один; 0..1 — необязательный; 0..N — множество", 25, false); canvas.save(output.resolve(group[0]));
    }
    private static final class Canvas {
        final BufferedImage image = new BufferedImage(1260, 940, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        final StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1260\" height=\"940\" viewBox=\"0 0 1260 940\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        Canvas() { g.setColor(Color.WHITE); g.fillRect(0, 0, 1260, 940); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setStroke(new BasicStroke(3)); }
        void line(int... coords) {
            g.setColor(Color.decode("#263746")); var path = new Path2D.Double(); path.moveTo(coords[0], coords[1]); svg.append("<polyline points=\"");
            for (int i = 0; i < coords.length; i += 2) { if (i > 0) path.lineTo(coords[i], coords[i + 1]); svg.append(coords[i]).append(',').append(coords[i + 1]).append(' '); }
            g.draw(path); svg.append("\" fill=\"none\" stroke=\"#263746\" stroke-width=\"3\"/>\n");
        }
        void box(int x, int y, int w, int h) { g.setColor(Color.decode("#f4f6f8")); g.fillRect(x, y, w, h); g.setColor(Color.decode("#263746")); g.drawRect(x, y, w, h); svg.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"").append(w).append("\" height=\"").append(h).append("\" fill=\"#f4f6f8\" stroke=\"#263746\" stroke-width=\"3\"/>\n"); }
        void text(int x, int y, String text, int size, boolean bold) {
            g.setFont(new Font("Arial", bold ? Font.BOLD : Font.PLAIN, size)); g.setColor(Color.decode("#263746")); String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) { float yy = y + (i - (lines.length - 1) / 2f) * (size + 7); var fm = g.getFontMetrics(); g.drawString(lines[i], x - fm.stringWidth(lines[i]) / 2f, yy + (fm.getAscent() - fm.getDescent()) / 2f); svg.append("<text x=\"").append(x).append("\" y=\"").append(yy).append("\" font-family=\"Arial, sans-serif\" font-size=\"").append(size).append("\" font-weight=\"").append(bold ? "bold" : "normal").append("\" text-anchor=\"middle\" dominant-baseline=\"central\" fill=\"#263746\">").append(xml(lines[i])).append("</text>\n"); }
        }
        void save(Path stem) throws Exception { g.dispose(); ImageIO.write(image, "PNG", Path.of(stem + ".png").toFile()); Files.writeString(Path.of(stem + ".svg"), svg.append("</svg>\n")); }
    }
    static String xml(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
