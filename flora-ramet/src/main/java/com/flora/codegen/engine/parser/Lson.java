package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.runtime.FunctionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 宽松对象符号（Lson）解析器 —— 全局唯一的模板表达式解析器。
 *
 * <p>两个公共入口实际使用相同的解析核心，均支持全部语法特性：
 * <ul>
 *   <li>字面量：字符串、数字、对象 {@code {}}、数组 {@code []}、布尔、null</li>
 *   <li>引用、属性链 {@code a.b.c}、索引 {@code a[0]}</li>
 *   <li>函数调用 {@code name(args)}</li>
 *   <li>圆括号分组 {@code (expr)}</li>
 *   <li>中缀函数：{@code a equals b} → {@code equals(a,b)}</li>
 *   <li>前缀函数：{@code not a} → {@code not(a)}</li>
 * </ul>
 */
public final class Lson {

    /**
     * 无引号的标识符引用。
     */
    public record Reference(String name) {
        public Reference {
            Objects.requireNonNull(name);
        }
        @Override public String toString() { return name; }
    }

    /**
     * 函数调用：{@code name(arg1, arg2, ...)}。
     */
    public record FunctionCall(String name, List<Object> args) {
        public FunctionCall {
            Objects.requireNonNull(name);
            Objects.requireNonNull(args);
        }
        @Override public String toString() {
            return name + args;
        }
    }

    /**
     * 属性链访问：{@code target.property}。
     */
    public record PropertyAccess(Object target, String property) {
        public PropertyAccess {
            Objects.requireNonNull(target);
            Objects.requireNonNull(property);
        }
        @Override public String toString() {
            return target + "." + property;
        }
    }

    /**
     * 索引访问：{@code target[index]}。
     */
    public record IndexAccess(Object target, Object index) {
        public IndexAccess {
            Objects.requireNonNull(target);
            Objects.requireNonNull(index);
        }
        @Override public String toString() {
            return target + "[" + index + "]";
        }
    }

    /**
     * Lson 关键字枚举。这些名称不能用作 object key。
     */
    public enum Keyword {
        TRUE("true"), FALSE("false"), NULL("null");

        public final String literal;
        Keyword(String literal) { this.literal = literal; }

        public static Keyword from(String word) {
            return switch (word) {
                case "true" -> TRUE;
                case "false" -> FALSE;
                case "null" -> NULL;
                default -> null;
            };
        }
    }

    private final String s;
    private int i;
    private final int line;
    private final String source;

    private Lson(String s, int line, String source) {
        this.s = s;
        this.line = line;
        this.source = source;
    }

    // ========== 公共入口（共享同一套解析核心） ==========

    /** {@link #parse(String, int, String) parse} 的便捷重载。 */
    public static Object parse(String src) {
        return parse(src, -1, null);
    }

    /** {@link #parse(String, int, String) parse} 的便捷重载。 */
    public static Object parse(String src, int line) {
        return parse(src, line, null);
    }

    /**
     * 解析 Lson 表达式/值，用于元数据值、{@code ${}} 插值、赋值右值、
     * {@code <#if>} / {@code <#for>} 指令表达式等所有场景。
     * 支持函数调用、中缀/前缀函数简写、圆括号分组等全部语法。
     */
    public static Object parse(String src, int line, String source) {
        Lson p = new Lson(src, line, source);
        p.skipWs();
        if (p.i >= p.s.length()) throw p.err("空输入");
        Object v = p.parseDirExpr();
        p.skipWs();
        if (p.i != p.s.length()) throw p.err("解析后存在多余字符");
        return v;
    }

    // ========== 共享解析核心（值解析 + 中缀运算符链） ==========

    private Map<String, Object> parseObject() {
        expect('{');
        skipWs();
        Map<String, Object> m = new LinkedHashMap<>();
        if (peek() == '}') { i++; return m; }
        while (true) {
            skipWs();
            String key = parseKey();
            skipWs();
            expect(':');
            skipWs();
            if (m.containsKey(key)) {
                throw err("重复的 key: " + key);
            }
            m.put(key, parseDirExpr());
            skipWs();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw err("期望 ',' 或 '}'");
        }
        return m;
    }

    private String parseKey() {
        if (Character.isJavaIdentifierStart(peek())) {
            int start = i;
            while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
            String key = s.substring(start, i);
            // 只保留字面量关键字（true/false/null）的保留，
            // 不阻止内置函数名作为键名。在 { key: value } 上下文中，
            // ':' 足以消除键与表达式之间的歧义，无需额外保留字。
            if (Keyword.from(key) != null) {
                throw err("key 不能是 Lson 关键字: " + key);
            }
            return key;
        }
        throw err("期望 key（标识符，不能带引号）");
    }

