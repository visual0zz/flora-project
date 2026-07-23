package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Ramet 模板引擎的词法分析器——将模板源码字符串扫描为 {@link Token} 列表。
 *
 * <p>词法分析流程：
 * <ol>
 *   <li>顺序扫描模板字符，识别以下几种结构：</li>
 *   <ul>
 *     <li>{@code ${...}} 变量插值 → {@link Token.Type#VAR}</li>
 *     <li>{@code <#...>} 指令标签（if、list、macro、include、else）→ 对应 Token 类型</li>
 *     <li>{@code <@...>} 宏调用 → {@link Token.Type#MACRO_CALL}</li>
 *     <li>{@code <#-- -->} 注释 → {@link Token.Type#COMMENT}</li>
 *     <li>{@code </#>} 结束标签 → {@link Token.Type#END}</li>
 *     <li>{@code \r}, {@code \n}, {@code \r\n} 换行符 → 首个设置 {@code pendingNewline} 状态，
 *         后续连续换行符产生独立 {@link Token.Type#NEWLINE} Token</li>
 *     <li>其余文本 → {@link Token.Type#PASSIVE}</li>
 *   </ul>
 *   <li>支持 {@code \$} 转义序列，反斜杠取消变量插值</li>
 *   <li>自动匹配花括号和指令闭合标签 {@code >}</li>
 * </ol>
 *
 * <p>入口方法：{@link #lex()} 返回完整的 {@link Token} 列表。
 *
 * <p>便捷入口：{@link #lex(String)} 直接从源码字符串执行词法分析。
 */
public class Lexer {
    String src;
    String source;
    int len;
    int i = 0;
    int line = 1;
    List<Token> toks = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    int bufLine = 1;
    int bufCol = 1;
    boolean pendingNewline = false;

    public Lexer(String src, String source) {
        this.src = src;
        this.source = source;
        this.len = src.length();
    }

    public List<Token> lex() {
        while (i < len) {
            step();
        }
        flush();
        if (pendingNewline) {
            toks.add(new Token(Token.Type.NEWLINE, "\n", line, colAt(src.length()), false));
        }
        suppressDirectiveNewlines();
        return toks;
    }

    /** 便捷入口：从源码字符串直接执行词法分析，不携带源文件信息。 */
    /** 返回位置 pos 所在的行内列号（从 1 开始）。 */
    private int colAt(int pos) {
        int c = 1;
        for (int k = pos - 1; k >= 0; k--) {
            char ch = src.charAt(k);
            if (ch == '\n' || ch == '\r') break;
            c++;
        }
        return c;
    }

    public static List<Token> lex(String src) {
        return new Lexer(src, null).lex();
    }

    private void flush() {
        if (!buf.isEmpty()) {
            toks.add(new Token(Token.Type.PASSIVE, buf.toString(), bufLine, bufCol, pendingNewline));
            pendingNewline = false;
            buf.setLength(0);
        }
    }

    private void step() {
        char c = src.charAt(i);
        if (c == '\r' || c == '\n') {
            // \r\n 视为一个换行符
            if (c == '\r' && i + 1 < len && src.charAt(i + 1) == '\n') {
                i++; // 跳过紧跟的 \n
            }
            flush();
            if (pendingNewline) {
                toks.add(new Token(Token.Type.NEWLINE, "\n", line, colAt(i), false));
                // 保持 pendingNewline = true，后续 token 吸收
            } else {
                pendingNewline = true;
            }
            line++;
            i++;
            return;
        }
        if (c == '$') {
            if (i + 1 < len && src.charAt(i + 1) == '{') {
                flush();
                int col = colAt(i);
                int end = matchBrace(i + 2);
                toks.add(new Token(Token.Type.VAR, src.substring(i + 2, end), line, col, pendingNewline));
                pendingNewline = false;
                i = end + 1;
            } else {
                buf.append('$');
                i++;
            }
            return;
        }
        if (c == '<' && i + 1 < len) {
            char n = src.charAt(i + 1);
            if (n == '#') {
                flush();
                int col = colAt(i);
                i += 2;
                handleDirective(col);
                return;
            }
            if (n == '@') {
                flush();
                int col = colAt(i);
                i += 2;
                handleMacroCall(col);
                return;
            }
            if (n == '/' && i + 2 < len && src.charAt(i + 2) == '#') {
                flush();
                int col = colAt(i);
                i += 3;
                skipEndTag(col);
                return;
            }
        }
        buf.append(c);
        if (buf.length() == 1) {
            // 首次向空缓冲区添加字符时，记录行号列号
            bufLine = line;
            bufCol = colAt(i);
        }
        i++;
    }

    private int matchBrace(int from) {
        int d = 1;
        for (int k = from; k < len; k++) {
            char c = src.charAt(k);
            if (c == '{') d++;
            else if (c == '}' && --d == 0) return k;
        }
        throw TemplateUtils.err(line, colAt(i), source, "未闭合的 ${");
    }

    private void handleDirective(int col) {
        while (i < len && Character.isWhitespace(src.charAt(i))) i++;
        if (i + 1 < len && src.charAt(i) == '-' && src.charAt(i + 1) == '-') {
            i += 2;
            int end = src.indexOf("-->", i);
            if (end < 0) throw TemplateUtils.err(line, colAt(i), source, "未闭合的 <#-- 注释");
            String commentBody = src.substring(i, end);
            toks.add(new Token(Token.Type.COMMENT, commentBody, line, col, pendingNewline));
            pendingNewline = false;
            i = end + 3;
            return;
        }
        int j = i;
        while (j < len && Character.isJavaIdentifierPart(src.charAt(j))) j++;
        String kw = src.substring(i, j);
        if (kw.isEmpty()) throw TemplateUtils.err(line, colAt(i), source, "期望指令名: <#if <#for <#macro <#include <#else <#meta");
        i = j;
        while (i < len && Character.isWhitespace(src.charAt(i))) i++;
        switch (kw) {
            case "else": {
                // <#else> 标签本身不允许携带任何参数/条件；条件分支请改用 <#elseif>
                while (i < len && Character.isWhitespace(src.charAt(i))) i++;
                if (i >= len || src.charAt(i) != '>')
                    throw TemplateUtils.err(line, colAt(i), source, "<#else> 不接受任何参数，条件分支请使用 <#elseif>");
                i++;   // 跳过 >
                toks.add(new Token(Token.Type.ELSE, "", line, col, pendingNewline));
                pendingNewline = false;
                return;
            }
            case "if":
            case "for":
            case "macro":
            case "include":
            case "elseif":
            case "continue":
            case "break": {
                int end = findGt(i);
                String inner = src.substring(i, end).trim();
                Token.Type ty = keywordType(kw);
                toks.add(new Token(ty, inner, line, col, pendingNewline));
                pendingNewline = false;
                i = end + 1;
                return;
            }
            case "meta": {
                int gt = findGt(i);
                String inner = src.substring(i, gt).trim();
                if (!inner.isEmpty()) {
                    throw TemplateUtils.err(line, colAt(i), source, "<#meta> 不接受参数");
                }
                i = gt + 1; // 跳过 >
                int end = src.indexOf("</#meta>", i);
                if (end < 0) throw TemplateUtils.err(line, colAt(i), source, "未闭合的 <#meta>");
                String metaBody = src.substring(i, end);
                toks.add(new Token(Token.Type.META, metaBody, line, col, pendingNewline));
                pendingNewline = false;
                i = end + 8; // 跳过 </#meta>
                return;
            }
            default:
                throw TemplateUtils.err(line, colAt(i), source, "未知指令: #" + kw);
        }
    }

    private void handleMacroCall(int col) {
        while (i < len && Character.isWhitespace(src.charAt(i))) i++;
        int j = i;
        while (j < len && Character.isJavaIdentifierPart(src.charAt(j))) j++;
        String name = src.substring(i, j);
        if (name.isEmpty()) throw TemplateUtils.err(line, colAt(i), source, "期望宏名");
        i = j;
        while (i < len && Character.isWhitespace(src.charAt(i))) i++;
        int end = findMacroEnd(i);
        String argsStr = src.substring(i, end);
        if (end < len && src.charAt(end) == '/') end++; // 跳过自闭合 '/'
        argsStr = argsStr.trim();
        i = end < len ? end + 1 : len;
        toks.add(new Token(Token.Type.MACRO_CALL, name, line, col,
                argsStr.isEmpty() ? List.of() : parseArgExprs(argsStr, line), pendingNewline));
        pendingNewline = false;
    }

    private void skipEndTag(int col) {
        while (i < len && Character.isJavaIdentifierPart(src.charAt(i))) i++;
        if (i < len && src.charAt(i) == '>') i++;
        toks.add(new Token(Token.Type.END, "", line, col, pendingNewline));
        pendingNewline = false;
    }

    /**
     * 从 from 开始找到闭合的 '>' 或 '/'，跳过字符串字面量和括号内的内容。
     * 用于宏调用标签边界检测。
     */
    private int findMacroEnd(int from) {
        for (int k = from; k < len; k++) {
            char c = src.charAt(k);
            if (c == '"') {
                k = skipString(k);
            } else if (c == '(') {
                k = skipBalanced(k, '(', ')');
            } else if (c == '[') {
                k = skipBalanced(k, '[', ']');
            } else if (c == '{') {
                k = skipBalanced(k, '{', '}');
            } else if (c == '>' || c == '/') {
                return k;
            }
        }
        return len;
    }

    /** 跳过双引号字符串字面量（含反斜杠转义），返回闭合 '"' 的索引。 */
    private int skipString(int k) {
        k++; // 跳过开头的 '"'
        while (k < len && src.charAt(k) != '"') {
            if (src.charAt(k) == '\\') k++;
            k++;
        }
        return k; // 此时 k 指向闭合的 '"'
    }

    /** 从 open 字符位置开始跳过平衡的括号对，返回闭合字符的索引。 */
    private int skipBalanced(int k, char open, char close) {
        int depth = 1;
        k++; // 跳过 open 字符
        while (k < len && depth > 0) {
            char c = src.charAt(k);
            if (c == '"') {
                k = skipString(k);
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
            }
            if (depth > 0) k++;
        }
        return k;
    }

    /** 从当前位置 i 开始，找到闭合的 '>'，跳过字符串字面量和括号内的内容。 */
    private int findGt(int from) {
        int parenDepth = 0;
        for (int k = from; k < len; k++) {
            char c = src.charAt(k);
            if (c == '"') {
                // 跳过字符串字面量
                k++;
                while (k < len && src.charAt(k) != '"') {
                    if (src.charAt(k) == '\\') k++;
                    k++;
                }
            } else if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
            } else if (c == '>' && parenDepth == 0) {
                // '=' 或 '-' 在前 → 是 >= 或 -> 运算符，不是闭合符
                if (k > from && (src.charAt(k - 1) == '=' || src.charAt(k - 1) == '-')) continue;
                // '>' 在后 → 是 >> 右移运算符（虽然引擎暂不支持，但安全处理）
                if (k + 1 < len && src.charAt(k + 1) == '>') continue;
                return k;
            }
        }
        throw TemplateUtils.err(line, colAt(i), source, "缺少 '>'");
    }

    private Token.Type keywordType(String kw) {
        return switch (kw) {
            case "if" -> Token.Type.IF;
            case "for" -> Token.Type.FOR;
            case "include" -> Token.Type.INCLUDE;
            case "macro" -> Token.Type.MACRO;
            case "elseif" -> Token.Type.ELSEIF;
            case "continue" -> Token.Type.CONTINUE;
            case "break" -> Token.Type.BREAK;
            default -> throw TemplateUtils.err(line, colAt(i), source, "内部错误: " + kw);
        };
    }

    /**
     * 将宏调用的参数字符串按顶层逗号分割为独立的 Lson 表达式并逐一解析。
     *
     * <p>与简单的 {@code split(",")} 不同，该方法会跟踪字符串、括号、方括号和花括号的嵌套状态，
     * 以确保包含逗号的参数（如字符串字面量 {@code "hel,lo"}、函数调用 {@code f(a,b)}）不被错误分割。
     */
    private List<Object> parseArgExprs(String argsStr, int line) {
        List<Object> out = new ArrayList<>();
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        int start = 0;
        for (int k = 0; k < argsStr.length(); k++) {
            char c = argsStr.charAt(k);
            if (inString) {
                if (c == '"') inString = false;
                else if (c == '\\') k++; // 跳过转义后的字符
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '(' -> parenDepth++;
                case ')' -> parenDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
            }
            if (c == ',' && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                String part = argsStr.substring(start, k).trim();
                if (!part.isEmpty()) {
                    out.add(Lson.parse(part, line));
                }
                start = k + 1;
            }
        }
        if (argsStr.length() > start) {
            String part = argsStr.substring(start).trim();
            if (!part.isEmpty()) {
                out.add(Lson.parse(part, line));
            }
        }
        return out;
    }

    /**
     * 后处理：对每个前导换行的指令 token，抑制其后第一个 NEWLINE token 的输出。
     * 避免模板中出现连续两个换行（指令吸收前导换行后，指令后的正文换行又独立输出）。
     */
    private void suppressDirectiveNewlines() {
        boolean pending = false;
        for (Token t : toks) {
            if (pending && t.type() == Token.Type.NEWLINE && !t.suppressed()) {
                t.suppress();
                pending = false;
            } else if (t.suppressed()) {
                // 已被抑制的 token 不改变状态
            } else if (t.type() != Token.Type.PASSIVE && t.type() != Token.Type.VAR
                    && t.type() != Token.Type.NEWLINE) {
                // 指令 token：若带前导换行，则准备抑制后续 NEWLINE
                if (t.leadingNewline()) {
                    pending = true;
                }
            } else {
                pending = false;
            }
        }
    }
}
