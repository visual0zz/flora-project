package com.flora.codegen.engine;

import com.flora.codegen.CodeGenUtil;
import com.flora.codegen.CompiledTemplate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖语法分析 Node（ParserImpl 与各 Node 渲染分支）以及解析期错误分支。
 * 全部通过内存接口 CodeGenUtil.generate / precompile 触发，无文件 I/O。
 */
class NodeTest {

    private static List<CodeGenUtil.Generated> gen(String tpl) throws IOException {
        return CodeGenUtil.generate(tpl, Map.of());
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

    @Test
    void includeResolvesViaLeadingSlash() throws IOException {
        String included = "[${v}]";
        String host = "<#meta>@Param{ v: \"Z\" } @Path{ \"C.java\" }</#meta><#include \"/inc.ftl\">";
        String content = CodeGenUtil.generate(host, Map.of("inc.ftl", CodeGenUtil.precompile(included)))
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
        Map<String, CompiledTemplate> includes = Map.of(
                "a.ftl", CodeGenUtil.precompile(a),
                "b.ftl", CodeGenUtil.precompile(b));
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> CodeGenUtil.generate(a, includes));
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
                <#meta>@Param{ n: 3 } @Path{ \"C.txt\" }</#meta>
                <#for i:range(1, n)>${i}</#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("123", content);
    }

    @Test
    void forLoopWithRangeAndVariableRight() throws IOException {
        String tpl = """
                <#meta>@Param{ from: 2, to: 4 } @Path{ \"C.txt\" }</#meta>
                <#for i:range(from, to)>${i}</#for>
                """;
        String content = gen(tpl).get(0).content().trim();
        assertEquals("234", content);
    }

    @Test
    void forLoopWithRangeEmptyElse() throws IOException {
        String tpl = """
                <#meta>@Path{ \"C.txt\" }</#meta>
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

    @Test
    void elseTagWithArgumentThrows() {
        String tpl = "<#meta>@Path{ \"C.java\" }</#meta><#if true>yes<#else cond>no</#if>";
        CodeGenException ex = assertThrows(CodeGenException.class, () -> gen(tpl));
        assertTrue(ex.getMessage().contains("elseif"), ex.getMessage());
    }
}
