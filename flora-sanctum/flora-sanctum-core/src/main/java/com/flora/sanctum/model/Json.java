package com.flora.sanctum.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 值模型（对象/数组/字符串/数字/布尔/null）。
 * <p>
 * 仅满足设计负载（manifest / 条目 / 字段 等 JSON）的读写，不追求完整 JSON 标准。
 */
public final class Json {

    private Json() {
    }

    public static final class Node {
        private final Object value;

        private Node(Object value) {
            this.value = value;
        }

        public boolean isObject() {
            return value instanceof Map;
        }

        public boolean isArray() {
            return value instanceof List;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Node> asObject() {
            if (!isObject()) {
                throw new IllegalStateException("not an object");
            }
            return (Map<String, Node>) value;
        }

        @SuppressWarnings("unchecked")
        public List<Node> asArray() {
            if (!isArray()) {
                throw new IllegalStateException("not an array");
            }
            return (List<Node>) value;
        }

        public String asString() {
            return (String) value;
        }

        public long asLong() {
            return ((Number) value).longValue();
        }

        public int asInt() {
            return ((Number) value).intValue();
        }

        public boolean asBoolean() {
            return (Boolean) value;
        }

        public boolean isNull() {
            return value == null;
        }

        public Node get(String key) {
            if (!isObject()) {
                return null;
            }
            return asObject().get(key);
        }

        public String str(String key) {
            Node n = get(key);
            return n == null || n.isNull() ? null : n.asString();
        }

        public Long lng(String key) {
            Node n = get(key);
            return n == null || n.isNull() ? null : n.asLong();
        }

        @Override
        public String toString() {
            return value == null ? "null" : value.toString();
        }
    }

    public static Node parse(String s) {
        Parser p = new Parser(s);
        Node n = p.parseValue();
        p.skipWs();
        if (p.pos() < s.length()) {
            throw new IllegalArgumentException("trailing characters");
        }
        return n;
    }

    public static String stringify(Node n) {
        StringBuilder sb = new StringBuilder();
        write(n, sb);
        return sb.toString();
    }

    public static Node obj() {
        return new Node(new LinkedHashMap<>());
    }

    public static Node arr() {
        return new Node(new ArrayList<>());
    }

    public static Node of(String v) {
        return new Node(v);
    }

    public static Node of(long v) {
        return new Node(v);
    }

    public static Node of(int v) {
        return new Node(v);
    }

    public static Node of(boolean v) {
        return new Node(v);
    }

    public static Node ofNull() {
        return new Node(null);
    }

    public static Node put(Node o, String key, Node val) {
        o.asObject().put(key, val);
        return o;
    }

    private static void write(Node n, StringBuilder sb) {
        if (n == null || n.isNull()) {
            sb.append("null");
        } else if (n.isObject()) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Node> e : n.asObject().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (n.isArray()) {
            sb.append('[');
            boolean first = true;
            for (Node e : n.asArray()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(e, sb);
            }
            sb.append(']');
        } else if (n.value instanceof String) {
            writeString((String) n.value, sb);
        } else if (n.value instanceof Boolean) {
            sb.append(n.value);
        } else if (n.value instanceof Number) {
            sb.append(n.value);
        } else {
            throw new IllegalStateException("unsupported value");
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        int pos() {
            return pos;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Node parseValue() {
            skipWs();
            if (pos >= s.length()) {
                throw new IllegalArgumentException("unexpected end");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return new Node(parseObject());
                case '[': return new Node(parseArray());
                case '"': return new Node(parseString());
                case 't':
                    expect("true");
                    return new Node(true);
                case 'f':
                    expect("false");
                    return new Node(false);
                case 'n':
                    expect("null");
                    return new Node(null);
                default:
                    return new Node(parseNumber());
            }
        }

        private void expect(String w) {
            if (!s.startsWith(w, pos)) {
                throw new IllegalArgumentException("unexpected token at " + pos);
            }
            pos += w.length();
        }

        private Map<String, Node> parseObject() {
            pos++; // {
            skipWs();
            Map<String, Node> m = new LinkedHashMap<>();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw new IllegalArgumentException("expected key");
                }
                String key = parseString();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw new IllegalArgumentException("expected :");
                }
                pos++;
                m.put(key, parseValue());
                skipWs();
                if (pos >= s.length()) {
                    throw new IllegalArgumentException("unexpected end");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("expected , or }");
                }
            }
            return m;
        }

        private List<Node> parseArray() {
            pos++; // [
            skipWs();
            List<Node> a = new ArrayList<>();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return a;
            }
            while (true) {
                a.add(parseValue());
                skipWs();
                if (pos >= s.length()) {
                    throw new IllegalArgumentException("unexpected end");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("expected , or ]");
                }
            }
            return a;
        }

        private String parseString() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        throw new IllegalArgumentException("bad escape");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            boolean isFloat = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' ||
                        (c >= '0' && c <= '9')) {
                    if (c == '.' || c == 'e' || c == 'E') {
                        isFloat = true;
                    }
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Long.parseLong(num.substring(1));
            }
        }
    }
}
