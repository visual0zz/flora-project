package com.flora.ramet.idea.lexer;

import com.intellij.lexer.Lexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 从模板文件加载真实 .ramet 模板进行词法分析（需要文件 I/O）。
 */
class RametLexerFileReadSlowTest {

    /** 对源码执行词法分析并验证 token 序列的完整性。 */
    private static void assertValidTokens(String source) {
        Lexer lexer = new RametLexer();
        lexer.start(source, 0, source.length(), 0);

        int prevEnd = 0;
        while (true) {
            var tokenType = lexer.getTokenType();
            if (tokenType == null) break;

            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();

            String msg = String.format("GAP at offset %d: prevEnd=%d tokenStart=%d type=%s '%s'",
                    prevEnd, prevEnd, start, tokenType,
                    source.substring(start, end).replace("\n", "\\n").replace("\r", "\\r"));

            assertEquals(prevEnd, start, msg);
            assertTrue(end > start, "Zero-length token at offset " + start + ": " + tokenType);

            prevEnd = end;
            lexer.advance();
        }

        assertEquals(lexer.getBufferEnd(), prevEnd,
                "Termination mismatch: last token ends at " + prevEnd
                        + " but buffer length is " + lexer.getBufferEnd());
    }

    /** 从测试资源目录加载 .ramet 模板文件。 */
    private static String loadTemplate(String filename) throws Exception {
        var url = RametLexerFileReadSlowTest.class.getResource("/templates/" + filename);
        assertNotNull(url, "Test resource not found: " + filename);
        return java.nio.file.Files.readString(
                java.nio.file.Paths.get(url.toURI()));
    }

    @Test
    void readFromByteKeyFastHashMapFile() throws Exception {
        assertValidTokens(loadTemplate("ByteKeyFastHashMap.ramet"));
    }

    @Test
    void readFromFastHashMapFile() throws Exception {
        assertValidTokens(loadTemplate("FastHashMap.ramet"));
    }

    @Test
    void readFromFastHashMapConsumerFile() throws Exception {
        assertValidTokens(loadTemplate("FastHashMapConsumer.ramet"));
    }

    @Test
    void readFromTupleFile() throws Exception {
        assertValidTokens(loadTemplate("Tuple.ramet"));
    }

    @Test
    void readFromTupleNFile() throws Exception {
        assertValidTokens(loadTemplate("TupleN.ramet"));
    }
}
