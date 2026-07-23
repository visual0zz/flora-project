package com.flora.codec.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * JSON 序列化器，将 Java 对象序列化为 JSON 字符串。
 * <p>支持序列化 Map、List、数组、字符串、数字、布尔值、枚举和普通 Java Bean。
 * 包含循环引用检测，可通过 {@link JsonIgnore} 注解排除字段。</p>
 */
public final class JsonBuilder {

    private static final String INDENT = "  ";

    private JsonBuilder() {}

    /**
     * 将 Java 对象序列化为紧凑格式的 JSON 字符串。
     * <p>普通 Bean 通过反射收集非静态字段进行序列化，
     * 使用 {@link JsonIgnore} 注解排除特定字段。</p>
     *
     * @param obj 要序列化的对象
     * @return 紧凑格式的 JSON 字符串
     * @throws IllegalArgumentException 如果遇到循环引用或不可序列化的值
     */
    public static String toJsonString(Object obj) {
        StringBuilder sb = new StringBuilder();
        serialize(obj, sb, null, new IdentityHashMap<>());
        return sb.toString();
    }

    /**
     * 将 Java 对象序列化为带缩进的美化格式 JSON 字符串。
     *
     * @param obj 要序列化的对象
     * @return 美化格式的 JSON 字符串
     * @throws IllegalArgumentException 如果遇到循环引用或不可序列化的值
     */
    public static String toPrettyJsonString(Object obj) {
        StringBuilder sb = new StringBuilder();
        serialize(obj, sb, "", new IdentityHashMap<>());
        return sb.toString();
    }

    

    /**
     * 序列化任意 Java 对象到 StringBuilder 中。
     * <p>根据对象类型分发到对应的序列化方法，并处理循环引用检测。</p>
     *
     * @param obj      要序列化的对象
     * @param sb       目标 StringBuilder
     * @param indent   当前缩进（null 表示紧凑格式）
     * @param visiting 访问跟踪集，用于循环引用检测
     */
    private static void serialize(Object obj, StringBuilder sb, String indent,
                                  IdentityHashMap<Object, Boolean> visiting) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (obj instanceof CharSequence || obj instanceof Enum) {
            serializeString(obj.toString(), sb);
            return;
        }
        if (obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        if (obj instanceof Number) {
            serializeNumber((Number) obj, sb);
            return;
        }
        
        if (isReferenceType(obj.getClass())) {
            if (visiting.put(obj, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("检测到循环引用: " + obj.getClass().getName()
                        + "@" + System.identityHashCode(obj));
            }
        }
        try {
            if (obj instanceof Map) {
                serializeMap((Map<?, ?>) obj, sb, indent, visiting);
                return;
            }
            if (obj instanceof List) {
                serializeList((List<?>) obj, sb, indent, visiting);
                return;
            }
            if (obj.getClass().isArray()) {
                serializeArray(obj, sb, indent, visiting);
                return;
            }
            
            serializeBean(obj, sb, indent, visiting);
        } finally {
            if (isReferenceType(obj.getClass())) {
                visiting.remove(obj);
            }
        }
    }

    
    private static boolean isReferenceType(Class<?> type) {
        return Map.class.isAssignableFrom(type)
                || List.class.isAssignableFrom(type)
                || type.isArray()
                || (!type.isPrimitive()
                    && !type.getName().startsWith("java.lang.")
                    && !type.getName().startsWith("java.math."));
    }

    

    private static void serializeString(String str, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    

    private static void serializeNumber(Number num, StringBuilder sb) {
        if (num instanceof Double) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("不可序列化为 JSON 的 double 值: " + d);
            }
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
            return;
        }
        if (num instanceof Float) {
            float f = num.floatValue();
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                throw new IllegalArgumentException("不可序列化为 JSON 的 float 值: " + f);
            }
            if (f == Math.floor(f) && !Float.isInfinite(f)) {
                sb.append((long) f);
            } else {
                sb.append(f);
            }
            return;
        }
        sb.append(num.toString());
    }

    

    private static void serializeMap(Map<?, ?> map, StringBuilder sb, String indent,
                                     IdentityHashMap<Object, Boolean> visiting) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        boolean pretty = indent != null;
        sb.append('{');
        String entryIndent = pretty ? indent + INDENT : null;

        Iterator<? extends Entry<?, ?>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Entry<?, ?> entry = it.next();
            if (pretty) sb.append('\n').append(entryIndent);
            serializeString(entry.getKey().toString(), sb);
            sb.append(pretty ? ": " : ":");
            serialize(entry.getValue(), sb, entryIndent, visiting);
            if (it.hasNext()) sb.append(',');
        }
        if (pretty) sb.append('\n').append(indent);
        sb.append('}');
    }

    

    private static void serializeList(List<?> list, StringBuilder sb, String indent,
                                      IdentityHashMap<Object, Boolean> visiting) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        boolean pretty = indent != null;
        sb.append('[');
        String entryIndent = pretty ? indent + INDENT : null;

        Iterator<?> it = list.iterator();
        while (it.hasNext()) {
            if (pretty) sb.append('\n').append(entryIndent);
            serialize(it.next(), sb, entryIndent, visiting);
            if (it.hasNext()) sb.append(',');
        }
        if (pretty) sb.append('\n').append(indent);
        sb.append(']');
    }

    

    private static void serializeArray(Object array, StringBuilder sb, String indent,
                                       IdentityHashMap<Object, Boolean> visiting) {
        int len = Array.getLength(array);
        if (len == 0) {
            sb.append("[]");
            return;
        }
        boolean pretty = indent != null;
        sb.append('[');
        String entryIndent = pretty ? indent + INDENT : null;

        for (int i = 0; i < len; i++) {
            if (pretty) sb.append('\n').append(entryIndent);
            serialize(Array.get(array, i), sb, entryIndent, visiting);
            if (i < len - 1) sb.append(',');
        }
        if (pretty) sb.append('\n').append(indent);
        sb.append(']');
    }

    

    private static void serializeBean(Object obj, StringBuilder sb, String indent,
                                      IdentityHashMap<Object, Boolean> visiting) {
        Map<String, Object> fieldMap = collectFields(obj);
        serializeMap(fieldMap, sb, indent, visiting);
    }

    
    private static Map<String, Object> collectFields(Object obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        
        for (Class<?> clazz = obj.getClass(); clazz != null && clazz != Object.class;
             clazz = clazz.getSuperclass()) {
            for (Field f : clazz.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod)) continue;
                if (f.isSynthetic()) continue;
                if (f.isAnnotationPresent(JsonIgnore.class)) continue;
                
                if (map.containsKey(f.getName())) continue;
                f.setAccessible(true);
                try {
                    map.put(f.getName(), f.get(obj));
                } catch (IllegalAccessException e) {
                    map.put(f.getName(), "<无法访问: " + e.getMessage() + ">");
                }
            }
        }
        return map;
    }
}
