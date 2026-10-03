package ru.itmo.is.poteryashki;
import org.junit.jupiter.api.Test;
import ru.itmo.is.poteryashki.persistence.SqlNames;
import static org.junit.jupiter.api.Assertions.*;
class SqlNamesTest {
    @Test void prefixCannotInjectSql() {
        assertThrows(IllegalArgumentException.class,()->new SqlNames("lf_;drop schema public;"));
        assertThrows(IllegalArgumentException.class,()->new SqlNames("users"));
        assertEquals("select lf3check_users",new SqlNames("lf3check_").sql("select lf_users"));
    }
}
