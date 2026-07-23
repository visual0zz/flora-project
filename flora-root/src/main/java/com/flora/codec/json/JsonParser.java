package com.flora.codec.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * JSON 解析器，将 JSON 格式的字符串解析为 Java 对象。
 * <p>支持解析 JSON Object（返回 {@code Map<String, Object>}）、
 * JSON Array（返回 {@code List<Object>}）以及基本类型值。
 * 数字根据精度返回 {@code Long}、{@code BigDecimal} 或 {@code BigInteger}。</p>
 */
public final class JsonParser {
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private final String s;
    private int i;

    private JsonParser(String s) {
        this.s = s;
    }

    /**
     * 解析 JSON 字符串为 Java 对象。
     * <p>自动识别 JSON Object、Array、字符串、数字、布尔值和 null。</p>
     *
     * @param src JSON 字符串
     * @return 解析后的 Java 对象
     * @throws IllegalStateException 如果输入格式不合法或解析后存在多余字符
     */
    public static Object parse(String src) {
        JsonParser j = new JsonParser(src);
        
        if (j.i < j.s.length() && j.s.charAt(0) == '\uFEFF') j.i++;
        j.skipWs();
        if (j.i >= j.s.length()) throw j.err("空白输入或仅含 BOM");
        Object v = j.parseValue();
        j.skipWs();
        if (j.i != j.s.length()) throw j.err("解析后存在多余字符");
        return v;
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Object。
     *
     * @param src JSON 字符串
     * @return 解析后的 Map
     * @throws IllegalStateException 如果顶层不是 Object 或格式不合法
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String src) {
        Object v = parse(src);
        if (!(v instanceof Map)) throw new IllegalStateException("顶层不是 JSON Object");
        return (Map<String, Object>) v;
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Array。
     *
     * @param src JSON 字符串
     * @return 解析后的 List
     * @throws IllegalStateException 如果顶层不是 Array 或格式不合法
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String src) {
        Object v = parse(src);
        if (!(v instanceof List)) throw new IllegalStateException("顶层不是 JSON Array");
        return (List<Object>) v;
    }

    

    /**
     * 解析下一个 JSON 值，根据首字符分发到具体解析方法。
     *
     * @return 解析出的 Java 对象
     */
    private Object parseValue() {
        skipWs();
        if (i >= s.length()) throw err("期望 JSON 值");
        char c = s.charAt(i);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': case 'f': return parseBool();
            case 'n': return parseNull();
            default:  return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        skipWs();
        Map<String, Object> m = new LinkedHashMap<>();
        if (peek() == '}') { i++; return m; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expect(':');
            skipWs();
            m.put(key, parseValue());
            skipWs();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw err("期望 ',' 或 '}'");
        }
        return m;
    }

    private List<Object> parseArray() {
        expect('[');
        skipWs();
        List<Object> list = new ArrayList<>();
        if (peek() == ']') { i++; return list; }
        while (true) {
            skipWs();
            list.add(parseValue());
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
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': {
                        if (i + 4 > s.length()) throw err("\\uXXXX 缺少 4 位十六进制数");
                        String hex = s.substring(i, i + 4);
                        char ch = (char) Integer.parseInt(hex, 16);
                        i += 4;
                        if (ch >= 0xD800 && ch <= 0xDBFF) {
                            
                            if (i + 1 >= s.length() || s.charAt(i) != '\\' || s.charAt(i + 1) != 'u') {
                                throw err("不完整的代理对: \\u" + hex);
                            }
                            i += 2;
                            if (i + 4 > s.length()) throw err("低代理 \\uXXXX 缺少 4 位十六进制数");
                            String hex2 = s.substring(i, i + 4);
                            char low = (char) Integer.parseInt(hex2, 16);
                            if (low < 0xDC00 || low > 0xDFFF) {
                                throw err("代理对中缺少低代理: \\u" + hex2);
                            }
                            i += 4;
                            sb.append(ch).append(low);
                        } else if (ch >= 0xDC00 && ch <= 0xDFFF) {
                            throw err("孤立的低代理: \\u" + hex);
                        } else {
                            sb.append(ch);
                        }
                        break;
                    }
                    default: throw err("非法转义 \\" + e);
                }
            } else {
                
                if (c < 0x20) throw err("字符串中包含未转义的控制字符 U+" + Integer.toHexString(c));
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object parseNumber() {
        int start = i;
        while (i < s.length() && "0123456789-.eE".indexOf(s.charAt(i)) >= 0) i++;
        String num = s.substring(start, i);
        if (num.isEmpty() || num.equals("-") || num.equals(".")) throw err("期望数字");
        
        if (!JSON_NUMBER.matcher(num).matches()) {
            throw err("非法数字格式: " + num);
        }
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
            
            return new BigDecimal(num);
        }
        try {
            return Long.parseLong(num);
        } catch (NumberFormatException ex) {
            
            return new BigInteger(num);
        }
    }

    private Boolean parseBool() {
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        throw err("期望 true / false");
    }

    private Object parseNull() {
        if (s.startsWith("null", i)) { i += 4; return null; }
        throw err("期望 null");
    }

    private void expect(char c) {
        if (peek() != c) throw err("期望 '" + c + "'");
        i++;
    }

    private char peek() { return s.charAt(i); }
    private char next() { return s.charAt(i++); }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalStateException err(String msg) {
        return new IllegalStateException("JSON 解析错误 @" + i + ": " + msg);
    }
}
