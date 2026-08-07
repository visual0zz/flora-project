package com.flora.ramet.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 {@link OutputEscaper} 的各转义方案与默认（不转义）行为。
 */
class OutputEscaperTest {

    @Test
    void htmlEscapesSpecialChars() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;",
                OutputEscaper.escape("&<>\"'", "html"));
    }

    @Test
    void xmlUsesAposForSingleQuote() {
        assertEquals("&lt;b&gt;&apos;x&apos;&lt;/b&gt;",
                OutputEscaper.escape("<b>'x'</b>", "xml"));
    }

    @Test
    void jsEscapesQuotesAndControlChars() {
        assertEquals("\\\"a\\\"\\nb\\n\\'",
                OutputEscaper.escape("\"a\"\nb\n'", "js"));
    }

    @Test
    void nullOrEmptySchemeLeavesUnchanged() {
        assertEquals("<a>&", OutputEscaper.escape("<a>&", null));
        assertEquals("<a>&", OutputEscaper.escape("<a>&", ""));
        assertEquals("<a>&", OutputEscaper.escape("<a>&", "none"));
    }

    @Test
    void unknownSchemeThrows() {
        assertThrows(CodeGenException.class, () -> OutputEscaper.escape("x", "foo"));
    }
}
