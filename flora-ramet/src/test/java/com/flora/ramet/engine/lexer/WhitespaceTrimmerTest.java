package com.flora.ramet.engine.lexer;

import com.flora.ramet.engine.ast.Node;
import com.flora.ramet.engine.parser.Parser;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.TemplateBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 覆盖 {@link WhitespaceTrimmer} 的空白规整行为：
 * 独占一行的指令被移除且不残留前导空白，内联指令与纯文本（含空行）不受影响。
 */
class WhitespaceTrimmerTest {

    /** 经 WhitespaceTrimmer 规整后再解析渲染，得到最终文本。 */
    private static String render(String tpl) throws IOException {
        return render(tpl, Map.of());
    }

    private static String render(String tpl, Map<String, Object> params) throws IOException {
        List<Node> nodes = Parser.parse(WhitespaceTrimmer.trim(Lexer.lex(tpl)));
        return TemplateBody.of(nodes).render(Context.of(params, Map.of()));
    }

    @Test
    void standaloneDirectiveLeavesNoLeadingWhitespace() throws IOException {
        // if 与 endif 各自独占一行：前导缩进被剥除，body 缩进保留，邻居以单个换行连接
        String tpl = "a\n<#if x>\n    b\n</#if>\nc";
        assertEquals("a\n    b\nc", render(tpl, Map.of("x", true)));
    }

    @Test
    void inlineDirectiveKeepsSurroundingNewlines() throws IOException {
        // 内联指令（同一行内紧跟内容）不触碰周围换行
        String tpl = "a\n<#if true>b</#if>";
        assertEquals("a\nb", render(tpl));
    }

    @Test
    void trueBranchKeepsPrecedingNewline() throws IOException {
        // 修复旧实现吞掉前导换行的怪癖：Hello\n<#if true>World → Hello\nWorld
        String tpl = "Hello\n<#if true>World</#if>";
        assertEquals("Hello\nWorld", render(tpl));
    }

    @Test
    void falseBranchStandaloneLineCollapsesCleanly() throws IOException {
        // 空 body 的独占指令行被整行移除，上下两行由单个换行连接
        String tpl = "line1\n<#if false>\n</#if>\nline2";
        assertEquals("line1\nline2", render(tpl));
    }

    @Test
    void commentLineDisappears() throws IOException {
        // 注释独占一行：整行消失，邻居保留
        String tpl = "a\n<#-- x -->\nb";
        assertEquals("a\nb", render(tpl));
    }

    @Test
    void blankLinesPreservedWithoutDirective() throws IOException {
        // 无指令时空行原样保留
        String tpl = "a\n\nb";
        assertEquals("a\n\nb", render(tpl));
    }

    @Test
    void fileStartIndentedDirectiveDropped() throws IOException {
        // 文件开头带缩进的独占指令：缩进与其行被整段删除
        String tpl = "   <#if x>\nb</#if>";
        assertEquals("b", render(tpl, Map.of("x", true)));
    }

    @Test
    void nonStandaloneDirectiveIsNotTrimmed() throws IOException {
        // 指令行末尾还跟着内容（非独占一行）→ 不规整，换行保留
        String tpl = "a\n<#if x>body</#if>";
        assertEquals("a\nbody", render(tpl, Map.of("x", true)));
    }
}
