package com.flora.ramet.engine.ast;
import com.flora.ramet.engine.CodeGenException;
import com.flora.ramet.engine.Template;
import com.flora.ramet.engine.TemplateEngine;
import com.flora.ramet.engine.TemplateRepository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖语法分析 Node（ParserImpl 与各 Node 渲染分支）以及解析期错误分支。
 * 全部通过内存接口 TemplateEngine.generate / precompile 触发，无文件 I/O。
 */
class NodeTest {

    private static List<TemplateEngine.Generated> gen(String tpl) throws IOException {
        return TemplateEngine.generate(tpl, TemplateRepository.none());
    }

    @Test
    void textCommentMetaAndNewlineNodesRender() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.java" }</#meta>
                visible<#-- hidden -->more
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("visible"), content);
        assertTrue(content.contains("more"), content);
        // 注释不输出
        assertEquals(-1, content.indexOf("hidden"));
    }

    @Test
    void dollarInPassiveIsLiteral() throws IOException {
        // 被动区域零转义：\$ 原样输出；单独的 $（不跟 {）也是字面量
        String tpl = """
                <#meta>@Param{ v: "V" } @Path{ "C.java" }</#meta>
                ${v} cost \\$5
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("V cost \\$5"), content);
    }

    @Test
    void hashInPassiveIsLiteral() throws IOException {
        // 被动区域零转义：\# 原样输出
        String tpl = "<#meta>@Path{ \"C.txt\" }</#meta>\\# hello";
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("\\# hello"), content);
    }

    @Test
    void ifAndElseBranches() throws IOException {
        String tpl = """
                <#meta>@Param{ a: true, b: false } @Path{ "C.txt" }</#meta>
                <#if a>YES</#if><#if b>NO</#if><#if b>x<#else>ELSE</#if>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("YES"), content);
        assertTrue(content.contains("ELSE"), content);
        assertEquals(-1, content.indexOf("NO"));
    }

    @Test
    void forLoopAndEmptyElse() throws IOException {
        String tpl = """
                <#meta>@Param{ empty: [], full: ["p", "q"] } @Path{ "C.java" }</#meta>
                <#for it:full>-${it}-</#for>
                <#for it:empty>never<#else>NONE</#for>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("-p-"), content);
        assertTrue(content.contains("-q-"), content);
        assertTrue(content.contains("NONE"), content);
        assertEquals(-1, content.indexOf("never"));
    }

    @Test
    void forWithIndexVarExposesZeroBasedCounter() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b", "c"] } @Path{ "C.txt" }</#meta>
                <#for i,v:items>${i}:${v};</#for>
                """;
        String content = gen(tpl).get(0).content().replace("\n", "");
        assertEquals("0:a;1:b;2:c;", content);
    }

    @Test
    void forWithoutIndexStillSupported() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b"] } @Path{ "C.txt" }</#meta>
                <#for v:items>[${v}]</#for>
                """;
        String content = gen(tpl).get(0).content().replace("\n", "");
        assertEquals("[a][b]", content);
    }

    @Test
    void forIndexAcceptsCustomName() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["x"] } @Path{ "C.txt" }</#meta>
                <#for n,el:items>${n}${el}</#for>
                """;
        String content = gen(tpl).get(0).content().replace("\n", "");
        assertEquals("0x", content);
    }

    @Test
    void forIndexExposesLongForArithmetic() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b"] } @Path{ "C.txt" }</#meta>
                <#for i,v:items>${i}+1=${i greaterThanOrEquals 1}</#for>
                """;
        String content = gen(tpl).get(0).content().replace("\n", "");
        assertEquals("0+1=false1+1=true", content);
    }

    @Test
    void macroDefinitionAndCallWithArgs() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.java" }</#meta>
                <#macro greet:who>Hi ${who}</#macro>
                <@greet "Bob"/>
                <@greet/>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("Hi Bob"), content);
        assertTrue(content.contains("Hi "), content);
    }

    // ---- 宏默认参数（= 默认值） ----

    @Test
    void macroDefaultParamUsedWhenArgOmitted() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.java" }</#meta>
                <#macro greet:who="Guest">Hi ${who}</#macro>
                <@greet/>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("Hi Guest"), content);
    }

    @Test
    void macroDefaultParamOverriddenByArg() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.java" }</#meta>
                <#macro greet:who="Guest">Hi ${who}</#macro>
                <@greet "Bob"/>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("Hi Bob"), content);
        assertEquals(-1, content.indexOf("Guest"));
    }

    // ---- 跨文件宏引用（include 一个定义宏的库后调用） ----

    @Test
    void crossFileMacroIncludeThenCall() throws IOException {
        String lib = "<#macro greet:who>Hi ${who}</#macro>";
        String host = "<#meta>@Path{ \"H.java\" }</#meta><#include \"lib.ftl\"><@greet \"Bob\"/>";
        String content = TemplateEngine.generate(host,
                        TemplateRepository.from(Map.of("lib.ftl", TemplateEngine.precompile(lib))))
                .get(0).content();
        assertTrue(content.contains("Hi Bob"), content);
    }

    @Test
    void crossFileMacroWithDefaultParam() throws IOException {
        String lib = "<#macro greet:who=\"Guest\">Hi ${who}</#macro>";
        String host = "<#meta>@Path{ \"H.java\" }</#meta><#include \"lib.ftl\"><@greet/><@greet \"Bob\"/>";
        String content = TemplateEngine.generate(host,
                        TemplateRepository.from(Map.of("lib.ftl", TemplateEngine.precompile(lib))))
                .get(0).content();
        assertTrue(content.contains("Hi Guest"), content);
        assertTrue(content.contains("Hi Bob"), content);
    }

    @Test
    void nestedCrossFileMacroHostCallsLibMacro() throws IOException {
        String lib = "<#macro wrap:x>[${x}]</#macro>";
        String host = """
                <#meta>@Path{ "H.java" }</#meta>
                <#macro use:y><@wrap y/></#macro>
                <#include "lib.ftl">
                <@use "Z"/>
                """;
        String content = TemplateEngine.generate(host,
                        TemplateRepository.from(Map.of("lib.ftl", TemplateEngine.precompile(lib))))
                .get(0).content();
        assertTrue(content.contains("[Z]"), content);
    }

    @Test
    void callingCrossFileMacroBeforeIncludeThrows() {
        String lib = "<#macro greet:who>Hi ${who}</#macro>";
        String host = "<#meta>@Path{ \"H.java\" }</#meta><@greet \"Bob\"/><#include \"lib.ftl\">";
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> TemplateEngine.generate(host,
                        TemplateRepository.from(Map.of("lib.ftl", TemplateEngine.precompile(lib)))));
        assertTrue(ex.getMessage().contains("未定义"), ex.getMessage());
    }

    // ---- 循环控制：break / continue 实际渲染 ----

    @Test
    void forLoopBreakRendersOnlyFirstItem() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b", "c"] } @Path{ "C.txt" }</#meta>
                <#for it:items>${it}<#break></#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("a", content);
    }

    @Test
    void forLoopContinueSkipsMatchedItem() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b", "c"] } @Path{ "C.txt" }</#meta>
                <#for it:items><#if it equals "b"><#continue></#if>${it}</#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("ac", content);
    }

    // ---- 循环控制：break / continue 的 depth 与条件参数 ----

    @Test
    void forLoopBreakWithDepthExitsNestedLoops() throws IOException {
        String tpl = "<#meta>@Param{ outer: [1, 2], inner: [\"a\", \"b\"] } @Path{ \"C.txt\" }</#meta>"
                + "<#for i:outer>[${i}<#for j:inner>${j}<#break 2></#for>]</#for>";
        String content = gen(tpl).get(0).content().trim();
        assertEquals("[1a", content);
    }

    @Test
    void forLoopContinueWithDepthSkipsBothLevels() throws IOException {
        String tpl = "<#meta>@Param{ outer: [1, 2], inner: [\"a\", \"b\"] } @Path{ \"C.txt\" }</#meta>"
                + "<#for i:outer><#for j:inner>${i}${j}<#continue 2></#for></#for>";
        String content = gen(tpl).get(0).content().trim();
        assertEquals("1a2a", content);
    }

    @Test
    void forLoopBreakWithCondition() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b", "c"] } @Path{ "C.txt" }</#meta>
                <#for it:items>${it}<#if it equals "b"><#break></#if></#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("ab", content);
    }

    // ---- 宏体内部包含控制结构 / 同文件宏互相调用 ----

    @Test
    void macroBodyContainsForLoop() throws IOException {
        String tpl = """
                <#meta>@Param{ items: ["a", "b"] } @Path{ "C.java" }</#meta>
                <#macro list:xs><#for x:xs>-${x}-</#for></#macro>
                <@list items/>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("-a-"), content);
        assertTrue(content.contains("-b-"), content);
    }

    @Test
    void sameFileMacroCallsAnotherMacro() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.java" }</#meta>
                <#macro inner:x>(${x})</#macro>
                <#macro outer:y><@inner y/></#macro>
                <@outer "Z"/>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("(Z)"), content);
    }

    @Test
    void twoLevelCrossFileMacroChain() throws IOException {
        String lib1 = "<#macro A:x>[${x}]</#macro>";
        String lib2 = "<#macro B:y><@A y/></#macro>";
        String host = "<#meta>@Path{ \"H.java\" }</#meta>"
                + "<#include \"lib1.ftl\"><#include \"lib2.ftl\"><@B \"Z\"/>";
        String content = TemplateEngine.generate(host,
                        TemplateRepository.from(Map.of(
                                "lib1.ftl", TemplateEngine.precompile(lib1),
                                "lib2.ftl", TemplateEngine.precompile(lib2))))
                .get(0).content();
        assertTrue(content.contains("[Z]"), content);
    }

    @Test
    void includeResolvesViaLeadingSlash() throws IOException {
        String included = "[${v}]";
        String host = "<#meta>@Param{ v: \"Z\" } @Path{ \"C.java\" }</#meta><#include \"/inc.ftl\">";
        String content = TemplateEngine.generate(host, TemplateRepository.from(Map.of("inc.ftl", TemplateEngine.precompile(included))))
                .get(0).content();
        assertTrue(content.contains("[Z]"), content);
    }

    @Test
    void includeMissingTemplateThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><#include \"missing.ftl\">";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("未找到"), ex.getMessage());
    }

    @Test
    void includeNonStringPathThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><#include 123>";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("字符串"), ex.getMessage());
    }

    @Test
    void includeCycleThrows() {
        String a = "<#meta>@Path{ \"A.java\" }</#meta><#include \"b.ftl\">";
        String b = "<#meta>@Path{ \"B.java\" }</#meta><#include \"a.ftl\">";
        Map<String, Template> includes = Map.of(
                "a.ftl", TemplateEngine.precompile(a),
                "b.ftl", TemplateEngine.precompile(b));
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> TemplateEngine.generate(a, TemplateRepository.from(includes)));
        assertTrue(ex.getMessage().contains("循环"), ex.getMessage());
    }

    @Test
    void unexpectedEndTokenThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen("</#if>"));
        assertTrue(ex.getMessage().contains("结束"), ex.getMessage());
    }

    @Test
    void listWithoutAsThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><#for x>a</#for>";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains(":"), ex.getMessage());
    }

    @Test
    void unknownDirectiveThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen("<#wrong>"));
        assertTrue(ex.getMessage().contains("未知指令"), ex.getMessage());
    }

    @Test
    void undefinedMacroThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><@nope/>";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("未定义"), ex.getMessage());
    }

    @Test
    void forLoopWithRange() throws IOException {
        String tpl = """
                <#meta>@Param{ n: 3 } @Path{ "C.txt" }</#meta>
                <#for i:range(1, n)>${i}</#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("123", content);
    }

    @Test
    void forLoopWithRangeAndVariableRight() throws IOException {
        String tpl = """
                <#meta>@Param{ from: 2, to: 4 } @Path{ "C.txt" }</#meta>
                <#for i:range(from, to)>${i}</#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("234", content);
    }

    @Test
    void forLoopWithRangeEmptyElse() throws IOException {
        String tpl = """
                <#meta>@Path{ "C.txt" }</#meta>
                <#for i:range(3, 1)>x</#for>
                """;
        assertTrue(gen(tpl).get(0).content().trim().isEmpty());
    }

    @Test
    void ifWithComparisonOperator() throws IOException {
        String tpl = """
                <#meta>@Param{ x: 5 } @Path{ "C.txt" }</#meta>
                <#if x greaterThan 3>YES</#if><#if x lessThanOrEquals 3>NO</#if>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("YES"), content);
        assertEquals(-1, content.indexOf("NO"));
    }

    @Test
    void ifElseIfElseChain() throws IOException {
        String tpl = """
                <#meta>@Param{ a: false, b: true, c: false } @Path{ "C.txt" }</#meta>
                <#if a>AA<#elseif b>BB<#elseif c>CC<#else>DD</#if>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("BB"), content);
        assertEquals(-1, content.indexOf("AA"));
        assertEquals(-1, content.indexOf("CC"));
        assertEquals(-1, content.indexOf("DD"));
    }

    @Test
    void ifElseIfFallsThroughToFinalElse() throws IOException {
        String tpl = """
                <#meta>@Param{ a: false, b: false } @Path{ "C.txt" }</#meta>
                <#if a>AA<#elseif b>BB<#else>DD</#if>
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("DD"), content);
    }

    // ---- @Config{ strictNull } 严格 null 求值 ----

    @Test
    void strictNullEnabledThrowsOnNullInterpolation() {
        String tpl = "<#meta>@Config{ strictNull: true } @Path{ \"C.java\" }</#meta>${missing}";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("null"), ex.getMessage());
    }

    @Test
    void strictNullDisabledToleratesNull() throws IOException {
        String tpl = "<#meta>@Config{ strictNull: false } @Path{ \"C.java\" }</#meta>${missing}";
        List<TemplateEngine.Generated> results = gen(tpl);
        assertEquals(1, results.size());
        // 容错：null 输出为空串，不抛异常，且字面 "missing" 不应出现在输出
        assertEquals(-1, results.get(0).content().indexOf("missing"));
    }

    // ---- 转义函数 html / xml / js（仅转义插值，不转义字面代码） ----

    @Test
    void escapeFunctionHtmlEscapesInterpolatedValue() throws IOException {
        String tpl = """
                <#meta>@Param{ x: "a<b&c" } @Path{ "C.java" }</#meta>
                ${html(x)}
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("a&lt;b&amp;c"), content);
    }

    @Test
    void escapeFunctionXmlEscapesInterpolatedValue() throws IOException {
        String tpl = """
                <#meta>@Param{ x: "<b>'x'</b>" } @Path{ "C.java" }</#meta>
                ${xml(x)}
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("&lt;b&gt;&apos;x&apos;&lt;/b&gt;"), content);
    }

    @Test
    void escapeFunctionJsEscapesInterpolatedValue() throws IOException {
        String tpl = """
                <#meta>@Param{ x: "a'b" } @Path{ "C.java" }</#meta>
                ${js(x)}
                """;
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("a\\'b"), content);
    }

    @Test
    void escapeFunctionOnlyEscapesInterpolatedValueNotLiteral() throws IOException {
        // 字面 <div> 不应被转义；只有 ${html(x)} 的插值被转义
        String tpl = "<#meta>@Param{ x: \"a<b\" } @Path{ \"C.html\" }</#meta><div>${html(x)}</div>";
        String content = gen(tpl).get(0).content();
        assertTrue(content.contains("<div>"), "字面 <div> 不应被转义: " + content);
        assertTrue(content.contains("a&lt;b"), "插值应被转义: " + content);
    }

    @Test
    void elseTagWithArgumentThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><#if true>yes<#else cond>no</#if>";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("elseif"), ex.getMessage());
    }
}
