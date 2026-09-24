package com.flora.ramet.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 {@link OutputEscaper} 的按值转义原语（被内置函数 html / xml / js 复用）。
 */
class OutputEscaperTest {

    @Test
    void htmlEscapesSpecialChars() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;",
                OutputEscaper.escapeHtml("&<>\"'"));
    }

    @Test
    void xmlUsesAposForSingleQuote() {
        assertEquals("&lt;b&gt;&apos;x&apos;&lt;/b&gt;",
                OutputEscaper.escapeXml("<b>'x'</b>"));
    }

    @Test
    void jsEscapesQuotesAndControlChars() {
        assertEquals("\\\"a\\\"\\nb\\n\\'",
                OutputEscaper.escapeJs("\"a\"\nb\n'"));
    }

    @Test
    void emptyStringEscapesToEmpty() {
        assertEquals("", OutputEscaper.escapeHtml(""));
        assertEquals("", OutputEscaper.escapeXml(""));
        assertEquals("", OutputEscaper.escapeJs(""));
    }
}
