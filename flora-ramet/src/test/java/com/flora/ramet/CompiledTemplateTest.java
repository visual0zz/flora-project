package com.flora.ramet;

import com.flora.ramet.engine.lexer.Lexer;
import com.flora.ramet.engine.parser.Parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖预编译模板 {@link CompiledTemplate}（record）的构造与访问分支：
 * <ul>
 *   <li>正常构造：持有节点列表</li>
 *   <li>非法参数：{@code null} 节点列表被构造器拒绝</li>
 * </ul>
 */
class CompiledTemplateTest {

    @Test
    void holdsNodes() {
        CompiledTemplate ct = new CompiledTemplate(
                Parser.parse(Lexer.lex("hello")));
        assertNotNull(ct.nodes());
    }

    @Test
    void nullNodesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompiledTemplate(null));
    }
}
