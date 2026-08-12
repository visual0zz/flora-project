package com.flora.root.ai.api.impl;

import java.util.List;
import java.util.Map;

/**
 * 厂商适配共享的 JSON 类型辅助（解析响应时安全取值）。
 */
public final class JsonHelper {

    private JsonHelper() {
    }

    /** 取字符串值；null 安全。 */
    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 取 int 值；null/非法返回 0。 */
    public static int intOf(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /** 安全转 List。 */
    public static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    /** 安全转 Map。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
