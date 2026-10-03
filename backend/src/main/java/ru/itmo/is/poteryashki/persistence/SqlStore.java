package ru.itmo.is.poteryashki.persistence;

import java.util.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized ordinary CRUD. Critical transitions live in DatabaseFunctions. */
@Repository
@RequiredArgsConstructor
public class SqlStore {
    private final JdbcTemplate jdbc;
    private final SqlNames names;
    private final EntityManager entities;
    public int update(String sql, Object... args) { int count=jdbc.update(names.sql(sql), args);entities.clear();return count; }
    public Long id(String sql, Object... args) { Long id=jdbc.queryForObject(names.sql(sql), Long.class, args);entities.clear();return id; }
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(names.sql(sql), args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = rows(sql, args);
        if (rows.isEmpty()) throw new NoSuchElementException("Object not found");
        if (rows.size() != 1) throw new IllegalStateException("Expected one object");
        return rows.get(0);
    }
    public boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(names.sql(sql), Boolean.class,args)); }
}
