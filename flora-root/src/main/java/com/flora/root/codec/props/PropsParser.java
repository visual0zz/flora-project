package com.flora.root.codec.props;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * .properties 文本解析器（纯内存、零依赖、线程安全）。
 * <p>每次 {@link #parse(String)} 调用都新建实例并持有独立状态，无共享可变静态状态，
 * 因此可安全用于多线程，无需 {@code @ThreadFragile}。</p>
 *
 * <p>解析结果将「点号键」展开为嵌套 {@link LinkedHashMap}；叶子值保持为 {@code String}
 * （忠实于 properties 语义，类型转换交由上层 config 处理）。</p>
 *
 * <p>支持的语法（与 {@code java.util.Properties} 大体一致）：
 * 整行注释（首字符为 {@code #} 或 {@code !}）、键值分隔符 {@code =} / {@code :} / 空白、
 * 行续接（行尾 {@code \}）、转义（{@code \= \: \\ \t \n \r \f} 以及 4 位十六进制 Unicode 转义）。</p>
 */
public final class PropsParser {

    private PropsParser() {}

    public static Map<String, Object> parse(String src) {
        if (src == null) throw new IllegalArgumentException("src 为 null");
        Map<String, Object> root = new LinkedHashMap<>();
        for (String line : toLogicalLines(src)) {
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty()) continue;
            char c = trimmed.charAt(0);
            if (c == '#' || c == '!') continue; // 整行注释

            int[] sep = findSeparator(line);
            if (sep == null) {
                String key = unescape(stripLeadingWs(line));
                if (!key.isEmpty()) put(root, key, "");
                continue;
            }
            String rawKey = line.substring(0, sep[0]);
            String rawVal = line.substring(sep[1]);
            String key = unescape(stripLeadingWs(rawKey));
            String val = unescape(stripLeadingWs(rawVal));
            if (!key.isEmpty()) put(root, key, val);
        }
        return root;
    }

    /**
     * 返回 {@code {keyEnd, valueStart}}；未找到分隔符时返回 {@code null}。
     * 分隔符优先级：首个未转义的 {@code =} / {@code :}；否则首个未转义的空白
     * （其后若紧跟 {@code =} / {@code :} 则一并跳过）。
     */
    private static int[] findSeparator(String line) {
        int len = line.length();
        int i = 0;
        while (i < len && Character.isWhitespace(line.charAt(i))) i++; // 跳过键前导空白
        boolean escaped = false;
        while (i < len) {
            char c = line.charAt(i);
            if (escaped) { escaped = false; i++; continue; }
            if (c == '\\') { escaped = true; i++; continue; }
            if (c == '=' || c == ':') {
                int j = i + 1;
                while (j < len && Character.isWhitespace(line.charAt(j))) j++;
                if (j < len && (line.charAt(j) == '=' || line.charAt(j) == ':')) j++;
                return new int[]{i, j};
            }
            if (Character.isWhitespace(c)) {
                int j = i;
                while (j < len && Character.isWhitespace(line.charAt(j))) j++;
                if (j < len && (line.charAt(j) == '=' || line.charAt(j) == ':')) j++;
                return new int[]{i, j};
            }
            i++;
        }
        return null;
    }

    /** 将物理行按行尾 {@code \} 续接为逻辑行（逻辑行不含尾部换行）。 */
    private static List<String> toLogicalLines(String s) {
        List<String> out = new ArrayList<>();
        String[] phys = s.split("\r\n|\r|\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : phys) {
            int n = countTrailingBackslashes(line);
            boolean cont = (n % 2 == 1);
            String kept = line.substring(0, line.length() - n);
            int literal = cont ? (n - 1) / 2 : n / 2;
            sb.append(kept);
            for (int k = 0; k < literal; k++) sb.append('\\');
            if (cont) {
                // 续接：不追加换行，下一物理行直接拼接
            } else {
                out.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out;
    }

    private static int countTrailingBackslashes(String line) {
        int n = 0;
        int i = line.length() - 1;
        while (i >= 0 && line.charAt(i) == '\\') { n++; i--; }
        return n;
    }

    private static String stripLeadingWs(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return s.substring(i);
    }

    /** 处理 properties 转义：{@code \\ \= \: \t \n \r \f} 以及 4 位十六进制 Unicode 转义，其余 {@code \X} 退化为 {@code X}。 */
    private static String unescape(String in) {
        StringBuilder sb = new StringBuilder(in.length());
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i);
            if (c == '\\' && i + 1 < in.length()) {
                char n = in.charAt(i + 1);
                switch (n) {
                    case 'u':
                        if (i + 6 <= in.length()) {
                            sb.append((char) Integer.parseInt(in.substring(i + 2, i + 6), 16));
                            i += 6;
                            continue;
                        }
                        break;
                    case 't': sb.append('\t'); i += 2; continue;
                    case 'n': sb.append('\n'); i += 2; continue;
                    case 'r': sb.append('\r'); i += 2; continue;
                    case 'f': sb.append('\f'); i += 2; continue;
                    default:
                        sb.append(n); i += 2; continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String dottedKey, String value) {
        String[] parts = dottedKey.split("\\.", -1);
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = cur.get(parts[i]);
            if (existing instanceof Map) {
                cur = (Map<String, Object>) existing;
            } else {
                Map<String, Object> m = new LinkedHashMap<>();
                cur.put(parts[i], m);
                cur = m;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }
}
