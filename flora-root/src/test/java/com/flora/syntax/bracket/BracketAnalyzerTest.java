package com.flora.syntax.bracket;

import com.flora.syntax.exceptions.SyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BracketAnalyzer} 测试。
 */
class BracketAnalyzerTest {

    private static List<String> texts(BracketNode n) {
        return switch (n) {
            case BracketNode.Text t -> List.of(t.text());
            case BracketNode.Group g -> {
                java.util.List<String> out = new java.util.ArrayList<>();
                for (BracketNode c : g.children()) {
                    out.addAll(texts(c));
                }
                yield out;
            }
        };
    }

    // ── 基础 ──

    @Test
    void nestedBrackets() {
        BracketAnalyzer a = new BracketAnalyzer("(", ")");
        List<BracketNode> nodes = a.analyze("(a(b)c)");
        assertEquals(1, nodes.size());
        BracketNode.Group outer = (BracketNode.Group) nodes.get(0);
        assertEquals("(", outer.open());
        assertEquals(")", outer.close());
        // children: a + (b) + c
        assertEquals(3, outer.children().size());
        assertTrue(outer.children().get(1) instanceof BracketNode.Group inner
                && inner.children().size() == 1);
    }

    @Test
    void customDelimiter() {
        BracketAnalyzer a = new BracketAnalyzer("<%", "%>");
        List<BracketNode> nodes = a.analyze("<%a <%b%> c%>");
        assertEquals(1, nodes.size());
        BracketNode.Group outer = (BracketNode.Group) nodes.get(0);
        assertEquals("<%", outer.open());
        assertEquals("%>", outer.close());
        assertEquals(3, outer.children().size());
    }

    @Test
    void passiveText() {
        BracketAnalyzer a = new BracketAnalyzer("(", ")");
        List<BracketNode> nodes = a.analyze("x(ab)y");
        assertEquals(3, nodes.size());
        assertTrue(nodes.get(0) instanceof BracketNode.Text && ((BracketNode.Text) nodes.get(0)).text().equals("x"));
        assertTrue(nodes.get(1) instanceof BracketNode.Group);
        assertTrue(nodes.get(2) instanceof BracketNode.Text && ((BracketNode.Text) nodes.get(2)).text().equals("y"));
    }

    @Test
    void noBracket() {
        BracketAnalyzer a = new BracketAnalyzer("(", ")");
        List<BracketNode> nodes = a.analyze("plain text");
        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0) instanceof BracketNode.Text);
        assertTrue(a.isBalanced("plain text"));
    }

    // ── 闭合校验 ──

    @Test
    void unbalancedThrows() {
        BracketAnalyzer a = new BracketAnalyzer("(", ")");
        assertFalse(a.isBalanced("(a"));
        assertThrows(SyntaxException.class, () -> a.validate("(a"));
        assertFalse(a.isBalanced("a)"));
        assertThrows(SyntaxException.class, () -> a.validate("a)"));
    }

    @Test
    void balancedValidates() {
        BracketAnalyzer a = new BracketAnalyzer("(", ")");
        assertTrue(a.isBalanced("(a(b)c)"));
        assertEquals("(a)", a.validate("(a)"));
    }

    @Test
    void unbalancedCustomDelimiter() {
        BracketAnalyzer a = new BracketAnalyzer("<%", "%>");
        assertFalse(a.isBalanced("<%a"));
        assertThrows(SyntaxException.class, () -> a.validate("<%a"));
    }

    // ── 构造校验 ──

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new BracketAnalyzer("", ")"));
        assertThrows(IllegalArgumentException.class, () -> new BracketAnalyzer("(", ""));
        assertThrows(IllegalArgumentException.class, () -> new BracketAnalyzer("(", "("));
    }
}
