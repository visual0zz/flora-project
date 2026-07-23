package com.flora.codegen.engine;

import com.flora.codegen.engine.ast.Node;
import com.flora.codegen.engine.parser.Lexer;
import com.flora.codegen.engine.parser.Parser;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.TemplateBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖模板主体 {@link TemplateBody#render} 的分支：
 * <ul>
 *   <li>被移除的指令节点（如 {@code false} 条件分支）周围的换行符保留</li>
 * </ul>
 */
class TemplateBodyTest {

    @Test
    void removedDirectiveLeavesSurroundingNewlines() throws IOException {
        String tpl = "line1\n<#if false>x</#if>\nline2";
        List<Node> nodes = Parser.parse(Lexer.lex(tpl));
        String out = TemplateBody.of(nodes).render(Context.of(Map.of(), Map.of()));
        assertTrue(out.contains("line1"), out);
        assertTrue(out.contains("line2"), out);
    }
}
