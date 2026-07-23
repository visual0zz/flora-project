package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.Token;
import com.flora.codegen.engine.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Ramet 模板引擎的语法分析器——将 {@link Token} 列表构建为 AST（抽象语法树）。
 *
 * <p>语法分析流程：
 * <ol>
 *   <li>遍历 Token 序列，按 {@link Token.Type} 分发到对应的节点构造逻辑</li>
 *   <li>支持指令嵌套：为 {@code <#if>}、{@code <#list>}、{@code <#macro>} 构建子树，
 *       通过 {@link #parseBlock()} 递归解析子块</li>
 *   <li>自动处理 {@code <#else>} 分支和 {@code </#>} 结束标签的匹配</li>
 *   <li>对 CommentNode 做元数据识别：将疑似 {@code @Param{...}} / {@code @Combine{...}} /
 *       {@code @Path{...}} 的注释块解析为 {@link MetaNode}</li>
 * </ol>
 *
 * <p>支持的 AST 节点类型见 {@code com.flora.codegen.engine.ast} 包。
 *
 * @see com.flora.codegen.engine.ast.Node
 */
public class Parser {
    List<Token> toks;
    String source;
    int p = 0;

    public Parser(List<Token> toks, String source) {
        this.toks = toks;
        this.source = source;
    }

    /** 便捷入口：从 Token 列表直接解析得到 Node 列表（不含源文件信息）。 */
    public static List<Node> parse(List<Token> toks) {
        return new Parser(toks, null).parseAll();
    }

    /** 便捷入口：从 Token 列表直接解析得到 Node 列表（携带源文件信息）。 */
    public static List<Node> parse(List<Token> toks, String source) {
        return new Parser(toks, source).parseAll();
    }

    public List<Node> parseAll() {
        List<Node> out = new ArrayList<>();
        boolean hasMetaNode = false;
        while (p < toks.size()) {
            Token t = toks.get(p);
            if (t.type() == Token.Type.END) throw TemplateUtils.err(t.line(), t.col(), source, "多余的结束标签");
            Node node = parseOne(t);
            if (node instanceof MetaNode) {
                if (hasMetaNode) {
                    throw new CodeGenException("只允许一个 meta 块，实际找到多个");
                }
                hasMetaNode = true;
            }
            out.add(node);
        }
        return out;
    }

    private List<Node> parseBlock() {
        List<Node> out = new ArrayList<>();
        while (p < toks.size()) {
            Token t = toks.get(p);
            if (t.type() == Token.Type.ELSE || t.type() == Token.Type.ELSEIF
                    || t.type() == Token.Type.END) return out;
            out.add(parseOne(t));
        }
        return out;
    }

    private Node parseOne(Token t) {
        return switch (t.type()) {
            case PASSIVE -> { p++; yield new TextNode(t.text(), t.leadingNewline()); }
            case NEWLINE -> {
                p++;
                // 被抑制的换行不生成节点
                if (t.suppressed()) yield new TextNode("", false);
                yield new TextNode("\n", false);
            }
            case VAR -> { p++; yield new VarNode(t.text(), t.line(), t.leadingNewline()); }
            case COMMENT -> { p++; yield new CommentNode(t.text(), t.line()); }
            case META -> {
                p++;
                MetaParser.MetaData data = MetaParser.parse(t.text(), t.line());
                yield new MetaNode(data);
            }
            case IF -> {
                p++;
                Object cond = Lson.parse(t.text(), t.line());
                List<Node> thenB = parseBlock();
                List<Node> elseB = consumeIfElse(t.line());
                yield new IfNode(cond, thenB, elseB);
            }
            case FOR -> {
                p++;
                int idx = t.text().indexOf(':');
                if (idx < 0) throw TemplateUtils.err(t.line(), t.col(), source, "<#for 语法应为 'var:expr'");
                String var = t.text().substring(0, idx).trim();
                String iterExpr = t.text().substring(idx + 1).trim();
                Object iter = Lson.parse(iterExpr, t.line());
                List<Node> body = parseBlock();
                List<Node> elseB = consumeElseEnd(t.line(), "<#for");
                yield new ForNode(var, iter, body, elseB);
            }
            case CONTINUE -> { p++; yield parseContinueBreak(t, true); }
            case BREAK -> { p++; yield parseContinueBreak(t, false); }
            case INCLUDE -> {
                p++;
                yield new IncludeNode(Lson.parse(t.text(), t.line()), t.line());
            }
            case MACRO -> {
                p++;
                String text = t.text().trim();
                int colon = text.indexOf(':');
                String name, paramPart;
                if (colon < 0) {
                    name = text;
                    paramPart = "";
                } else {
                    name = text.substring(0, colon).trim();
                    paramPart = text.substring(colon + 1);
                }
                List<MacroDefNode.MacroParam> params = new ArrayList<>();
                if (!paramPart.isBlank()) {
                    for (String seg : splitByComma(paramPart)) {
                        int eq = seg.indexOf('=');
                        if (eq < 0) {
                            params.add(new MacroDefNode.MacroParam(seg.trim(), null));
                        } else {
                            String pname = seg.substring(0, eq).trim();
                            String defExpr = seg.substring(eq + 1).trim();
                            Object defLson = defExpr.isEmpty() ? null : Lson.parse(defExpr, t.line());
                            params.add(new MacroDefNode.MacroParam(pname, defLson));
                        }
                    }
                }
                List<Node> body = parseBlock();
                Token end = toks.get(p);
                if (end.type() != Token.Type.END)
                    throw TemplateUtils.err(end.line(), end.col(), source, "<#macro 缺少对应的结束标签");
                p++;
                yield new MacroDefNode(name, params, body);
            }
            case MACRO_CALL -> {
                p++;
                yield new MacroCallNode(t.text(), t.lsonArgs(), t.line());
            }
            default -> throw TemplateUtils.err(t.line(), t.col(), source, "未知指令: " + t.type());
        };
    }

    private List<Node> consumeElseEnd(int line, String what) {
        Token term = toks.get(p);
        if (term.type() == Token.Type.ELSE) {
            p++;
            List<Node> eb = parseBlock();
            Token end = toks.get(p);
            if (end.type() != Token.Type.END)
                throw TemplateUtils.err(end.line(), end.col(), source, what + " 缺少对应的结束标签");
            p++;
            return eb;
        }
        if (term.type() == Token.Type.END) {
            p++;
            return null;
        }
        throw TemplateUtils.err(term.line(), term.col(), source, what + " 缺少对应的结束标签");
    }

    /**
     * 解析 {@code <#if>} 的 then 块之后的分支链：支持 {@code <#elseif>} 串联。
     *
     * <p>终止形式：
     * <ul>
     *   <li>{@code </#>}：无 else 分支，返回 {@code null}</li>
     *   <li>{@code <#else>...}：允许带分支内容（标签本身不带参数）</li>
     *   <li>{@code <#elseif cond>...}：递归串联为嵌套 {@link IfNode}</li>
     * </ul>
     */
    private List<Node> consumeIfElse(int line) {
        Token term = toks.get(p);
        if (term.type() == Token.Type.END) {
            p++;
            return null;
        }
        if (term.type() == Token.Type.ELSE) {
            p++;
            List<Node> eb = parseBlock();
            Token end = toks.get(p);
            if (end.type() != Token.Type.END)
                throw TemplateUtils.err(end.line(), end.col(), source, "<#if 缺少对应的结束标签");
            p++;
            return eb;
        }
        if (term.type() == Token.Type.ELSEIF) {
            p++;
            Object cond = Lson.parse(term.text(), term.line());
            List<Node> thenB = parseBlock();
            List<Node> elseB = consumeIfElse(line);
            IfNode nested = new IfNode(cond, thenB, elseB);
            return List.of(nested);
        }
        throw TemplateUtils.err(term.line(), term.col(), source, "<#if 缺少对应的结束标签");
    }

    /**
     * 解析 {@code <#continue>} / {@code <#break>} 的参数。
     * 格式：{@code [depth][:condition]}，depth 为直接数字，condition 为 Lson 表达式。
     * 示例：{@code <#break 2:cond>}、{@code <#break cond>}、{@code <#break 2>}、{@code <#break>}
     */
    private Node parseContinueBreak(Token t, boolean isContinue) {
        String text = t.text().trim();
        int depth = 1;
        Object condLson = null;

        int colon = text.indexOf(':');
        if (colon >= 0) {
            // [depth]:condition 格式
            String depthStr = text.substring(0, colon).trim();
            if (!depthStr.isEmpty()) {
                depth = Integer.parseInt(depthStr);
            }
            String condStr = text.substring(colon + 1).trim();
            if (!condStr.isEmpty()) {
                condLson = Lson.parse(condStr, t.line());
            }
        } else if (!text.isEmpty()) {
            // 纯数字 → depth；否则 → condition
            try {
                depth = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                condLson = Lson.parse(text, t.line());
            }
        }
        // depth 至少为 1
        if (depth < 1) depth = 1;

        if (isContinue) {
            return new ContinueNode(depth, condLson);
        } else {
            return new BreakNode(depth, condLson);
        }
    }

    /**
     * 按顶层逗号分割字符串（跳过引号、括号、方括号、花括号内的逗号）。
     * 用于宏定义的参数列表解析。
     */
    private static List<String> splitByComma(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (inString) {
                if (c == '"') inString = false;
                else if (c == '\\') k++;
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '(' -> depth++;
                case ')' -> depth--;
                case '[' -> depth++;
                case ']' -> depth--;
                case '{' -> depth++;
                case '}' -> depth--;
            }
            if (c == ',' && depth == 0) {
                String part = s.substring(start, k).trim();
                if (!part.isEmpty()) parts.add(part);
                start = k + 1;
            }
        }
        if (start < s.length()) {
            String part = s.substring(start).trim();
            if (!part.isEmpty()) parts.add(part);
        }
        return parts;
    }

}
