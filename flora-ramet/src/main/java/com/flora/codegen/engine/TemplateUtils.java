package com.flora.codegen.engine;

import com.flora.codegen.engine.runtime.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板引擎内部工具方法集合——提供表达式中常用的辅助操作。
 *
 * <p>主要功能：
 * <ul>
 *   <li>真值判断：严格模式，仅 {@link Boolean#TRUE} 视为真</li>
 *   <li>集合展开：将 Map、Iterable、数组、Iterator 统一转为 {@link List}</li>
 *   <li>反射属性访问：通过 getter/is 方法或公有字段获取对象属性值（带反射缓存）</li>
 *   <li>关键词查找：在表达式中定位关键字，自动跳过括号内的嵌套内容</li>
 *   <li>异常构建：统一创建 {@link CodeGenException}，自动拼接行号和源文件信息</li>
 * </ul>
 *
 * <p>工具类不可实例化，所有方法均为静态。
 */
public final class TemplateUtils {

    private TemplateUtils() {}

    // ---- 真值判断 ----

    public static boolean truthy(Object v) {
        // 严格模式：只有 Boolean.TRUE 为真
        return Boolean.TRUE.equals(v);
    }

    // ---- 集合/Map/数组/迭代器 → List ----

    @SuppressWarnings("unchecked")
    public static List<Object> toList(Object col) {
        List<Object> out = new ArrayList<>();
        if (col == null) return out;
        if (col instanceof Map) {
            out.addAll(((Map<Object, Object>) col).entrySet());
        } else if (col instanceof Iterable) {
            for (Object e : (Iterable<Object>) col) out.add(e);
        } else if (col.getClass().isArray()) {
            int len = Array.getLength(col);
            for (int k = 0; k < len; k++) out.add(Array.get(col, k));
        } else if (col instanceof Iterator) {
            Iterator<Object> it = (Iterator<Object>) col;
            while (it.hasNext()) out.add(it.next());
        } else {
            throw err(-1, "无法迭代的类型: " + col.getClass());
        }
        return out;
    }

    // ---- 反射属性访问（带缓存） ----

    /** 属性访问器缓存：(Class, 属性名) → 访问器 */
    private static final Map<Class<?>, Map<String, Accessor>> PROP_CACHE = new ConcurrentHashMap<>();

    /** 内部访问器接口——统一 getMethod/isMethod/Field 三种访问路径。 */
    @FunctionalInterface
    private interface Accessor {
        Object get(Object target) throws Exception;
    }

    public static Object getProperty(Object obj, String field) {
        if (obj == null) return null;
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(field);
        }
        Class<?> clazz = obj.getClass();
        Map<String, Accessor> classCache = PROP_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        Accessor accessor = classCache.get(field);
        if (accessor == null) {
            accessor = resolveAccessor(clazz, field);
            classCache.put(field, accessor);
        }
        try {
            return accessor.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 索引访问：支持数组（含基本类型数组）、List、Map。
     * <ul>
     *   <li>数组/基本类型数组 → {@link Array#get}</li>
     *   <li>List → {@link List#get(int)}</li>
     *   <li>Map → {@link Map#get(Object)}（key 为 index 的字符串形式）</li>
     * </ul>
     */
    public static Object getIndex(Object obj, Object index) {
        if (obj == null) return null;
        // Map 用原始 index（保持其类型），不做数值转换
        if (obj instanceof Map<?, ?> map) {
            return map.get(index);
        }
        // 数组、List 用整数索引
        int i = (index instanceof Number n) ? n.intValue()
                : Integer.parseInt(index.toString());
        if (obj.getClass().isArray()) {
            return Array.get(obj, i);
        }
        if (obj instanceof List<?> list) {
            return list.get(i);
        }
        return null;
    }

    /** 为指定类型和属性名解析访问器（首次反射，后续从缓存命中）。 */
    private static Accessor resolveAccessor(Class<?> clazz, String field) {
        String cap = field.substring(0, 1).toUpperCase() + field.substring(1);
        try {
            Method m = clazz.getMethod("get" + cap);
            return m::invoke;
        } catch (NoSuchMethodException e) {
            try {
                Method m = clazz.getMethod("is" + cap);
                return m::invoke;
            } catch (NoSuchMethodException e2) {
                try {
                    Field f = clazz.getField(field);
                    return f::get;
                } catch (NoSuchFieldException e3) {
                    // 返回始终返回 null 的占位访问器，避免重复反射
                    return target -> null;
                }
            }
        }
    }

    // ---- 关键词查找（跳过括号内容） ----

    public static int indexOfKeyword(String s, String kw) {
        int d = 0;
        for (int k = 0; k + kw.length() <= s.length(); k++) {
            char c = s.charAt(k);
            if (c == '(' || c == '[' || c == '{') d++;
            else if (c == ')' || c == ']' || c == '}') d--;
            if (d == 0 && s.startsWith(kw, k)) {
                boolean beforeOk = (k == 0) || !Character.isJavaIdentifierPart(s.charAt(k - 1));
                boolean afterOk = (k + kw.length() >= s.length()) || !Character.isJavaIdentifierPart(s.charAt(k + kw.length()));
                if (beforeOk && afterOk) return k;
            }
        }
        return -1;
    }

    // ---- 异常构建 ----

    public static CodeGenException err(int line, String msg) {
        return new CodeGenException(line < 0 ? msg : "模板第 " + line + " 行: " + msg);
    }

    public static CodeGenException err(int line, String source, String msg) {
        StringBuilder sb = new StringBuilder();
        if (source != null) {
            sb.append(source).append(": ");
        }
        if (line >= 0) {
            sb.append("第 ").append(line).append(" 行: ");
        }
        sb.append(msg);
        return new CodeGenException(sb.toString());
    }

    /** 带列号的异常构建。col < 0 时等价于 {@link #err(int, String, String)}。 */
    public static CodeGenException err(int line, int col, String source, String msg) {
        StringBuilder sb = new StringBuilder();
        if (source != null) {
            sb.append(source).append(": ");
        }
        if (line >= 0) {
            sb.append("第 ").append(line).append(" 行");
            if (col >= 0) {
                sb.append("第 ").append(col).append(" 列");
            }
            sb.append(": ");
        }
        sb.append(msg);
        return new CodeGenException(sb.toString());
    }

    /**
     * 构建异常，自动追加宏调用链和 include 链信息。
     *
     * <p>当 {@code ctx} 中有活动的宏调用栈或 include 链时，在异常消息末尾追加
     * 类似 {@code "  ↓ 宏调用链: name @ file:line → name @ file:line"} 和
     * {@code "  ↓ 包含链: a.ftl → b.ftl"} 的上下文信息。
     */
    public static CodeGenException err(int line, String source, String msg, Context ctx) {
        String base = err(line, -1, source, msg).getMessage();
        return new CodeGenException(appendChain(base, ctx));
    }

    /** 带列号和上下文的异常构建。 */
    public static CodeGenException err(int line, int col, String source, String msg, Context ctx) {
        String base = err(line, col, source, msg).getMessage();
        return new CodeGenException(appendChain(base, ctx));
    }

    /**
     * 将���调用链和 include 链信息追加到已有消息末尾（如已存在则不再重复追加）。
     * 用于在渲染边界（IncludeNode / MacroCallNode）捕获异常后重新抛出时添加上下文。
     */
    public static String appendChain(String msg, Context ctx) {
        String mc = ctx.getMacroCallChain();
        String ip = ctx.getIncludePath();
        boolean hasMc = (mc == null) || msg.contains("宏调用链");
        boolean hasIp = (ip == null) || msg.contains("包含链");
        if (hasMc && hasIp) return msg;
        StringBuilder sb = new StringBuilder(msg);
        if (mc != null && !msg.contains("宏调用链")) {
            sb.append("\n  ↓ 宏调用链: ").append(mc);
        }
        if (ip != null && !msg.contains("包含链")) {
            sb.append("\n  ↓ 包含链: ").append(ip);
        }
        return sb.toString();
    }

}
