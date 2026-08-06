package com.flora.syntax.bracket;

import com.flora.tag.ModuleEntry;
import com.flora.syntax.common.definition.Token;
import com.flora.syntax.common.definition.TokenKind;
import com.flora.syntax.common.exceptions.SyntaxException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.flora.syntax.bracket.BracketNode.Group;

/**
 * 括号结构分析器：按自定义左右定界符把输入切分为嵌套括号结构与被动文本。
 * <p>定界符可为任意非空字符串（如 {@code "("}/{@code ")"}、{@code "<%"}/{@code "%>"}），
 * 构造时校验非空且互不相同。内部先用共享词法基元（{@link Token}）把输入扫描为
 * 「开/闭定界符 + 被动文本」的 token 流（保留原文与空白，不丢弃任何字符），再据此递归
 * 组装嵌套结构；与 {@code com.flora.syntax.expr}/{@code peg} 共用同一套词法词汇。</p>
 *
 * <pre>{@code
 * BracketAnalyzer a = new BracketAnalyzer("<%", "%>");
 * List<BracketNode> nodes = a.analyze("<%a <%b%> c%>");   // 嵌套 Group + Text
 * boolean ok = a.isBalanced("(a(b)c)");                    // true
 * }</pre>
 */
@ModuleEntry
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
        List<Token> tokens = tokenize(input);
        Deque<List<BracketNode>> stack = new ArrayDeque<>();
        stack.push(new ArrayList<>());
        StringBuilder passive = new StringBuilder();
        int openPos = -1;
        for (Token t : tokens) {
            if (t.kind() == TokenKind.EOF) {
                break;
            }
            String text = t.text();
            if (text.equals(open)) {
                flush(passive, stack.peek());
                stack.push(new ArrayList<>());
                openPos = t.start();
            } else if (text.equals(close)) {
                flush(passive, stack.peek());
                if (stack.size() <= 1) {
                    throw SyntaxException.at(t.start(), "多余的闭定界符 " + close);
                }
                List<BracketNode> children = stack.pop();
                stack.peek().add(new Group(open, children, close));
            } else {
                passive.append(text);
            }
        }
        flush(passive, stack.peek());
        if (stack.size() > 1) {
            throw SyntaxException.at(openPos, "缺少闭定界符 " + close);
        }
        return stack.peek();
    }

    /** 括号是否闭合：无顶层未闭合 open，且无孤立的 close。 */
    public boolean isBalanced(String input) {
        try {
            analyze(input);
            return true;
        } catch (SyntaxException e) {
            return false;
        }
    }

    /** 校验闭合；不闭合抛 {@link SyntaxException}（带位置）。 */
    public String validate(String input) {
        analyze(input);
        return input;
    }

    /** 扫描为 token 流：开/闭定界符为 {@link TokenKind#OPERATOR}，其余原文累积为被动文本 token。 */
    private List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int n = input.length();
        int pos = 0;
        int line = 1;
        int col = 1;
        StringBuilder buf = new StringBuilder();
        int bufStart = 0;
        int bufLine = 1;
        int bufCol = 1;
        while (pos < n) {
            if (input.startsWith(open, pos)) {
                emitPassive(tokens, buf, bufStart, bufLine, bufCol);
                tokens.add(new Token(TokenKind.OPERATOR, open, open, pos, pos + open.length(), line, col));
                pos = advance(line, col, open, pos, open.length());
                bufStart = pos;
                bufLine = line;
                bufCol = col;
            } else if (input.startsWith(close, pos)) {
                emitPassive(tokens, buf, bufStart, bufLine, bufCol);
                tokens.add(new Token(TokenKind.OPERATOR, close, close, pos, pos + close.length(), line, col));
                pos = advance(line, col, close, pos, close.length());
                bufStart = pos;
                bufLine = line;
                bufCol = col;
            } else {
                if (buf.isEmpty()) {
                    bufStart = pos;
                    bufLine = line;
                    bufCol = col;
                }
                char c = input.charAt(pos);
                buf.append(c);
                if (c == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
                pos++;
            }
        }
        emitPassive(tokens, buf, bufStart, bufLine, bufCol);
        tokens.add(new Token(TokenKind.EOF, "EOF", "", n, n, line, col));
        return tokens;
    }

    /** 推进 line/col 跨过一段文本（用于定界符）。 */
    private int advance(int line, int col, String s, int pos, int len) {
        for (int k = 0; k < len; k++) {
            if (s.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return pos + len;
    }

    /** 把累积的被动文本提交为一个 TEXT token。 */
    private static void emitPassive(List<Token> tokens, StringBuilder buf, int start, int line, int col) {
        if (buf.length() > 0) {
            tokens.add(new Token(TokenKind.CUSTOM, "TEXT", buf.toString(), start, start + buf.length(), line, col));
            buf.setLength(0);
        }
    }

    /** 把被动文本提交为 Text 节点（若有内容）。 */
    private static void flush(StringBuilder passive, List<BracketNode> nodes) {
        if (passive.length() > 0) {
            nodes.add(new BracketNode.Text(passive.toString()));
            passive.setLength(0);
        }
    }
}
