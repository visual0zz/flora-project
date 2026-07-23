package com.flora.ramet.idea.lexer;

import com.intellij.lexer.Lexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RametLexer 单元测试——验证 token 序列连续性和终止偏移量。
 *
 * <p>每个测试用例模拟 {@code LexerEditorHighlighter.doSetText()} 的验证逻辑：
 * <ol>
 *   <li>遍历 lexer 产生的所有 token</li>
 *   <li>检查每个 token 的 start 等于上一个 token 的 end（连续性）</li>
 *   <li>检查末尾 {@code getTokenType() == null} 时 {@code getTokenEnd() == getBufferEnd()}（终止偏移量）</li>
 * </ol>
 */
class RametLexerTest {

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

    /** 从指定初始状态启动 lexer 并验证。 */
    private static void assertValidTokensFromState(String source, int initialState) {
        Lexer lexer = new RametLexer();
        lexer.start(source, 0, source.length(), initialState);
        int prevEnd = 0;
        while (true) {
            var tokenType = lexer.getTokenType();
            if (tokenType == null) break;
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            assertEquals(prevEnd, start, "GAP at " + prevEnd);
            prevEnd = end;
            lexer.advance();
        }
        assertEquals(lexer.getBufferEnd(), prevEnd, "Termination mismatch from state " + initialState);
    }

    // ========== 基本 ==========

    @Test
    void emptyFile() {
        assertValidTokens("");
    }

    /** initialState=DONE：验证回退到 PASSIVE 不会导致闪退。 */
    @Test
    void initialStateDone() {
        assertValidTokensFromState("hello", 5);
    }

    /** initialState=DONE 对空文件也不应抛异常。 */
    @Test
    void initialStateDoneEmpty() {
        assertValidTokensFromState("", 5);
    }

    /** initialState 越大越界也应安全回退。 */
    @Test
    void initialStateOutOfRange() {
        assertValidTokensFromState("hello", 99);
    }

    @Test
    void plainText() {
        assertValidTokens("hello world");
    }

    @Test
    void trailingNewline() {
        assertValidTokens("hello\n");
    }

    @Test
    void multipleNewlines() {
        assertValidTokens("\n\n\n");
    }

    @Test
    void crlfNewline() {
        assertValidTokens("hello\r\nworld");
    }

    @Test
    void trailingCrlf() {
        assertValidTokens("hello\r\n");
    }

    // ========== 注释 ==========

    @Test
    void simpleComment() {
        assertValidTokens("<#-- note -->");
    }

    @Test
    void commentWithMeta() {
        assertValidTokens("<#-- @Param{ x: 1 } -->");
    }

    @Test
    void commentThenText() {
        assertValidTokens("<#-- @Param{ x: 1 } -->\nhello");
    }

    @Test
    void unclosedComment() {
        assertValidTokens("<#-- oops");
    }

    @Test
    void minimalComment() {
        assertValidTokens("<#-->");
    }

    // ========== 指令 ==========

    @Test
    void ifDirective() {
        assertValidTokens("<#if x>");
    }

    @Test
    void listDirective() {
        assertValidTokens("<#list i:1..10>");
    }

    @Test
    void includeDirective() {
        assertValidTokens("<#include \"path.ramet\">");
    }

    @Test
    void macroDirective() {
        assertValidTokens("<#macro greet who>");
    }

    @Test
    void elseDirective() {
        assertValidTokens("<#else>");
    }

    @Test
    void endTag() {
        assertValidTokens("</#if>");
    }

    @Test
    void unclosedDirective() {
        assertValidTokens("<#if x");
    }

    @Test
    void directiveArgsWithParens() {
        assertValidTokens("<#if (x > 0)>");
    }

    @Test
    void directiveWithOperators() {
        assertValidTokens("<#if x greaterThan 3>");
    }

    // ========== 新引擎指令（for, continue, break, meta） ==========

    @Test
    void forDirective() {
        assertValidTokens("<#for i:range(1, 10)>");
    }

    @Test
    void continueDirective() {
        assertValidTokens("<#continue>");
    }

    @Test
    void continueWithDepth() {
        assertValidTokens("<#continue 2>");
    }

    @Test
    void continueWithCondition() {
        assertValidTokens("<#continue x greaterThan 0>");
    }

