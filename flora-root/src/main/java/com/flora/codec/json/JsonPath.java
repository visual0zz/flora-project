package com.flora.codec.json;

import java.util.List;
import java.util.Map;


/**
 * JSONPath 表达式引擎，支持在解析后的 JSON 对象上进行路径查询。
 * <p>路径语法：以 {@code $} 开头，后跟 {@code .key} 访问对象字段，
 * 或 {@code [index]} 访问数组元素。</p>
 */
public final class JsonPath {

    private JsonPath() {}

    /**
     * 在解析后的 JSON 对象上执行 JSONPath 表达式求值。
     * <p>示例：{@code eval(root, "$.store.books[0].title")}</p>
     *
     * @param root 根对象（通常为 Map 或 List）
     * @param path JSONPath 表达式，如 {@code "$.key1.key2[0]"}
     * @return 查询结果，路径中遇到未找到的字段或越界索引时返回 null
     * @throws IllegalArgumentException 如果路径表达式语法非法或为空
     */
    @SuppressWarnings("unchecked")
    public static Object eval(Object root, String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("JSONPath 表达式不能为空");
        }
        String expr = path.trim();
        
        if (expr.startsWith("$")) {
            expr = expr.substring(1);
        }
        
        if (expr.isEmpty()) {
            return root;
        }

        Object current = root;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (c == '.') {
                
                i++;
                int nameEnd = i;
                while (nameEnd < expr.length()
                        && expr.charAt(nameEnd) != '.'
                        && expr.charAt(nameEnd) != '[') {
                    nameEnd++;
                }
                String key = expr.substring(i, nameEnd);
                if (key.isEmpty()) throw new IllegalArgumentException("路径中存在空字段名: " + expr);
                if (!(current instanceof Map)) return null;
                current = ((Map<String, Object>) current).get(key);
                i = nameEnd;

            } else if (c == '[') {
                
                int end = expr.indexOf(']', i);
                if (end < 0) throw new IllegalArgumentException("未匹配的 ']': " + expr);
                String content = expr.substring(i + 1, end).trim();
                i = end + 1;
                if (content.isEmpty()) throw new IllegalArgumentException("空方括号: " + expr);
                try {
                    int idx = Integer.parseInt(content);
                    if (!(current instanceof List)) return null;
                    List<Object> list = (List<Object>) current;
                    if (idx < 0 || idx >= list.size()) return null;
                    current = list.get(idx);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("方括号内只支持数字索引: " + content);
                }

            } else {
                throw new IllegalArgumentException("非法路径字符 '" + c
                        + "'，表达式应以 '$' 开头，后跟 '.key' 或 '[index]'");
            }
        }
        return current;
    }
}
