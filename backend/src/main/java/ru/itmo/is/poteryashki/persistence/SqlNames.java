package ru.itmo.is.poteryashki.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SqlNames {
    private final String prefix;
    public SqlNames(@Value("${app.table-prefix:lf_}") String prefix) { validate(prefix); this.prefix = prefix; }
    public static void validate(String prefix) {
        if (!prefix.matches("lf[a-z0-9]*_")) throw new IllegalArgumentException("Invalid coursework prefix");
    }
    public String sql(String template) { return template.replace("lf_", prefix); }
}
