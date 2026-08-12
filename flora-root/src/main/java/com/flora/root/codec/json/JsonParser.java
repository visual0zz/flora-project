package com.flora.root.codec.json;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonBool;
import com.flora.root.codec.json.model.JsonNull;
import com.flora.root.codec.json.model.JsonNumber;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonString;
import com.flora.root.codec.json.model.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;


/**
 * JSON 解析器，将 JSON 格式的字符串解析为 {@link JsonValue} 模型。
 * <p>顶层与非叶子对象产出 {@link JsonObject}，数组产出 {@link JsonArray}，标量为
 * {@link JsonString} / {@link JsonNumber} / {@link JsonBool} / {@link JsonNull}。
 * 数字根据精度返回包装 {@code Long}、{@code BigDecimal} 或 {@code BigInteger} 的 {@link JsonNumber}。</p>
 */
public final class JsonParser {
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /**
     * 嵌套深度硬上限（栈溢出防护）。
     * 解析为递归实现，每嵌套一层约消耗 2 个调用帧；触发本检查时调用栈深度约为
     * {@code 2 * MAX_DEPTH + 1} 帧。取值须足够小，保证该深度远早于 JVM 线程栈耗尽前就抛出
     * {@link IllegalStateException}，而非 {@code StackOverflowError}。
     * 取 512：仍远超任何真实 JSON 的嵌套深度（实际文档极少超过十余层），同时把调用栈限制在约
     * 1025 帧——约为此前 2001 帧溢出点的一半，留出充分余量；也足以容纳合法的深嵌套（如 501 层数组）。
     */
    private static final int MAX_DEPTH = 512;

    /**
     * 重复键检测开关（默认关闭，与 Jackson/Fastjson 一致：重复键后者覆盖前者）。
     * 开启后遇到重复键抛出 {@link IllegalStateException}，对应 Jackson 的
     * {@code STRICT_DUPLICATE_DETECTION}。
     */
    private static volatile boolean STRICT_DUPLICATE_KEYS = false;

    private final String s;
    private int i;

    private JsonParser(String s) {
        this.s = s;
    }

    /**
     * 设置是否对重复对象键启用严格检测。
     * <p>默认关闭：重复键后者覆盖前者（RFC 8259 仅建议键唯一，未强制）。
     * 开启后，解析遇到重复键立即抛出 {@link IllegalStateException}。</p>
     *
     * @param strict 是否严格检测重复键
     */
    public static void setStrictDuplicateKeys(boolean strict) {
        STRICT_DUPLICATE_KEYS = strict;
    }

    /**
     * 当前是否启用重复键严格检测。
     *
     * @return 是否严格检测
     */
    public static boolean isStrictDuplicateKeys() {
        return STRICT_DUPLICATE_KEYS;
    }

    /**
     * 解析 JSON 字符串为 {@link JsonValue} 模型。
     * <p>自动识别 JSON Object、Array、字符串、数字、布尔值和 null。</p>
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonValue
     * @throws IllegalStateException 如果输入格式不合法或解析后存在多余字符
     */
    public static JsonValue parse(String src) {
        JsonParser j = new JsonParser(src);

        if (j.i < j.s.length() && j.s.charAt(0) == '\uFEFF') j.i++;
        j.skipWs();
        if (j.i >= j.s.length()) throw j.err("空白输入或仅含 BOM");
        JsonValue v = j.parseValue(0);
        j.skipWs();
        if (j.i != j.s.length()) throw j.err("解析后存在多余字符");
        return v;
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Object。
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonObject
     * @throws IllegalStateException 如果顶层不是 Object 或格式不合法
     */
    public static JsonObject parseObject(String src) {
        JsonValue v = parse(src);
        if (!v.isObject()) throw new IllegalStateException("顶层不是 JSON Object");
        return v.asObject();
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Array。
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonArray
     * @throws IllegalStateException 如果顶层不是 Array 或格式不合法
     */
    public static JsonArray parseArray(String src) {
        JsonValue v = parse(src);
        if (!v.isArray()) throw new IllegalStateException("顶层不是 JSON Array");
        return v.asArray();
    }



    /**
     * 解析下一个 JSON 值，根据首字符分发到具体解析方法。
     *
     * @return 解析出的 JsonValue
     */
    private JsonValue parseValue(int depth) {
        // depth 为当前嵌套层级；超过上限即判定为过深 JSON，抛出 IllegalStateException（栈溢出防护）
        if (depth > MAX_DEPTH) throw err("JSON 嵌套层级过深 (超过 " + MAX_DEPTH + " 层)");
        skipWs();
        if (i >= s.length()) throw err("期望 JSON 值");
        char c = s.charAt(i);
        switch (c) {
            case '{': return parseObject(depth);
            case '[': return parseArray(depth);
            case '"': return new JsonString(parseString());
            case 't': case 'f': return new JsonBool(parseBool());
            case 'n': return parseNull();
            default:  return parseNumber();
        }
    }

    private JsonObject parseObject(int depth) {
        expect('{');
        skipWs();
        JsonObject obj = new JsonObject();
        if (peek() == '}') { i++; return obj; }
        while (true) {
            skipWs();
            String key = parseString();
            skipWs();
            expect(':');
            skipWs();
            if (STRICT_DUPLICATE_KEYS && obj.containsKey(key)) {
                throw err("重复的对象键: \"" + key + "\"");
            }
            obj.put(key, parseValue(depth + 1));
            skipWs();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw err("期望 ',' 或 '}'");
        }
        return obj;
    }

    private JsonArray parseArray(int depth) {
        expect('[');
        skipWs();
        JsonArray list = new JsonArray();
        if (peek() == ']') { i++; return list; }
        while (true) {
            skipWs();
            list.add(parseValue(depth + 1));
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

    private JsonNumber parseNumber() {
        int start = i;
        while (i < s.length() && "0123456789-.eE+".indexOf(s.charAt(i)) >= 0) i++;
        String num = s.substring(start, i);
        if (num.isEmpty() || num.equals("-") || num.equals(".")) throw err("期望数字");

        if (!JSON_NUMBER.matcher(num).matches()) {
            throw err("非法数字格式: " + num);
        }
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {

            return new JsonNumber(new BigDecimal(num));
        }
        try {
            return new JsonNumber(Long.parseLong(num));
        } catch (NumberFormatException ex) {

            return new JsonNumber(new BigInteger(num));
        }
    }

    private boolean parseBool() {
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        throw err("期望 true / false");
    }

    private JsonNull parseNull() {
        if (s.startsWith("null", i)) { i += 4; return JsonNull.INSTANCE; }
        throw err("期望 null");
    }

    private void expect(char c) {
        if (peek() != c) throw err("期望 '" + c + "'");
        i++;
    }

    private char peek() {
        if (i >= s.length()) throw err("期望更多字符，输入意外结束");
        return s.charAt(i);
    }
    private char next() {
        if (i >= s.length()) throw err("期望更多字符，输入意外结束");
        return s.charAt(i++);
    }

    private void skipWs() {
        // RFC 8259 仅允许空格(0x20)、制表符(0x09)、LF(0x0A)、CR(0x0D) 作为空白；
        // 不接受其他 Unicode 空白（如 U+00A0、U+2028），与 Jackson 一致。
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }

    private IllegalStateException err(String msg) {
        return new IllegalStateException("JSON 解析错误 @" + i + ": " + msg);
    }
}
