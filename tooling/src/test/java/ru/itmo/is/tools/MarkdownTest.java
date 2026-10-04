package ru.itmo.is.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownTest {
    @Test void codeDoesNotBecomeHeadingsOrTables() {
        var blocks = Markdown.parse("## Текст\n\n```sql\n# literal\n| literal |\n\nSELECT 1;\n```\n");
        assertEquals(2, blocks.size()); assertEquals("code", blocks.get(1).kind());
        assertTrue(blocks.get(1).text().contains("\n\nSELECT 1;"));
    }
    @Test void tablesKeepEmptyCellsAndSkipAlignmentRow() {
        var block = Markdown.parse("| Поле | Значение |\n| :--- | ---: |\n| key | |\n").get(0);
        assertEquals(2, block.rows().size()); assertEquals("", block.rows().get(1).get(1));
    }
    @Test void inlineEscapesXmlAndPreservesLink() {
        String value = Reports.inline("**x < y** [Spring](https://spring.io/?a=1&b=2)", false);
        assertTrue(value.contains("x &lt; y")); assertTrue(value.contains("a=1&amp;b=2"));
    }
}
