package ru.itmo.is.poteryashki.web;

import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component("ui")
public class UiText {
    private static final ZoneId ZONE=ZoneId.of("Europe/Moscow");
    private static final Map<String,String> STATES=Map.ofEntries(
            Map.entry("draft","Черновик"),Map.entry("pending","На проверке"),Map.entry("published","Опубликовано"),
            Map.entry("rejected","Отклонено"),Map.entry("archived","В архиве"),Map.entry("reserved","Зарезервировано"),
            Map.entry("returned","Возвращено"),Map.entry("auctioned","Продано на аукционе"),Map.entry("approved","Одобрено"),
            Map.entry("verified","Профиль проверен"),Map.entry("blocked","Заблокирован"),Map.entry("accepted","Передача согласована"),
            Map.entry("cancelled","Отменено"),Map.entry("fulfilled","Вещь возвращена"),Map.entry("scheduled","Ожидает начала"),
            Map.entry("active","Идёт аукцион"),Map.entry("suspended","Приостановлено"),Map.entry("finished","Завершено"),
            Map.entry("paid","Оплачено"),Map.entry("created","Ожидает оплаты"),Map.entry("resolved","Рассмотрено"));
    public String state(Object value) { return value==null ? "—" : STATES.getOrDefault(value.toString(),value.toString()); }
    public String kind(Object value) { return "found".equals(value) ? "Найдено" : "Потеряно"; }
    private Instant instant(Object value) {
        if(value instanceof java.sql.Timestamp t) return t.toInstant();
        if(value instanceof OffsetDateTime t) return t.toInstant();
        if(value instanceof Instant t) return t;
        return LocalDateTime.parse(value.toString()).atZone(ZONE).toInstant();
    }
    public String date(Object value) { return value==null ? "—" : DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").format(instant(value).atZone(ZONE)); }
    public String inputDate(Object value) { return value==null ? "" : DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").format(instant(value).atZone(ZONE)); }
    public String reason(Object value) { return value==null ? "" : value.toString(); }
    public static OffsetDateTime time(String value) { return value==null || value.isBlank() ? null : LocalDateTime.parse(value).atZone(ZONE).toOffsetDateTime(); }
}
