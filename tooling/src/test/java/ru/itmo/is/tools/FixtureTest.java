package ru.itmo.is.tools;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FixtureTest {
    @Test void remapsEscapedPatternsAsWellAsNames() {
        assertEquals("lf3check_users relname LIKE 'lf3check\\_%'", Fixture.remap("lf_users relname LIKE 'lf\\_%'", "lf3check_"));
    }
    @Test void remoteQuoteKeepsShellMetacharactersLiteral() {
        assertEquals("'x'\"'\"'$(pwd)'", Remote.quote("x'$(pwd)"));
    }
}
