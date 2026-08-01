package com.flora.syntax.bracket;

import com.flora.syntax.SyntaxException;

import java.util.ArrayList;
import java.util.List;

import static com.flora.syntax.bracket.BracketNode.Group;

/**
 * 括号结构分析器：按自定义左右定界符把输入切分为嵌套括号结构与被动文本。
 * <p>定界符可为任意非空字符串（如 {@code "("}/{@code ")"}、{@code "<%"}/{@code "%>"}），
 * 构造时校验非空且互不相同。嵌套通过递归匹配实现。</p>
 *
 * <pre>{@code
 * BracketAnalyzer a = new BracketAnalyzer("<%", "%>");
 * List<BracketNode> nodes = a.analyze("<%a <%b%> c%>");   // 嵌套 Group + Text
 * boolean ok = a.isBalanced("(a(b)c)");                    // true
 * }</pre>
 */
public final class BracketAnalyzer {

    private final String open;
    private final String close;

    public BracketAnalyzer(String open, String close) {
        if (open == null || open.isEmpty()) {
            throw new IllegalArgumentException("开定界符不能为空");
        }
        if (close == null || close.isEmpty()) {
            throw new IllegalArgumentException("闭定界符不能为空");
        }
        if (open.equals(close)) {
            throw new IllegalArgumentException("开/闭定界符必须不同: " + open);
        }
        this.open = open;
        this.close = close;
    }

    /**
     * 分析输入，返回顶层节点列表（Text/Group 混合）。
     * 未闭合的括号不抛异常——用 {@link #isBalanced(String)} 或 {@link #validate(String)} 判定。
     */
    public List<BracketNode> analyze(String input) {
        Cursor c = new Cursor(input);
        List<BracketNode> nodes = new ArrayList<>();
        while (!c.atEnd()) {
            if (c.startsWith(open)) {
                flushText(nodes, c);
                c.consume(open.length());
                nodes.add(parseGroup(c));
            } else {
                c.advance();
            }
        }
        flushText(nodes, c);
        return nodes;
    }

    /** 括号是否闭合：无顶层未闭合 open，且无孤立的 close。 */
    public boolean isBalanced(String input) {
        try {
            validate(input);
            return true;
        } catch (SyntaxException e) {
            return false;
        }
    }

    /** 校验闭合；不闭合抛 {@link SyntaxException}（带位置）。 */
    public String validate(String input) {
        Cursor c = new Cursor(input);
        int depth = 0;
        while (!c.atEnd()) {
            if (c.startsWith(open)) {
                c.consume(open.length());
                depth++;
            } else if (c.startsWith(close)) {
                if (depth == 0) {
                    throw SyntaxException.at(c.pos, "多余的闭定界符 " + close);
                }
                c.consume(close.length());
                depth--;
            } else {
                c.advance();
            }
        }
        if (depth > 0) {
            throw SyntaxException.at(c.openPos, "缺少 " + depth + " 个闭定界符 " + close);
        }
        return input;
    }

    /** 解析括号组内容，直到匹配的闭定界符；未闭合抛异常。 */
    private Group parseGroup(Cursor c) {
        List<BracketNode> children = new ArrayList<>();
        while (!c.atEnd()) {
            if (c.startsWith(close)) {
                flushText(children, c);
                c.consume(close.length());
                return new Group(open, children, close);
            }
            if (c.startsWith(open)) {
                flushText(children, c);
                c.consume(open.length());
                children.add(parseGroup(c));
            } else {
                c.advance();
            }
        }
        throw SyntaxException.at(c.openPos, "缺少闭定界符 " + close);
    }

    /** 把 Cursor 中累积的文本提交为 Text 节点。 */
    private static void flushText(List<BracketNode> nodes, Cursor c) {
        String text = c.takeText();
        if (!text.isEmpty()) {
            nodes.add(new BracketNode.Text(text));
        }
    }

    /** 输入游标：累积定界符之间的被动文本。 */
    private final class Cursor {
        private final String input;
        private int pos;
        private int textStart = 0;
        private int openPos = -1;

        Cursor(String input) {
            this.input = input;
        }

        boolean atEnd() {
            return pos >= input.length();
        }

        boolean startsWith(String s) {
            return input.startsWith(s, pos);
        }

        /** 消耗定界符并推进；记录最近一次 open 的位置（用于错误定位）。 */
        void consume(int len) {
            if (len == open.length()) {
                openPos = pos;
            }
            pos += len;
            textStart = pos;
        }

        /** 推进一个字符（成为被动文本的一部分）。 */
        void advance() {
            pos++;
        }

        /** 提交自 textStart 起累积的文本并复位。 */
        String takeText() {
            String text = input.substring(textStart, pos);
            textStart = pos;
            return text;
        }
    }
}
