package com.flora.root.codec.yaml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * YAML 解析器（纯内存、零依赖、线程安全）。
 * <p>每次 {@link #parse(String)} / {@link #parseDocuments(String)} 调用都新建实例并持有独立状态，
 * 无共享可变静态状态，因此可安全用于多线程，无需 {@code @ThreadFragile}。</p>
 *
 * <p>支持（尽量完整 YAML 1.2 核心 + 语言无关/失败安全 schema + JSON schema 类型）：
 * 多文档（{@code ---}）、块/流集合、各类标量（普通/单双引号/字面{@code |}/折叠{@code >、
 * chomping 与缩进指示符）、注释、锚点{@code &}/别名{@code *}/合并{@code <<}、标准标签
 * （{@code !!str/int/float/bool/null/map/seq/timestamp}）、普通标量类型推断
 * （null/bool/int/long/float/时间戳/字符串）。</p>
 *
 * <p>范围边界（明确不支持）：可插拔自定义标签解析器 SPI（未知 {@code !local} 标签原样返回）、
 * 复杂映射键{@code ? k\n: v}（仅尽力支持简单键）、非 UTF-8 输入、sexagesimal 等晦涩数值基。</p>
 */
public final class YamlParser {

    private static final Pattern FLOAT = Pattern.compile(
            "[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][-+]?\\d+)?");

    private final Line[] lines;
    private int idx;
    private final Map<String, Object> anchors = new LinkedHashMap<>();

    private YamlParser(String src) {
        this.lines = preprocess(src);
        this.idx = 0;
    }

    public static Object parse(String src) {
        List<Object> docs = parseDocuments(src);
        return docs.isEmpty() ? null : docs.get(0);
    }

    public static List<Object> parseDocuments(String src) {
        if (src == null) throw new IllegalArgumentException("src 为 null");
        YamlParser p = new YamlParser(src);
        List<Object> docs = new ArrayList<>();
        while (true) {
            p.skipBlankAndComments();
            if (p.idx >= p.lines.length) break;
            if (p.isDocEnd()) { p.idx++; continue; }
            if (p.isDocStart()) {
                p.idx++;
                p.skipBlankAndComments();
                if (p.idx >= p.lines.length) break;
            }
            Object doc = p.parseContent(0);
            if (doc != null) docs.add(doc);
            p.skipBlankAndComments();
            if (p.idx < p.lines.length && (p.isDocStart() || p.isDocEnd())) continue;
            break;
        }
        return docs;
    }

    // ===================== 预处理 =====================

    private static final class Line {
        final int indent;
        final String raw;
        final boolean blank;
        final boolean comment;
        Line(int indent, String raw, boolean blank, boolean comment) {
            this.indent = indent; this.raw = raw; this.blank = blank; this.comment = comment;
        }
    }

    private static Line[] preprocess(String src) {
        String[] phys = src.split("\r\n|\r|\n", -1);
        Line[] out = new Line[phys.length];
        for (int i = 0; i < phys.length; i++) {
            String line = phys[i];
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            if (indent < line.length() && line.charAt(indent) == '\t') {
                throw new IllegalStateException("YAML 不允许使用制表符缩进: " + line);
            }
            String stripped = line.strip();
            boolean blank = stripped.isEmpty();
            boolean comment = !blank && line.charAt(indent) == '#';
            out[i] = new Line(indent, line, blank, comment);
        }
        return out;
    }

    // ===================== 文档级辅助 =====================

    private void skipBlankAndComments() {
        while (idx < lines.length) {
            Line L = lines[idx];
            if (L.blank || L.comment || L.raw.strip().startsWith("%")) { idx++; continue; }
            break;
        }
    }

    private boolean isDocStart() {
        String t = lines[idx].raw.strip();
        return t.equals("---") || t.startsWith("--- ");
    }

    private boolean isDocEnd() {
        String t = lines[idx].raw.strip();
        return t.equals("...") || t.startsWith("... ");
    }

    private static String stripIndent(Line L) {
        return L.indent < L.raw.length() ? L.raw.substring(L.indent) : "";
    }

    /** 剥离行内注释：在引号外、且位于行首或空白之后的 {@code #} 视为注释起点。 */
    private static String stripComment(String s) {
        boolean sq = false, dq = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq && c == '#') {
                if (i == 0 || s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t') {
                    return s.substring(0, i).stripTrailing();
                }
            }
        }
        return s;
    }

    // ===================== 节点分发 =====================

    private Object parseContent(int minIndent) {
        skipBlankAndComments();
        if (idx >= lines.length) return null;
        Line L = lines[idx];
        if (L.indent < minIndent) return null;
        if (isDocStart() || isDocEnd()) return null;
        int indent = L.indent;
        String inline = stripComment(stripIndent(L));
        if (inline.isEmpty()) { idx++; return parseContent(minIndent); }

        if (inline.startsWith("*")) {
            String name = inline.substring(1).strip();
            idx++;
            return anchors.getOrDefault(name, null);
        }
        if (inline.startsWith("&")) {
            int sp = 1;
            while (sp < inline.length() && !Character.isWhitespace(inline.charAt(sp))) sp++;
            String name = inline.substring(1, sp);
            String rest = inline.substring(sp).stripLeading();
            idx++;
            Object val = rest.isEmpty() ? parseContent(indent + 1) : parseInlineValue(rest, indent);
            anchors.put(name, val);
            return val;
        }
        if (inline.startsWith("!")) {
            int sp = 1;
            while (sp < inline.length() && inline.charAt(sp) != ' ' && inline.charAt(sp) != '\t') sp++;
            String tag = inline.substring(1, sp);
            String rest = inline.substring(sp).stripLeading();
            idx++;
            Object val = rest.isEmpty() ? parseContent(indent + 1) : parseInlineValue(rest, indent);
            return YamlTags.resolve(tag, val);
        }
        if (inline.startsWith("|") || inline.startsWith(">")) {
            return parseBlockScalar(indent, inline);
        }
        if (inline.startsWith("[") || inline.startsWith("{")) {
            idx++;
            return parseInlineValue(inline, indent);
        }
        if (inline.startsWith("\"") || inline.startsWith("'")) {
            idx++;
            return readQuoted(inline, new int[]{0});
        }
        if (inline.startsWith("-") && (inline.length() == 1
                || inline.charAt(1) == ' ' || inline.charAt(1) == '\t')) {
            return parseBlockSequence(indent);
        }
        if (isMappingEntry(inline)) {
            return parseMappingEntries(indent, null);
        }
        idx++;
        return inferType(inline);
    }

    // ===================== 块映射 =====================

    private Object parseMappingEntries(int indent, String firstInline) {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean seeded = (firstInline != null);
        while (true) {
            String inline;
            if (seeded) { inline = firstInline; seeded = false; }
            else {
                skipBlankAndComments();
                if (idx >= lines.length) break;
                Line L = lines[idx];
                if (L.indent != indent) break;
                if (isDocStart() || isDocEnd()) break;
                inline = stripComment(stripIndent(L));
                if (!isMappingEntry(inline)) break;
                idx++;
            }
            int colon = findKeyColon(inline);
            String rawKey = inline.substring(0, colon).strip();
            String rest = inline.substring(colon + 1);
            String key = resolveKey(rawKey);
            Object val;
            if (rest.strip().isEmpty()) {
                val = parseContent(indent + 1);
            } else {
                val = parseInlineValue(rest.stripLeading(), indent);
            }
            if ("<<".equals(key)) mergeInto(map, val);
            else map.put(key, val);
        }
        return map;
    }

    private static boolean isMappingEntry(String inline) {
        if (inline.startsWith("-")) return false;
        if (inline.startsWith("#")) return false;
        int c = findKeyColon(inline);
        if (c < 0) return false;
        String key = inline.substring(0, c).strip();
        return !key.isEmpty();
    }

    private static int findKeyColon(String inline) {
        boolean inSq = false, inDq = false;
        for (int i = 0; i < inline.length(); i++) {
            char c = inline.charAt(i);
            if (c == '\'' && !inDq) inSq = !inSq;
            else if (c == '"' && !inSq) inDq = !inDq;
            else if (!inSq && !inDq && c == ':') {
                if (i + 1 >= inline.length() || inline.charAt(i + 1) == ' ' || inline.charAt(i + 1) == '\t') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String resolveKey(String rawKey) {
        String k = rawKey;
        if (k.startsWith("\"") || k.startsWith("'")) return readQuoted(k, new int[]{0});
        if (k.startsWith("&")) {
            int sp = 1;
            while (sp < k.length() && !Character.isWhitespace(k.charAt(sp))) sp++;
            return k.substring(1, sp);
        }
        if (k.startsWith("!")) {
            int sp = 1;
            while (sp < k.length() && k.charAt(sp) != ' ' && k.charAt(sp) != '\t') sp++;
            return k.substring(sp).strip();
        }
        return k;
    }

    // ===================== 块序列 =====================

    private Object parseBlockSequence(int indent) {
        List<Object> list = new ArrayList<>();
        while (true) {
            skipBlankAndComments();
            if (idx >= lines.length) break;
            Line L = lines[idx];
            if (L.indent != indent) break;
            if (isDocStart() || isDocEnd()) break;
            String inline = stripComment(stripIndent(L));
            if (!(inline.startsWith("-") && (inline.length() == 1
                    || inline.charAt(1) == ' ' || inline.charAt(1) == '\t'))) break;
            String afterDash = inline.length() == 1 ? "" : inline.substring(2).stripLeading();
            idx++;
            if (afterDash.isEmpty()) {
                list.add(parseContent(indent + 1));
            } else if (isMappingEntry(afterDash)) {
                list.add(parseMappingEntries(indent + 2, afterDash));
            } else {
                list.add(parseInlineValue(afterDash, indent));
            }
        }
        return list;
    }

    // ===================== 行内值 =====================

    private Object parseInlineValue(String token, int keyIndent) {
        if (token.isEmpty()) return null;
        if (token.startsWith("&")) {
            int sp = 1;
            while (sp < token.length() && !Character.isWhitespace(token.charAt(sp))) sp++;
            String name = token.substring(1, sp);
            String rest = token.substring(sp).stripLeading();
            Object val = rest.isEmpty() ? parseContent(keyIndent + 1) : parseInlineValue(rest, keyIndent);
            anchors.put(name, val);
            return val;
        }
        if (token.startsWith("*")) {
            return anchors.getOrDefault(token.substring(1).strip(), null);
        }
        if (token.startsWith("!")) {
            int sp = 1;
            while (sp < token.length() && token.charAt(sp) != ' ' && token.charAt(sp) != '\t') sp++;
            String tag = token.substring(1, sp);
            String rest = token.substring(sp).stripLeading();
            Object val = rest.isEmpty() ? parseContent(keyIndent + 1) : parseInlineValue(rest, keyIndent);
            return YamlTags.resolve(tag, val);
        }
        if (token.startsWith("[") || token.startsWith("{")) {
            return parseFlow(gatherFlow(token, keyIndent));
        }
        if (token.startsWith("|") || token.startsWith(">")) {
            return parseBlockScalar(keyIndent, token);
        }
        if (token.startsWith("\"") || token.startsWith("'")) {
            return readQuoted(token, new int[]{0});
        }
        return inferType(token);
    }

    private String gatherFlow(String firstPart, int keyIndent) {
        StringBuilder sb = new StringBuilder(firstPart);
        int depth = countBrackets(firstPart);
        while (depth > 0 && idx < lines.length) {
            Line L = lines[idx];
            if (L.blank || L.comment || L.raw.strip().startsWith("%")
                    || isDocStart() || isDocEnd() || L.indent <= keyIndent) break;
            sb.append("\n").append(L.raw);
            depth += countBrackets(L.raw);
            idx++;
        }
        return sb.toString();
    }

    private static int countBrackets(String s) {
        int d = 0, i = 0;
        boolean sq = false, dq = false;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\'' && !dq) sq = !sq;
            else if (c == '"' && !sq) dq = !dq;
            else if (!sq && !dq) {
                if (c == '[' || c == '{' || c == '(') d++;
                else if (c == ']' || c == '}' || c == ')') d--;
            }
        }
        return d;
    }

    // ===================== 块标量 =====================

    private Object parseBlockScalar(int refIndent, String header) {
        boolean fold = header.charAt(0) == '>';
        char chomp = ' ';
        int explicitIndent = -1;
        for (int i = 1; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '-') chomp = '-';
            else if (c == '+') chomp = '+';
            else if (c >= '0' && c <= '9') explicitIndent = c - '0';
        }
        List<String> content = new ArrayList<>();
        Integer blockIndent = null;
        while (idx < lines.length) {
            Line L = lines[idx];
            if (L.blank) { content.add(""); idx++; continue; }
            if (L.comment || L.raw.strip().startsWith("%") || isDocStart() || isDocEnd()) break;
            if (L.indent <= refIndent) break;
            if (blockIndent == null) blockIndent = L.indent;
            int strip = (explicitIndent >= 0) ? (refIndent + explicitIndent) : blockIndent;
            String lineContent = L.raw.length() >= strip ? L.raw.substring(strip) : "";
            content.add(lineContent);
            idx++;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String cl : content) {
            if (fold) {
                if (cl.isEmpty()) sb.append('\n');
                else { if (!first) sb.append(' '); sb.append(cl); }
            } else {
                if (!first) sb.append('\n');
                sb.append(cl);
            }
            first = false;
        }
        String result = sb.toString();
        if (chomp == '-') {
            int e = result.length();
            while (e > 0 && result.charAt(e - 1) == '\n') e--;
            result = result.substring(0, e);
        } else if (chomp == ' ') {
            int e = result.length();
            while (e > 0 && result.charAt(e - 1) == '\n') e--;
            result = result.substring(0, e);
            if (!result.isEmpty()) result = result + "\n";
        }
        return result;
    }

    // ===================== 合并键 << =====================

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> map, Object val) {
        if (val instanceof Map) map.putAll((Map<String, Object>) val);
        else if (val instanceof List) {
            for (Object o : (List<Object>) val) {
                if (o instanceof Map) map.putAll((Map<String, Object>) o);
            }
        }
    }

    // ===================== 引号标量 =====================

    private static String readQuoted(String src, int[] p) {
        char q = src.charAt(p[0]);
        boolean dq = (q == '"');
        p[0]++;
        StringBuilder sb = new StringBuilder();
        while (p[0] < src.length()) {
            char c = src.charAt(p[0]);
            if (c == q) {
                if (!dq && p[0] + 1 < src.length() && src.charAt(p[0] + 1) == q) {
                    sb.append('\''); p[0] += 2; continue;
                }
                p[0]++;
                return sb.toString();
            }
            if (dq && c == '\\') {
                p[0]++;
                if (p[0] >= src.length()) break;
                appendDqEscape(sb, src, p, src.charAt(p[0]));
                continue;
            }
            sb.append(c); p[0]++;
        }
        throw new IllegalStateException("YAML 引号未闭合");
    }

    private static void appendDqEscape(StringBuilder sb, String src, int[] p, char e) {
        switch (e) {
            case '"': sb.append('"'); p[0]++; break;
            case '\\': sb.append('\\'); p[0]++; break;
            case '/': sb.append('/'); p[0]++; break;
            case '0': sb.append('\0'); p[0]++; break;
            case 'a': sb.append('\u0007'); p[0]++; break;
            case 'b': sb.append('\b'); p[0]++; break;
            case 't': sb.append('\t'); p[0]++; break;
            case 'n': sb.append('\n'); p[0]++; break;
            case 'v': sb.append('\u000B'); p[0]++; break;
            case 'f': sb.append('\f'); p[0]++; break;
            case 'r': sb.append('\r'); p[0]++; break;
            case 'e': sb.append('\u001B'); p[0]++; break;
            case ' ': sb.append(' '); p[0]++; break;
            case 'u': case 'U': case 'x': {
                int digits = (e == 'U') ? 8 : (e == 'x') ? 2 : 4;
                String hex = src.substring(p[0] + 1, p[0] + 1 + digits);
                sb.append((char) Integer.parseInt(hex, 16));
                p[0] += 1 + digits;
                break;
            }
            default: sb.append(e); p[0]++; break;
        }
    }

    // ===================== 流集合 =====================

    private String fsrc;
    private int[] fpos;

    private Object parseFlow(String text) {
        this.fsrc = text;
        this.fpos = new int[1];
        return flowNode();
    }

    private char peek() {
        return fpos[0] < fsrc.length() ? fsrc.charAt(fpos[0]) : '\0';
    }

    private void skipFlowWs() {
        while (fpos[0] < fsrc.length()) {
            char c = fsrc.charAt(fpos[0]);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') fpos[0]++;
            else break;
        }
    }

    private Object flowNode() {
        skipFlowWs();
        if (fpos[0] >= fsrc.length()) return null;
        char c = fsrc.charAt(fpos[0]);
        if (c == '[') return flowSeq();
        if (c == '{') return flowMap();
        if (c == '"' || c == '\'') return readQuoted(fsrc, fpos);
        if (c == '*') { String n = flowToken(); return anchors.getOrDefault(n.substring(1), null); }
        if (c == '&') { flowToken(); return flowNode(); }
        if (c == '!') {
            String tag = flowTag();
            Object v = flowNode();
            return YamlTags.resolve(tag, v);
        }
        return flowPlain();
    }

    private Object flowSeq() {
        fpos[0]++;
        skipFlowWs();
        List<Object> list = new ArrayList<>();
        if (peek() == ']') { fpos[0]++; return list; }
        while (true) {
            list.add(flowNode());
            skipFlowWs();
            if (fpos[0] >= fsrc.length()) throw new IllegalStateException("YAML flow 序列未闭合");
            char c = fsrc.charAt(fpos[0]);
            if (c == ']') { fpos[0]++; break; }
            if (c == ',') { fpos[0]++; continue; }
            throw new IllegalStateException("YAML flow 序列期望 ',' 或 ']'，得到 '" + c + "'");
        }
        return list;
    }

    private Object flowMap() {
        fpos[0]++;
        skipFlowWs();
        Map<String, Object> map = new LinkedHashMap<>();
        if (peek() == '}') { fpos[0]++; return map; }
        while (true) {
            skipFlowWs();
            String key = flowKey();
            skipFlowWs();
            if (fpos[0] >= fsrc.length() || fsrc.charAt(fpos[0]) != ':') {
                throw new IllegalStateException("YAML flow 映射期望 ':'");
            }
            fpos[0]++;
            skipFlowWs();
            Object val = flowNode();
            map.put(key, val);
            skipFlowWs();
            if (fpos[0] >= fsrc.length()) throw new IllegalStateException("YAML flow 映射未闭合");
            char c = fsrc.charAt(fpos[0]);
            if (c == '}') { fpos[0]++; break; }
            if (c == ',') { fpos[0]++; continue; }
            throw new IllegalStateException("YAML flow 映射期望 ',' 或 '}'");
        }
        return map;
    }

    private String flowKey() {
        skipFlowWs();
        if (peek() == '"' || peek() == '\'') return readQuoted(fsrc, fpos);
        int start = fpos[0];
        while (fpos[0] < fsrc.length() && fsrc.charAt(fpos[0]) != ':') fpos[0]++;
        String tk = fsrc.substring(start, fpos[0]).strip();
        return tk;
    }

    private String flowToken() {
        int start = fpos[0];
        while (fpos[0] < fsrc.length() && !Character.isWhitespace(fsrc.charAt(fpos[0]))
                && fsrc.charAt(fpos[0]) != ',' && fsrc.charAt(fpos[0]) != ']' && fsrc.charAt(fpos[0]) != '}') {
            fpos[0]++;
        }
        return fsrc.substring(start, fpos[0]);
    }

    private String flowTag() {
        fpos[0]++; // 跳过 '!'
        int start = fpos[0];
        while (fpos[0] < fsrc.length() && fsrc.charAt(fpos[0]) != ' ' && fsrc.charAt(fpos[0]) != '\t'
                && fsrc.charAt(fpos[0]) != ',' && fsrc.charAt(fpos[0]) != ']' && fsrc.charAt(fpos[0]) != '}') {
            fpos[0]++;
        }
        return fsrc.substring(start, fpos[0]);
    }

    private Object flowPlain() {
        skipFlowWs();
        if (fpos[0] >= fsrc.length()) return null;
        if (peek() == '"' || peek() == '\'') return readQuoted(fsrc, fpos);
        int start = fpos[0];
        while (fpos[0] < fsrc.length()) {
            char c = fsrc.charAt(fpos[0]);
            if (c == ',' || c == ']' || c == '}') break;
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') break;
            if (c == '#' && (fpos[0] == start || fsrc.charAt(fpos[0] - 1) == ' ')) break;
            fpos[0]++;
        }
        String tk = fsrc.substring(start, fpos[0]).strip();
        return inferType(tk);
    }

    // ===================== 类型推断 =====================

    private static Object inferType(String s) {
        if (s.isEmpty()) return null;
        if (s.equals("~") || s.equalsIgnoreCase("null")) return null;
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (isInt(s)) return parseInteger(s);
        if (isFloat(s)) {
            if (s.equals(".inf") || s.equals(".Inf") || s.equals(".INF")) return Double.POSITIVE_INFINITY;
            if (s.equals("-.inf") || s.equals("-.Inf") || s.equals("-.INF")) return Double.NEGATIVE_INFINITY;
            if (s.equals(".nan") || s.equals(".NaN") || s.equals(".NAN")) return Double.NaN;
            return new BigDecimal(s);
        }
        return s;
    }

    private static boolean isInt(String s) {
        if (s.isEmpty()) return false;
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') i = 1;
        if (i == s.length()) return false;
        if (s.regionMatches(i, "0x", 0, 2) || s.regionMatches(i, "0X", 0, 2)) {
            return hexAll(s, i + 2);
        }
        if (s.regionMatches(i, "0o", 0, 2) || s.regionMatches(i, "0O", 0, 2)) {
            return octAll(s, i + 2);
        }
        if (s.regionMatches(i, "0b", 0, 2) || s.regionMatches(i, "0B", 0, 2)) {
            return binAll(s, i + 2);
        }
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static boolean hexAll(String s, int from) {
        if (from >= s.length()) return false;
        for (int i = from; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static boolean octAll(String s, int from) {
        if (from >= s.length()) return false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '7') return false;
        }
        return true;
    }

    private static boolean binAll(String s, int from) {
        if (from >= s.length()) return false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '0' && c != '1') return false;
        }
        return true;
    }

    private static boolean isFloat(String s) {
        if (s.isEmpty()) return false;
        if (s.equals(".inf") || s.equals(".Inf") || s.equals(".INF")
                || s.equals("-.inf") || s.equals("-.Inf") || s.equals("-.INF")
                || s.equals(".nan") || s.equals(".NaN") || s.equals(".NAN")) return true;
        return FLOAT.matcher(s).matches();
    }

    static Number parseInteger(String s) {
        if (s.isEmpty()) return null;
        int i = 0;
        boolean neg = false;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') {
            neg = (s.charAt(0) == '-');
            i = 1;
        }
        if (i == s.length()) throw new IllegalStateException("YAML 非法整数: " + s);
        int radix = 10;
        String digits;
        if (s.regionMatches(i, "0x", 0, 2) || s.regionMatches(i, "0X", 0, 2)) {
            radix = 16; digits = s.substring(i + 2);
        } else if (s.regionMatches(i, "0o", 0, 2) || s.regionMatches(i, "0O", 0, 2)) {
            radix = 8; digits = s.substring(i + 2);
        } else if (s.regionMatches(i, "0b", 0, 2) || s.regionMatches(i, "0B", 0, 2)) {
            radix = 2; digits = s.substring(i + 2);
        } else {
            digits = s.substring(i);
        }
        if (digits.isEmpty()) throw new IllegalStateException("YAML 非法整数: " + s);
        try {
            long v = Long.parseLong(digits, radix);
            return neg ? -v : v;
        } catch (NumberFormatException ignore) {
            return new BigInteger((neg ? "-" : "") + digits, radix);
        }
    }
}