    private List<Object> parseArray() {
        expect('[');
        skipWs();
        List<Object> list = new ArrayList<>();
        if (peek() == ']') { i++; return list; }
        while (true) {
            skipWs();
            list.add(parseDirExpr());
            skipWs();
            char c = next();
            if (c == ']') break;
            if (c != ',') throw err("期望 ',' 或 ']'");
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') break;
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(parseUnicodeEscape());
                    default -> throw err("非法转义 \\" + e);
                }
            } else {
                if (c < 0x20) throw err("字符串中包含未转义的控制字符");
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 解析 Java 风格的 \\uXXXX Unicode 转义。调用时已跳过 '\\u'。 */
    private char parseUnicodeEscape() {
        int val = 0;
        for (int j = 0; j < 4; j++) {
            char h = next();
            int d = Character.digit(h, 16);
            if (d < 0) throw err("非法 Unicode 转义 \\u" + h);
            val = (val << 4) | d;
        }
        return (char) val;
    }

    // ========== 函数调用解析 ==========

    /** 解析函数调用：name(arg1, arg2, ...) */
    private FunctionCall parseFunctionCall(String name) {
        expect('(');
        skipWs();
        List<Object> args = new ArrayList<>();
        if (i < s.length() && s.charAt(i) != ')') {
            args.add(parseDirExpr());
            skipWs();
            while (i < s.length() && s.charAt(i) == ',') {
                i++;
                skipWs();
                args.add(parseDirExpr());
                skipWs();
            }
        }
        expect(')');
        return new FunctionCall(name, args);
    }

    /** 读取标识符属性名，用于 {@code a.b} 属性链中的 {@code b}。 */
    private String readIdentWord() {
        if (i >= s.length() || !Character.isJavaIdentifierStart(s.charAt(i))) {
            throw err("期望属性名（标识符）");
        }
        int start = i;
        while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
        return s.substring(start, i);
    }

    // ========== 共享解析核心（值解析 + 中缀运算符链） ==========

    private Object parseDirExpr() {
        skipWs();
        if (i >= s.length()) throw err("期望表达式");
        if (s.charAt(i) == '(') {
            i++;
            Object v = parseDirExpr();
            skipWs();
            if (i < s.length() && s.charAt(i) == ')') i++;
            return parseDirInfix(v);
        }
        Object left = parseDirAtom();
        return parseDirInfix(left);
    }

    private Object parseDirExprOperand() {
        skipWs();
        if (i < s.length() && s.charAt(i) == '(') {
            i++;
            Object v = parseDirExpr();
            skipWs();
            if (i < s.length() && s.charAt(i) == ')') i++;
            return v;
        }
        return parseDirAtom();
    }

    private Object parseDirAtom() {
        skipWs();
        if (i >= s.length()) throw err("期望值");
        char c = s.charAt(i);

        // 字面量
        if (c == '"') return parseString();
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '-' || c == '.' || Character.isDigit(c)) return parseNumber();

        // 标识符
        if (Character.isJavaIdentifierStart(c)) {
            int start = i;
            while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
            String word = s.substring(start, i);

            // 关键字
            Keyword kw = Keyword.from(word);
            if (kw != null) {
                return switch (kw) {
                    case TRUE -> Boolean.TRUE;
                    case FALSE -> Boolean.FALSE;
                    case NULL -> null;
                };
            }

            // 函数调用：name(args) — 优先于前缀函数
            if (i < s.length() && s.charAt(i) == '(') {
                return parseFunctionCall(word);
            }

            // 前缀函数（arity=1）：not a → not(a)
            Integer arityKw = FunctionRegistry.BUILTINS.get(word);
            if (arityKw != null && arityKw == 1) {
                Object operand = parseDirExprOperand();
                return new FunctionCall(word, List.of(operand));
            }

            // 引用 + 属性链 + 索引
            Object result = new Reference(word);
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == '.') {
                    i++;
                    result = new PropertyAccess(result, readIdentWord());
                } else if (ch == '[') {
                    i++;
                    skipWs();
                    Object index = parseDirExpr();
                    skipWs();
                    expect(']');
                    result = new IndexAccess(result, index);
                } else {
                    break;
                }
            }
            return result;
        }

        throw err("期望值");
    }

    private Object parseDirInfix(Object left) {
        skipWs();
        if (i >= s.length()) return left;

        // 已知二元函数（arity=2 或 -1）作为中缀：a greaterThan b → greaterThan(a,b)
        if (Character.isJavaIdentifierStart(s.charAt(i))) {
            int start = i;
            while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) i++;
            String word = s.substring(start, i);

            Integer arity = FunctionRegistry.BUILTINS.get(word);
            if (arity != null && (arity == 2 || arity == -1)) {
                skipWs();
                Object right = parseDirExprOperand();
                return parseDirInfix(new FunctionCall(word, List.of(left, right)));
            }

            throw err("未知的运算符: " + word);
        }

        return left;
    }

    // ========== 字面量解析 ==========

    private Object parseNumber() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ("0123456789-.eEx".indexOf(c) < 0) break;
            // NOTE: 刻意不支持 .. 范围运算符，请使用 range() 函数。此处不设检测，
            // 因为字符集包含 '.'，.. 会被正常吞入数字字符串并最终在 Double.parseDouble 处报错。
            i++;
        }
        String num = s.substring(start, i);
        if (num.isEmpty() || num.equals("-") || num.equals(".")) throw err("期望数字");
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
            return LsonNumber.of(Double.parseDouble(num));
        }
        try {
            return LsonNumber.of(Long.parseLong(num));
        } catch (NumberFormatException ex) {
            return LsonNumber.of(Double.parseDouble(num));
        }
    }

    // ========== 工具方法 ==========

    private void expect(char c) {
        if (peek() != c) throw err("期望 '" + c + "'");
        i++;
    }

    private char peek() { return s.charAt(i); }
    private char next() { return s.charAt(i++); }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private CodeGenException err(String msg) {
        String loc;
        if (source != null) {
            loc = source + (line >= 0 ? " 第 " + line + " 行" : "");
        } else if (line >= 0) {
            loc = "第 " + line + " 行";
        } else {
            loc = "";
        }
        String prefix = loc.isEmpty() ? "Lson 解析错误" : loc + ": Lson 解析错误";
        return new CodeGenException(prefix + " @" + i + ": " + msg);
    }
}
