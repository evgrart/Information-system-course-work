package ru.itmo.is.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PgPassTest {
    @Test void escapedPasswordAndFirstMatch() {
        assertEquals("a:b\\c", PgPass.find("# comment\nwrong:5432:studs:u:no\npg:5432:studs:u:a\\:b\\\\c\n*:*:*:*:fallback", "pg", 5432, "studs", "u"));
    }
    @Test void wildcardAndMalformedLines() {
        assertEquals("ok", PgPass.find("malformed\n*:*:studs:*:ok", "pg", 5432, "studs", "u"));
        assertThrows(IllegalStateException.class, () -> PgPass.find("pg:5432:other:u:no", "pg", 5432, "studs", "u"));
    }
}
