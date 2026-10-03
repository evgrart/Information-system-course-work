package ru.itmo.is.poteryashki.persistence;

import java.util.Map;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.service.spi.Configurable;

/** Allows isolated integration fixtures in the same allocated PostgreSQL schema. */
public class TableNamingStrategy extends PhysicalNamingStrategyStandardImpl implements Configurable {
    private String prefix = System.getenv().getOrDefault("DB_PREFIX", "lf_");
    @Override public void configure(Map<String, Object> settings) {
        prefix = settings.getOrDefault("hibernate.lf_prefix", "lf_").toString();
        SqlNames.validate(prefix);
    }
    @Override public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment env) {
        String text = name.getText();
        return Identifier.toIdentifier(text.startsWith("lf_") ? prefix + text.substring(3) : text, name.isQuoted());
    }
}
