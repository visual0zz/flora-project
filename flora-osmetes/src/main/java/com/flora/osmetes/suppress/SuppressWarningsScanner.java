package com.flora.osmetes.suppress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析 Java 源码中的 {@code @SuppressWarnings("osmetes:<检查名>")} 注解，
 * 计算每一行被抑制的检查名称。
 * <p>
 * 检查名由检查项自身的 {@code name()} 定义。注解的抑制范围：
 * <ul>
 *   <li>类 / 接口 / 枚举 / 记录级：整个类型体；</li>
 *   <li>方法级：整个方法体；</li>
 *   <li>字段 / 局部变量（语句）级：该声明直到分号；</li>
 *   <li>括号内（方法参数、for 变量等）注解：仅注解所在行。</li>
 * </ul>
 * 字符串、字符、注释与文本块中的内容不参与解析。
 */
public final class SuppressWarningsScanner {

    private static final String PREFIX = "osmetes:";
    private static final String SUPPRESS_WARNINGS = "SuppressWarnings";

    private final Map<Integer, Set<String>> suppressedByLine;

    private SuppressWarningsScanner(Map<Integer, Set<String>> suppressedByLine) {
        this.suppressedByLine = suppressedByLine;
    }

    /**
     * 解析 Java 源文件。
     *
     * @throws IOException 文件读取失败
     */
    public static SuppressWarningsScanner parse(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * 从源码文本解析。
     */
    public static SuppressWarningsScanner parse(String text) {
        List<Tok> tokens = tokenize(text);
        int[] parenDepthAt = new int[tokens.size()];
        int paren = 0;
        for (int i = 0; i < tokens.size(); i++) {
            parenDepthAt[i] = paren;
            String s = tokens.get(i).text;
            if (s.equals("(")) {
                paren++;
            } else if (s.equals(")")) {
                paren--;
            }
        }

        Map<Integer, Set<String>> suppressed = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!isSuppressWarningsAt(tokens, i)) {
                continue;
            }
            List<String> names = extractNames(tokens, i);
            if (names.isEmpty()) {
                continue;
            }
            int startLine = tokens.get(i).line;
            int endLine = parenDepthAt[i] > 0 ? startLine : resolveEndLine(tokens, i);
            for (int line = startLine; line <= endLine; line++) {
                suppressed.computeIfAbsent(line, k -> new HashSet<>()).addAll(names);
            }
        }
        return new SuppressWarningsScanner(suppressed);
    }

    /**
     * 判断某行上的某检查是否被抑制。
     *
     * @param line      从 1 开始的行号
     * @param checkName 检查项名称（对应检查类的 {@code name()}）
     * @return {@code true} 表示该行该检查被抑制
     */
    public boolean isSuppressed(int line, String checkName) {
        Set<String> names = suppressedByLine.get(line);
        return names != null && names.contains(checkName);
    }

    /** 判定 {@code tokens[i]} 是否为 {@code @SuppressWarnings} 的 {@code @} 记号。 */
    private static boolean isSuppressWarningsAt(List<Tok> tokens, int i) {
        if (!tokens.get(i).text.equals("@")) {
            return false;
        }
        // 注解名可能是全限定名（如 java.lang.SuppressWarnings）。tokenize 把 '.' 吞掉、
        // 不产生 token，故限定名被切成多个标识符 token；取 '@' 与 '(' 之间的最后一个
        // 标识符片段判断是否等于 SuppressWarnings，既能识别简单形式也能识别全限定形式。
        String lastName = null;
        for (int j = i + 1; j < tokens.size(); j++) {
            String t = tokens.get(j).text;
            if (t.equals("(")) {
                break;
            }
            if (!t.isEmpty() && Character.isJavaIdentifierStart(t.charAt(0))) {
                lastName = t;
            } else {
                break;
            }
        }
        return SUPPRESS_WARNINGS.equals(lastName);
    }

    /** 从注解参数中提取所有以 {@code osmetes:} 开头的字符串值，返回对应检查名。 */
    private static List<String> extractNames(List<Tok> tokens, int atIndex) {
        List<String> names = new ArrayList<>();
        int paren = 0;
        for (int i = atIndex + 2; i < tokens.size(); i++) {
            String s = tokens.get(i).text;
            if (s.equals("(")) {
                paren++;
            } else if (s.equals(")")) {
                if (--paren == 0) {
                    break;
                }
            } else if (s.startsWith("\"")) {
                String value = s.substring(1);
                if (value.startsWith(PREFIX)) {
                    names.add(value.substring(PREFIX.length()));
                }
            }
        }
        return names;
    }

    /**
     * 解析注解作用域的结束行。
     * <p>
     * 先跳过本注解自身的参数列表与后续的其他注解，再从声明中找第一个
     * {@code ;} 或 {@code {}：前者表示字段/语句，后者表示类或方法体，
     * 结束行取与该左花括号配对的右花括号所在行。
     */
    private static int resolveEndLine(List<Tok> tokens, int atIndex) {
        int i = atIndex + 1;
        if (i < tokens.size()) {
            i++; // 注解名
        }
        if (i < tokens.size() && tokens.get(i).text.equals("(")) {
            i = skipBalancedParen(tokens, i);
        }
        while (i < tokens.size()) {
            String s = tokens.get(i).text;
            if (s.equals("@")) {
                i = skipAnnotation(tokens, i);
                continue;
            }
            if (s.equals(";")) {
                return tokens.get(i).line;
            }
            if (s.equals("{")) {
                return matchingBraceEndLine(tokens, i);
            }
            i++;
        }
        return tokens.get(tokens.size() - 1).line;
    }

    /** 跳过 {@code @名称(参数...)} 整个注解，返回其后第一个记号下标。 */
    private static int skipAnnotation(List<Tok> tokens, int atIndex) {
        int i = atIndex + 1; // '@'
        if (i < tokens.size()) {
            i++; // 注解名
        }
        if (i < tokens.size() && tokens.get(i).text.equals("(")) {
            return skipBalancedParen(tokens, i);
        }
        return i;
    }

    /** 跳过配对的括号组（{@code openIndex} 指向左括号），返回其后第一个记号下标。 */
    private static int skipBalancedParen(List<Tok> tokens, int openIndex) {
        int paren = 0;
        for (int i = openIndex; i < tokens.size(); i++) {
            String s = tokens.get(i).text;
            if (s.equals("(")) {
                paren++;
            } else if (s.equals(")")) {
                if (--paren == 0) {
                    return i + 1;
                }
            }
        }
        return tokens.size();
    }

    /** 从 {@code openIndex} 的左花括号开始配对，返回配对右花括号所在行。 */
    private static int matchingBraceEndLine(List<Tok> tokens, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < tokens.size(); i++) {
            String s = tokens.get(i).text;
            if (s.equals("{")) {
                depth++;
            } else if (s.equals("}")) {
                depth--;
                if (depth == 0) {
                    return tokens.get(i).line;
                }
            }
        }
        return tokens.get(tokens.size() - 1).line;
    }

    /** 词法记号：文本与所在行号。字符串记号以 {@code "} 开头，内容紧随其后。 */
    private record Tok(String text, int line) {
    }

    /** 对源码做轻量词法切分：跳过空白、注释、字符/字符串/文本块，产出关键符号与标识符。 */
    private static List<Tok> tokenize(String text) {
        List<Tok> tokens = new ArrayList<>();
        int n = text.length();
        int i = 0;
        int[] lineRef = {1};
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineRef[0]++;
                i++;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                i += 2;
                while (i < n && !(text.charAt(i) == '*' && i + 1 < n && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') {
                        lineRef[0]++;
                    }
                    i++;
                }
                i = Math.min(i + 2, n);
                continue;
            }
            if (c == '"') {
                i = consumeString(text, i, lineRef, tokens);
                continue;
            }
            if (c == '\'') {
                i = consumeChar(text, i, lineRef);
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(text.charAt(i))) {
                    i++;
                }
                tokens.add(new Tok(text.substring(start, i), lineRef[0]));
                continue;
            }
            switch (c) {
                case '{':
                case '}':
                case '(':
                case ')':
                case ';':
                case '@':
                    tokens.add(new Tok(String.valueOf(c), lineRef[0]));
                    break;
                default:
                    break;
            }
            i++;
        }
        return tokens;
    }

    /** 消费一个字符串或文本块字面量，返回其后的下标；普通字符串作为记号输出。 */
    private static int consumeString(String text, int i, int[] lineRef, List<Tok> tokens) {
        if (i + 2 < text.length() && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
            return consumeTextBlock(text, i + 3, lineRef);
        }
        StringBuilder sb = new StringBuilder("\"");
        i++;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                sb.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                i++;
                break;
            }
            if (c == '\n') {
                lineRef[0]++;
            }
            sb.append(c);
            i++;
        }
        tokens.add(new Tok(sb.toString(), lineRef[0]));
        return i;
    }

    /** 消费文本块 {@code """..."""}，不产出记号。 */
    private static int consumeTextBlock(String text, int i, int[] lineRef) {
        while (i < text.length()) {
            if (text.charAt(i) == '"' && i + 2 < text.length()
                    && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
                return i + 3;
            }
            if (text.charAt(i) == '\n') {
                lineRef[0]++;
            }
            i++;
        }
        return i;
    }

    /** 消费字符字面量，不产出记号。 */
    private static int consumeChar(String text, int i, int[] lineRef) {
        i++;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i += 2;
                continue;
            }
            if (c == '\'') {
                return i + 1;
            }
            if (c == '\n') {
                lineRef[0]++;
            }
            i++;
        }
        return i;
    }
}