    @Test
    void continueWithDepthAndCondition() {
        assertValidTokens("<#continue 2:x greaterThan 0>");
    }

    @Test
    void breakDirective() {
        assertValidTokens("<#break>");
    }

    @Test
    void breakWithDepth() {
        assertValidTokens("<#break 2>");
    }

    @Test
    void breakWithCondition() {
        assertValidTokens("<#break cond>");
    }

    @Test
    void metaBlock() {
        assertValidTokens("<#meta>\n@Param{ x: 1 }\n@Path{ \"test.java\" }\n</#meta>");
    }

    @Test
    void forLoopWithMetaTemplate() {
        assertValidTokens("<#meta>\n@Param{ x: 1 }\n</#meta>\n<#for i:items>\n${i}\n</#for>");
    }

    // ========== 变量插值 ==========

    @Test
    void simpleVar() {
        assertValidTokens("${x}");
    }

    @Test
    void varWithProperty() {
        assertValidTokens("${user.name}");
    }

    @Test
    void varWithFunction() {
        assertValidTokens("${concat(a, b)}");
    }

    @Test
    void nestedVars() {
        assertValidTokens("${a.b(c.d(e))}");
    }

    @Test
    void unclosedVar() {
        assertValidTokens("${x");
    }

    // ========== 宏调用 ==========

    @Test
    void macroCallNoArgs() {
        assertValidTokens("<@greet/>");
    }

    @Test
    void macroCallWithArg() {
        assertValidTokens("<@greet \"Bob\"/>");
    }

    // ========== 混合内容 ==========

    @Test
    void templateWithJavaCode() {
        assertValidTokens("<#-- @Param{ x: 1 } -->\n"
                + "public void test() {\n"
                + "    System.out.println(${x});\n"
                + "}");
    }

    @Test
    void forLoopTemplate() {
        assertValidTokens("<#list i:1..10>\n"
                + "item ${i}\n"
                + "</#list>");
    }

    @Test
    void ifElseTemplate() {
        assertValidTokens("<#if flag>YES<#else>NO</#if>");
    }

    @Test
    void templateWithExtremeMix() {
        // Generic types + interpolations + directives
        assertValidTokens("<#list idx:1..20>\n"
                + "    public <${sequenceJoin(\"T{}\", 1, idx, \",\")}> void foo() {}\n"
                + "</#list>");
    }

    // ========== 真实模板文件 ==========

    /**
     * 从文本构造 lexer 并直接测试——与 IDE 无关，不需要文件 I/O。
     * 覆盖 ByteKeyFastHashMap.ramet 的元数据注释块。
     */
    @Test
    void metaBlockLikeByteKeyFastHashMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("<#--\n");
        sb.append("@Param{\n");
        sb.append("  Byte: { name: \"Byte\", type: \"byte\", boxed: \"Byte\" },\n");
        sb.append("  Int: { name: \"Int\", type: \"int\", boxed: \"Integer\" }\n");
        sb.append("}\n");
        sb.append("@Combine{ V: [Byte, Short, Int, Long] }\n");
        sb.append("@Path{ concat(javaPackageToPath(package), \"/Byte2\", V.name, \"FastHashMap.java\") }\n");
        sb.append("-->\n");
        sb.append("package ${package};\n");
        sb.append("public class Byte2${V.name}FastHashMap {\n");
        sb.append("    private ${V.type}[] values;\n");
        sb.append("    public ${V.type} get(${V.type} key) { return values[0]; }\n");
        sb.append("}");
        assertValidTokens(sb.toString());
    }

    /**
     * 覆盖 Tuple.ramet 模板特征：<#list idx:1..20> 与 Java 泛型 <T> 共存。
     */
    @Test
    void tupleLikeTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("<#-- @Path{ \"Tuple.java\" } -->\n");
        sb.append("package pkg;\n");
        sb.append("<#list idx:1..20>\n");
        sb.append("    public static <T> Tuple<T> of() { return null; }\n");
        sb.append("</#list>\n");
        sb.append("    public String toString() {\n");
        sb.append("        StringBuilder sb = new StringBuilder(\"<\");\n");
        sb.append("        return sb.toString();\n");
        sb.append("    }\n");
        sb.append("}");
        assertValidTokens(sb.toString());
    }
}
