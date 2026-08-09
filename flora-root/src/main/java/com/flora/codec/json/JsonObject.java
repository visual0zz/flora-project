package com.flora.codec.json;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON 对象值，按插入顺序持有键值对，是整个 JSON 工具的核心数据与默认类型。
 * <p>解析器 {@link JsonParser} 的顶层与非叶子对象均产出本类型；
 * 提供与 {@code Map<String, Object>} 原生树的双向转换（{@link #toMap()} / {@link #fromMap(Map)}），
 * 以及与普通 Java Bean 的双向转换（{@link #toBean(Class)} / {@link #fromBean(Object)}）。</p>
 * <p>写入时接受 {@link JsonValue} 或任意原生值（{@code null}、{@code String}、{@code Number}、
 * {@code Boolean}、{@code List}、数组、{@code Map}），原生值会经 {@link JsonConversions} 自动包裹；
 * 读取时除通用 {@link #get(String)} 外，另提供类型安全的 {@code getXxx} 取值助手。</p>
 */
public final class JsonObject implements JsonValue {

    private final Map<String, JsonValue> members;

    public JsonObject() {
        this.members = new LinkedHashMap<>();
    }

    private JsonObject(Map<String, JsonValue> members) {
        this.members = members;
    }

    /** 键值对数量。 */
    public int size() {
        return members.size();
    }

    /** 是否为空。 */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** 是否包含指定键。 */
    public boolean containsKey(String key) {
        return members.containsKey(key);
    }

    /** 所有键的视图。 */
    public Set<String> keySet() {
        return members.keySet();
    }

    /** 所有值的只读视图。 */
    public Collection<JsonValue> values() {
        return members.values();
    }

    /** 键值条目的只读视图，便于遍历（每条目的值为 {@link JsonValue}）。 */
    public Set<Map.Entry<String, JsonValue>> entrySet() {
        return members.entrySet();
    }

    /** 取得指定键的 {@link JsonValue}；不存在时返回 {@code null}。 */
    public JsonValue get(String key) {
        return members.get(key);
    }

    /** 放入一个 {@link JsonValue}，返回自身。 */
    public JsonObject put(String key, JsonValue value) {
        members.put(key, value);
        return this;
    }

    /** 放入一个原生值（自动包裹），返回自身。 */
    public JsonObject put(String key, Object value) {
        members.put(key, JsonConversions.toValue(value));
        return this;
    }

    /** 移除指定键，返回自身。 */
    public JsonObject remove(String key) {
        members.remove(key);
        return this;
    }

    /** 浅拷贝本对象（键共享，值引用共享）。 */
    public JsonObject copy() {
        return new JsonObject(new LinkedHashMap<>(members));
    }

    // ====== 类型安全取值助手 ======

    /** 取得字符串值；键缺失或类型不符时返回 {@code null}；非 string 时抛 {@link IllegalStateException}。 */
    public String getString(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asString();
    }

    /** 取得布尔值；键缺失或类型为 null 时返回 {@code null}；非 boolean 时抛异常。 */
    public Boolean getBool(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asBool();
    }

    /** 取得 {@link JsonNumber}；键缺失或类型为 null 时返回 {@code null}；非 number 时抛异常。 */
    public JsonNumber getNumber(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asNumber();
    }

    /** 取得 {@code long} 值；键缺失或类型为 null 时返回 {@code null}；非 number 时抛异常。 */
    public Long getLong(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asNumber().longValue();
    }

    /** 取得 {@code int} 值；键缺失或类型为 null 时返回 {@code null}。 */
    public Integer getInt(String key) {
        Long l = getLong(key);
        return l == null ? null : l.intValue();
    }

    /** 取得 {@code double} 值；键缺失或类型为 null 时返回 {@code null}。 */
    public Double getDouble(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asNumber().doubleValue();
    }

    /** 取得 JSON Object 子值；键缺失或类型为 null 时返回 {@code null}；非 object 时抛异常。 */
    public JsonObject getObject(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asObject();
    }

    /** 取得 JSON Array 子值；键缺失或类型为 null 时返回 {@code null}；非 array 时抛异常。 */
    public JsonArray getArray(String key) {
        JsonValue v = members.get(key);
        return v == null || v.isNull() ? null : v.asArray();
    }

    // ====== 与 Map 原生树互转 ======

    /** 深拷贝为 {@code Map<String, Object>} 原生树（容器递归展开）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> e : members.entrySet()) {
            map.put(e.getKey(), e.getValue().toNative());
        }
        return map;
    }

    /** 由 {@code Map<String, ?>} 原生树构建（容器递归转换；非 JsonValue 元素经 {@link JsonConversions} 包裹）。 */
    public static JsonObject fromMap(Map<String, ?> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, ?> e : map.entrySet()) {
            obj.members.put(e.getKey(), JsonConversions.toValue(e.getValue()));
        }
        return obj;
    }

    // ====== 与 Java Bean 互转 ======

    /**
     * 将本对象填充到指定类型的 Bean 实例。
     * <p>按属性名匹配键；优先通过 setter 方法注入，缺失 setter 时回退到可访问字段。
     * 数字键按目标字段类型做精度转换（{@code long}/{@code int}/{@code double}/{@code BigDecimal} 等）。</p>
     *
     * @param type Bean 类型（须有无参构造器）
     * @param <T>  Bean 类型
     * @return 填充后的 Bean 实例
     * @throws IllegalArgumentException 若无法实例化或注入失败
     */
    public <T> T toBean(Class<T> type) {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            populateBean(instance, type);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法实例化 Bean: " + type.getName(), e);
        }
    }

    private void populateBean(Object instance, Class<?> type) {
        for (Map.Entry<String, JsonValue> e : members.entrySet()) {
            String prop = e.getKey();
            JsonValue value = e.getValue();
            if (value.isNull()) continue;
            try {
                if (!setViaSetter(instance, type, prop, value)) {
                    setViaField(instance, type, prop, value);
                }
            } catch (ReflectiveOperationException ex) {
                // 忽略无法注入的属性，保持与序列化时"尽力而为"一致
            }
        }
    }

    private boolean setViaSetter(Object instance, Class<?> type, String prop, JsonValue value)
            throws ReflectiveOperationException {
        String setterName = "set" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
        for (Method m : type.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                m.setAccessible(true);
                m.invoke(instance, coerce(value, m.getParameterTypes()[0]));
                return true;
            }
        }
        // 也尝试 List/Map 类型的原生树注入（JsonValue.toNative）
        return false;
    }

    private void setViaField(Object instance, Class<?> type, String prop, JsonValue value)
            throws ReflectiveOperationException {
        Field f = findField(type, prop);
        if (f == null) return;
        f.setAccessible(true);
        f.set(instance, coerce(value, f.getType()));
    }

    private static Field findField(Class<?> type, String prop) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(prop);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }

    /** 将 JsonValue 按目标类型做适配转换（含原生树展开与数字精度转换）。 */
    private static Object coerce(JsonValue value, Class<?> target) {
        if (target == JsonValue.class) return value;
        if (value.isArray()) {
            if (target == List.class || target == Collection.class) return value.toNative();
            if (target.isArray()) return toArray(value.asArray(), target.getComponentType());
        }
        if (value.isObject()) {
            if (target == Map.class) return value.toNative();
            if (!target.isPrimitive() && !target.getName().startsWith("java.")) {
                return value.asObject().toBean(target);
            }
        }
        if (value.isNumber()) {
            JsonNumber n = value.asNumber();
            if (target == long.class || target == Long.class) return n.longValue();
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == short.class || target == Short.class) return n.shortValue();
            if (target == byte.class || target == Byte.class) return n.byteValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == BigDecimal.class) return n.decimalValue();
            if (target == BigInteger.class) return n.decimalValue().toBigInteger();
        }
        if (value.isString() && (target == char.class || target == Character.class)) {
            String s = value.asString();
            if (s.length() == 1) return s.charAt(0);
        }
        // 字符串/布尔等目标为 String/Boolean 时直接返回标量原生值
        if (value.isString() && target == String.class) return value.asString();
        if (value.isBool() && (target == boolean.class || target == Boolean.class)) return value.asBool();
        return value.toNative();
    }

    private static Object toArray(JsonArray array, Class<?> componentType) {
        int len = array.size();
        Object result = java.lang.reflect.Array.newInstance(componentType, len);
        for (int i = 0; i < len; i++) {
            java.lang.reflect.Array.set(result, i, coerce(array.get(i), componentType));
        }
        return result;
    }

    /**
     * 将普通 Java Bean 转为 JsonObject（通过 getter 收集属性，支持 {@link JsonIgnore}）。
     *
     * @param bean 源 Bean（getter 收集属性；容器/标量递归转换）
     * @return 对应的 JsonObject
     */
    public static JsonObject fromBean(Object bean) {
        if (bean == null) throw new IllegalArgumentException("bean 为 null");
        JsonObject obj = new JsonObject();
        for (Method m : bean.getClass().getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == void.class) continue;

            String name = m.getName();
            String propName = null;
            if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                propName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                    && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                propName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }
            if (propName == null) continue;
            if (m.isAnnotationPresent(JsonIgnore.class)) continue;
            if (fieldHasJsonIgnore(bean.getClass(), propName)) continue;
            if (obj.members.containsKey(propName)) continue;

            try {
                m.setAccessible(true);
                obj.members.put(propName, JsonConversions.toValue(m.invoke(bean)));
            } catch (ReflectiveOperationException ignored) {
                // 跳过无法访问的属性
            }
        }
        return obj;
    }

    private static boolean fieldHasJsonIgnore(Class<?> clazz, String propName) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(propName);
                return f.isAnnotationPresent(JsonIgnore.class);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return false;
    }

    // ====== JsonValue 接口实现 ======

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    @Override
    public Object toNative() {
        return toMap();
    }

    @Override
    public String toJsonString() {
        return JsonBuilder.toJsonString(this);
    }

    @Override
    public String toPrettyString() {
        return JsonBuilder.toPrettyJsonString(this);
    }

    @Override
    public String typeName() {
        return "object";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JsonObject)) return false;
        return members.equals(((JsonObject) o).members);
    }

    @Override
    public int hashCode() {
        return members.hashCode();
    }

    @Override
    public String toString() {
        return toJsonString();
    }
}
