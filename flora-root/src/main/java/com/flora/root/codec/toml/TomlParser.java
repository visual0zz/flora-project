package com.flora.root.codec.toml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TOML v1.0 解析器（纯内存、零依赖、线程安全）。
 * <p>每次 {@link #parse(String)} 调用新建实例并持有独立状态，
 * 无共享可变静态状态，可安全用于多线程，无需 {@code @ThreadFragile}。</p>
 *
 * <p>支持：键值对、各类字符串（基本/字面/多行基本/多行字面）、整数（十进制/十六进制/八进制/二进制）、
 * 浮点数（含 inf/nan）、布尔值、日期/时间（偏移日期时间/本地日期时间/日期/时间，均返回字符串）、
 * 数组、行内表、表头 {@code [table]}、表数组 {@code [[items]]}、点分隔键、注释。</p>
 */
public final class TomlParser {

    private final String[] rawLines;
    private int idx;
    private final Map<String, Object> root;
    private List<String> currentPath;

    private TomlParser(String src) {
        if (src == null) throw new IllegalArgumentException("src 为 null");
        this.rawLines = src.split("\r\n|[\n\r]", -1);
        this.root = new LinkedHashMap<>();
        this.currentPath = new ArrayList<>();
        this.idx = 0;
    }

    public static Map<String, Object> parse(String src) {
        TomlParser p = new TomlParser(src);
        p.parseRoot();
        return p.root;
    }

    // ===================== 主解析循环 =====================

    private void parseRoot() {
        while (idx < rawLines.length) {
            String line = rawLines[idx];
            int stripped = strippedLineStart(line);
            if (stripped < 0) { idx++; continue; } // blank / comment / directive
            String trimmed = line.substring(stripped);
            if (trimmed.startsWith("[[")) {
                idx++;
                int end = trimmed.indexOf("]]");
                if (end < 0) throw err("表数组头 ']]' 未闭合: " + trimmed);
                String keyRaw = trimmed.substring(2, end).strip();
                enterArrayOfTable(parseKeyPath(keyRaw));
            } else if (trimmed.startsWith("[")) {
                idx++;
                int end = trimmed.indexOf(']');
                if (end < 0) throw err("表头 ']' 未闭合: " + trimmed);
                if (end == 1) throw err("空表头");
                String keyRaw = trimmed.substring(1, end).strip();
                enterTable(parseKeyPath(keyRaw));
            } else {
                parseKVLine();
            }
        }
    }

    /** 返回行内第一个非空白字符位置；-1 表示空行/注释/指令。 */
    private int strippedLineStart(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '#' || c == '%') return -1;
            if (!(c == ' ' || c == '\t')) return i;
        }
        return -1;
    }

    // ===================== 表头 / 表数组 =====================

    private void enterTable(List<String> path) {
        tableArrayTarget = null; // 离开表数组模式
        currentPath = path;
        Map<String, Object> target = navigateCreate(root, path);
        if (target == null) {
            throw err("路径被非表类型的值占用: " + dottedKey(path));
        }
    }

    @SuppressWarnings("unchecked")
    private void enterArrayOfTable(List<String> path) {
        currentPath = path;
        // 在 root 中导航到父级
        Map<String, Object> parent = root;
        for (int i = 0; i < path.size() - 1; i++) {
            String seg = path.get(i);
            Object exist = parent.get(seg);
            if (exist == null) {
                Map<String, Object> sub = new LinkedHashMap<>();
                parent.put(seg, sub);
                parent = sub;
            } else if (exist instanceof Map) {
                parent = (Map<String, Object>) exist;
            } else {
                throw err("路径段 '" + seg + "' 被非表类型占用");
            }
        }
        String leaf = path.get(path.size() - 1);
        Object arr = parent.get(leaf);
        List<Map<String, Object>> list;
        if (arr == null) {
            list = new ArrayList<>();
            parent.put(leaf, list);
        } else if (arr instanceof List) {
            list = (List<Map<String, Object>>) arr;
        } else {
            throw err("路径 '" + dottedKey(path) + "' 被非数组类型占用");
        }
        Map<String, Object> newTable = new LinkedHashMap<>();
        list.add(newTable);
        // 将 currentPath 指向新创建的 table——但后续 kv 要写入这个 table
        // 我们用一个引用保存这个 table，在 parseKeyValue 中直接写入
        tableArrayTarget = newTable;
    }

    /** 标记当前位于表数组内部，后续 kv 写入此 map 而非导航到 root。 */
    private Map<String, Object> tableArrayTarget;

    // ===================== 键值对 =====================

    private void parseKVLine() {
        // 拼接可能跨行的值（数组 / 行内表 / 多行字符串）
        StringBuilder sb = new StringBuilder();
        sb.append(rawLines[idx]);
        idx++;
        String combined = combineLinesIfNeeded(sb);

        // 拆分为 key 部分和 value 部分
        int eq = findUnquoted(combined, '=');
        if (eq < 0) throw err("缺少 '=': " + combined);

        String keyRaw = combined.substring(0, eq).strip();
        String valRaw = combined.substring(eq + 1).strip();

        // 从 keyRaw 中去掉末尾注释（键中只可能末尾有注释）
        int comment = findUnquoted(keyRaw, '#');
        if (comment >= 0) keyRaw = keyRaw.substring(0, comment).strip();
        if (keyRaw.isEmpty()) throw err("空键");

        List<String> keys = parseKeyPath(keyRaw);
        Object value = parseValue(valRaw);

        // 写入目标
        Map<String, Object> targetMap = tableArrayTarget;
        if (targetMap == null) {
            targetMap = navigateCreate(root, currentPath);
            if (targetMap == null) throw err("路径 '" + dottedKey(currentPath) + "' 被非表类型占用");
        }

        putKeys(targetMap, keys, value);
    }

    /** 如果值部分未闭合（数组/多行字符串/行内表），继续拼接后续行。 */
    private String combineLinesIfNeeded(StringBuilder sb) {
        while (true) {
            String cur = sb.toString();
            int valStart = findValueStart(cur);
            if (valStart < 0) break; // 没有 '='，不可能
            String val = cur.substring(valStart).stripLeading();

            if (val.isEmpty()) {
                // 只有 '=' 后无内容 → 期望多行值
                if (idx >= rawLines.length) break;
                sb.append('\n').append(rawLines[idx++]);
                continue;
            }

            if (val.startsWith("\"\"\"") || val.startsWith("'''")) {
                String delim = val.startsWith("\"\"\"") ? "\"\"\"" : "'''";
                int count = countDelim(val, delim);
                if (count >= 2) break; // 已闭合
                if (idx >= rawLines.length) break;
                sb.append('\n').append(rawLines[idx++]);
                continue;
            }

            if (val.startsWith("[")) {
                if (bracketsBalanced(val, '[', ']')) break;
                if (idx >= rawLines.length) break;
                sb.append('\n').append(rawLines[idx++]);
                continue;
            }

            if (val.startsWith("{")) {
                if (bracketsBalanced(val, '{', '}')) break;
                if (idx >= rawLines.length) break;
                sb.append('\n').append(rawLines[idx++]);
                continue;
            }

            break; // 普通标量值，一行就够了
        }
        return sb.toString();
    }

    /** 找到 '=' 之后的起始位置。 */
    private static int findValueStart(String s) {
        int eq = findUnquoted(s, '=');
        return eq >= 0 ? eq + 1 : -1;
    }

    /** 在字符串中统计分隔符出现次数（用于多行字符串的 """ 和 '''）。 */
    private static int countDelim(String s, String delim) {
        int count = 0, i = 0;
        while (i <= s.length() - delim.length()) {
            boolean match = true;
            for (int j = 0; j < delim.length(); j++) {
                if (s.charAt(i + j) != delim.charAt(j)) { match = false; break; }
            }
            if (match) { count++; i += delim.length(); }
            else i++;
        }
        return count;
    }

    /** 检查括号是否平衡（忽略引号内的括号）。 */
    private static boolean bracketsBalanced(String s, char open, char close) {
        int depth = 0;
        boolean sq = false, dq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq) {
                if (c == open) depth++;
                else if (c == close) depth--;
            }
        }
        return depth <= 0;
    }

    // ===================== 值解析 =====================

    private Object parseValue(String val) {
        if (val.isEmpty()) throw err("空值");

        // 多行字符串
        if (val.startsWith("\"\"\"")) return parseMultiLineBasicString(val);
        if (val.startsWith("'''")) return parseMultiLineLiteralString(val);

        // 单行字符串
        if (val.startsWith("\"")) return parseBasicString(val);
        if (val.startsWith("'")) return parseLiteralString(val);

        // 数组
        if (val.startsWith("[")) return parseArray(val);

        // 行内表
        if (val.startsWith("{")) return parseInlineTable(val);

        // 布尔值
        if (val.equals("true")) return Boolean.TRUE;
        if (val.equals("false")) return Boolean.FALSE;

        // 日期/时间（必须先于数字检测，如 2024-01-01）
        if (isDateLike(val)) return val.strip(); // 以字符串形式返回

        // 数字（含 inf/nan）
        return parseNumber(val);
    }

    // ===================== 字符串 =====================

    private String parseBasicString(String s) {
        // "..." — 处理转义
        int end = findClosingQuote(s, 0, '"');
        if (end < 0) throw err("基本字符串未闭合: " + s);
        String content = s.substring(1, end);
        return unescapeBasic(content);
    }

    private String parseLiteralString(String s) {
        // '...'
        int end = findClosingQuote(s, 0, '\'');
        if (end < 0) throw err("字面字符串未闭合: " + s);
        return s.substring(1, end);
    }

    private String parseMultiLineBasicString(String s) {
        // """..."""
        // 去掉开头的 """
        String body = s.substring(3);
        if (body.startsWith("\n")) body = body.substring(1);
        else if (body.startsWith("\r\n")) body = body.substring(2);

        int end = body.lastIndexOf("\"\"\"");
        if (end < 0) throw err("多行基本字符串未闭合: " + s);

        // 处理尾部的空白修剪：""" 之前的空白（最多5个）被去掉
        String content = body.substring(0, end);
        // 从 content 末尾去除空白直到遇到换行；换行后的空白也被去掉
        content = trimMultiLineEnd(content);

        return unescapeMultiLineBasic(content);
    }

    private String parseMultiLineLiteralString(String s) {
        String body = s.substring(3);
        if (body.startsWith("\n")) body = body.substring(1);
        else if (body.startsWith("\r\n")) body = body.substring(2);

        int end = body.lastIndexOf("'''");
        if (end < 0) throw err("多行字面字符串未闭合: " + s);

        String content = body.substring(0, end);
        content = trimMultiLineEnd(content);
        return content;
    }

    /** 修剪多行字符串末尾：去掉 closing delimiter 前的空白及前面的换行。 */
    private static String trimMultiLineEnd(String content) {
        // 从末尾向前扫描：去掉最多5个空白字符+换行
        int i = content.length() - 1;
        int spaces = 0;
        while (i >= 0 && (content.charAt(i) == ' ' || content.charAt(i) == '\t')) {
            spaces++;
            i--;
        }
        if (i >= 0 && (content.charAt(i) == '\n')) {
            content = content.substring(0, i);
        }
        return content;
    }

    private static int findClosingQuote(String s, int from, char q) {
        for (int i = from + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && q == '"') { i++; continue; }
            if (c == q) return i;
        }
        return -1;
    }

    static String unescapeBasic(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char e = s.charAt(++i);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case 'u': {
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                        break;
                    }
                    case 'U': {
                        String hex = s.substring(i + 1, i + 9);
                        int cp = Integer.parseInt(hex, 16);
                        sb.append(Character.toChars(cp));
                        i += 8;
                        break;
                    }
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescapeMultiLineBasic(String s) {
        // 处理行尾反斜杠: \ 后跟 \n 或 \r\n 表示行连接（去掉换行及后面的空白）
        // 然后再处理转义
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '\n' || n == '\r') {
                    i += (n == '\r' && i + 2 < s.length() && s.charAt(i + 2) == '\n') ? 3 : 2;
                    // 跳过后续空白
                    while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
                    continue;
                }
                // 普通转义
                switch (n) {
                    case 'n': sb.append('\n'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '"': sb.append('"'); i += 2; break;
                    case 'u': {
                        String hex = s.substring(i + 2, i + 6);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 6;
                        break;
                    }
                    case 'U': {
                        String hex = s.substring(i + 2, i + 10);
                        int cp = Integer.parseInt(hex, 16);
                        sb.append(Character.toChars(cp));
                        i += 10;
                        break;
                    }
                    default: sb.append(n); i += 2;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ===================== 数组 =====================

    @SuppressWarnings("unchecked")
    private List<Object> parseArray(String text) {
        // [...]
        String body = text.substring(1, text.lastIndexOf(']')).strip();
        List<Object> list = new ArrayList<>();
        if (body.isEmpty()) return list;
        parseArrayElements(body, list);
        return list;
    }

    private void parseArrayElements(String body, List<Object> list) {
        // 分阶段解析元素（按逗号分割，但注意字符串和嵌套集合内的逗号）
        int i = 0;
        while (i < body.length()) {
            // 跳过空白和换行
            while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t' || body.charAt(i) == '\n' || body.charAt(i) == '\r')) i++;
            if (i >= body.length()) break;
            if (body.charAt(i) == ',') { i++; continue; } // 允许尾部逗号
            if (body.charAt(i) == '#') {
                // 跳过注释到行尾
                while (i < body.length() && body.charAt(i) != '\n') i++;
                continue;
            }

            int start = i;
            i = findElementEnd(body, i);
            String elemStr = body.substring(start, i).strip();
            if (elemStr.isEmpty()) continue;
            // 去除行内注释
            String elemClean = stripComment(elemStr);
            list.add(parseValue(elemClean.strip()));
        }
    }

    /** 查找一个数组/行内表元素的结束位置（考虑字符串和嵌套）。 */
    private static int findElementEnd(String s, int pos) {
        boolean sq = false, dq = false;
        int depthArr = 0, depthMap = 0;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq) {
                if (c == '[') depthArr++;
                else if (c == ']') depthArr--;
                else if (c == '{') depthMap++;
                else if (c == '}') depthMap--;
                else if (c == ',' && depthArr == 0 && depthMap == 0) return pos;
                else if (c == '#' && depthArr == 0 && depthMap == 0) {
                    // 注释跳过到行尾
                    while (pos < s.length() && s.charAt(pos) != '\n') pos++;
                    continue;
                }
            }
            pos++;
        }
        return pos;
    }

    // ===================== 行内表 =====================

    private Map<String, Object> parseInlineTable(String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        String body = text.substring(1, text.lastIndexOf('}')).strip();
        if (body.isEmpty()) return map;
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length()) break;
            if (body.charAt(i) == ',') { i++; continue; }

            // 解析 key
            int keyEnd = findKeyEnd(body, i);
            String keyRaw = body.substring(i, keyEnd).strip();
            i = keyEnd;

            // 期待 '='
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= body.length() || body.charAt(i) != '=') throw err("行内表期望 '='");
            i++;
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;

            // 解析值
            int valEnd = findElementEnd(body, i);
            String valRaw = body.substring(i, valEnd).strip();
            // 去除行内注释
            valRaw = stripComment(valRaw).strip();
            i = valEnd;

            List<String> keys = parseKeyPath(keyRaw);
            putKeys(map, keys, parseValue(valRaw));
        }
        return map;
    }

    /** 查找键的结束位置（遇到引号、字面字符、或键结尾）。 */
    private static int findKeyEnd(String s, int pos) {
        if (pos >= s.length()) return pos;
        char c = s.charAt(pos);
        if (c == '"' || c == '\'') {
            // 引号键
            return findClosingQuote(s, pos, c) + 1;
        }
        // 裸键
        while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos))
                || s.charAt(pos) == '_' || s.charAt(pos) == '-')) pos++;
        return pos;
    }

    // ===================== 数字 =====================

    private static Object parseNumber(String s) {
        s = s.strip();
        if (s.isEmpty()) throw err("空数字值");

        // 特殊浮点值
        if (s.equalsIgnoreCase("inf") || s.equalsIgnoreCase("+inf")) return Double.POSITIVE_INFINITY;
        if (s.equalsIgnoreCase("-inf")) return Double.NEGATIVE_INFINITY;
        if (s.equalsIgnoreCase("nan") || s.equalsIgnoreCase("+nan") || s.equalsIgnoreCase("-nan")) return Double.NaN;

        // 去掉下划线
        String clean = s.replace("_", "");
        boolean negative = clean.charAt(0) == '-';
        int baseIdx = (clean.charAt(0) == '+' || clean.charAt(0) == '-') ? 1 : 0;

        if (clean.length() > baseIdx + 2) {
            String prefix = clean.substring(baseIdx, baseIdx + 2);
            if (prefix.equals("0x") || prefix.equals("0X")) {
                return parseIntegerLiteral(clean, baseIdx + 2, 16, negative);
            }
            if (prefix.equals("0o") || prefix.equals("0O")) {
                return parseIntegerLiteral(clean, baseIdx + 2, 8, negative);
            }
            if (prefix.equals("0b") || prefix.equals("0B")) {
                return parseIntegerLiteral(clean, baseIdx + 2, 2, negative);
            }
        }

        // 检查是否为浮点（包含 . 或 e/E）
        boolean isFloat = false;
        for (int i = baseIdx; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') { isFloat = true; break; }
        }

        if (isFloat) {
            return new BigDecimal(clean);
        }

        return parseIntegerLiteral(clean, baseIdx, 10, negative);
    }

    private static Number parseIntegerLiteral(String s, int from, int radix, boolean negative) {
        String digits = s.substring(from);
        if (digits.isEmpty()) throw err("非法整数: " + s);
        if (radix == 10 && negative) digits = "-" + digits;
        else if (negative) digits = "-" + digits;
        try {
            long v = Long.parseLong(digits, radix);
            return v;
        } catch (NumberFormatException e) {
            try {
                return new BigInteger((negative ? "-" : "") + digits, radix);
            } catch (NumberFormatException e2) {
                throw err("非法整数: " + s);
            }
        }
    }

    // ===================== 日期/时间检测 =====================

    private static boolean isDateLike(String s) {
        // 偏移日期时间: 1979-05-27T07:32:00Z / +08:00 / -05:00
        // 本地日期时间: 1979-05-27T07:32:00
        // 本地日期: 1979-05-27
        // 本地时间: 07:32:00

        if (s.length() < 8) return false;
        char c0 = s.charAt(0);
        if (c0 >= '0' && c0 <= '9') {
            // 日期模式: 开头是数字
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                // 可能是日期
                if (s.length() == 10) return true; // 2024-01-01
                // 后面有 T 或空格
                if (s.length() > 10 && (s.charAt(10) == 'T' || s.charAt(10) == 't' || s.charAt(10) == ' ')) {
                    return true; // 日期时间
                }
            }
            // 时间模式: 00:00:00
            if (s.indexOf(':') == 2 && s.lastIndexOf(':') == 5) {
                return true; // 时间
            }
        }
        return false;
    }

    // ===================== 键解析 =====================

    static List<String> parseKeyPath(String keyRaw) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < keyRaw.length()) {
            // 跳过点
            while (i < keyRaw.length() && keyRaw.charAt(i) == '.') i++;
            if (i >= keyRaw.length()) break;

            char c = keyRaw.charAt(i);
            if (c == '"' || c == '\'') {
                // 引号键
                int end = findClosingQuote(keyRaw, i, c);
                if (end < 0) throw err("键中的引号未闭合: " + keyRaw);
                String key = (c == '"') ? unescapeBasic(keyRaw.substring(i + 1, end))
                                        : keyRaw.substring(i + 1, end);
                parts.add(key);
                i = end + 1;
            } else {
                // 裸键
                int start = i;
                while (i < keyRaw.length() && keyRaw.charAt(i) != '.') i++;
                parts.add(keyRaw.substring(start, i).strip());
            }
        }
        if (parts.isEmpty()) throw err("空键路径");
        return parts;
    }

    // ===================== 树操作 =====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> navigateCreate(Map<String, Object> map, List<String> path) {
        Map<String, Object> cur = map;
        for (String seg : path) {
            Object next = cur.get(seg);
            if (next == null) {
                Map<String, Object> sub = new LinkedHashMap<>();
                cur.put(seg, sub);
                cur = sub;
            } else if (next instanceof Map) {
                cur = (Map<String, Object>) next;
            } else {
                return null; // 被非表类型占用
            }
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static void putKeys(Map<String, Object> target, List<String> keys, Object value) {
        Map<String, Object> cur = target;
        for (int i = 0; i < keys.size() - 1; i++) {
            String k = keys.get(i);
            Object next = cur.get(k);
            if (next == null) {
                Map<String, Object> sub = new LinkedHashMap<>();
                cur.put(k, sub);
                cur = sub;
            } else if (next instanceof Map) {
                cur = (Map<String, Object>) next;
            } else {
                throw err("键 '" + k + "' 被非表类型占用");
            }
        }
        String lastKey = keys.get(keys.size() - 1);
        if (cur.containsKey(lastKey)) {
            throw err("重复键 '" + lastKey + "'");
        }
        cur.put(lastKey, value);
    }

    // ===================== 辅助 =====================

    /** 找到第一个不在引号内的指定字符。 */
    static int findUnquoted(String s, char target) {
        boolean sq = false, dq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq && c == target) return i;
        }
        return -1;
    }

    /** 去除行内注释（仅处理引号外的 #）。 */
    static String stripComment(String s) {
        boolean sq = false, dq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq && c == '#') {
                // 检查前一个字符是空白或行首
                if (i == 0 || s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t') {
                    return s.substring(0, i).stripTrailing();
                }
            }
        }
        return s;
    }

    private static String dottedKey(List<String> parts) {
        return String.join(".", parts);
    }

    private static IllegalStateException err(String msg) {
        return new IllegalStateException("TOML: " + msg);
    }
}
