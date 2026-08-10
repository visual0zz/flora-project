package com.flora.codec.json;

import com.flora.codec.json.impl.JsonConversions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON 数组值，按插入顺序持有 {@link JsonValue} 列表。
 */
public final class JsonArray implements JsonValue {

    private final List<JsonValue> elements;

    public JsonArray() {
        this.elements = new ArrayList<>();
    }

    public JsonArray(List<JsonValue> elements) {
        this.elements = new ArrayList<>(elements);
    }

    /** 元素个数。 */
    public int size() {
        return elements.size();
    }

    /** 是否为空。 */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /** 取得指定下标元素（不检查越界）。 */
    public JsonValue get(int index) {
        return elements.get(index);
    }

    /** 追加一个值，返回自身以便链式调用。 */
    public JsonArray add(JsonValue value) {
        elements.add(value);
        return this;
    }

    /** 追加一个原生值（null 转为 {@link JsonNull}），返回自身。 */
    public JsonArray add(Object value) {
        elements.add(JsonConversions.toValue(value));
        return this;
    }

    /** 所有元素的只读视图。 */
    public List<JsonValue> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** 深拷贝为 {@code List<Object>} 原生树（容器递归展开）。 */
    public List<Object> toList() {
        return (List<Object>) toNative();
    }

    /** 由 {@code List<?>} 原生列表构建（元素经 {@link JsonConversions} 包裹）。 */
    public static JsonArray fromList(List<?> list) {
        JsonArray array = new JsonArray();
        for (Object o : list) {
            array.elements.add(JsonConversions.toValue(o));
        }
        return array;
    }

    /** 由任意数组构建（元素经 {@link JsonConversions} 包裹）。 */
    public static JsonArray fromArray(Object array) {
        int len = java.lang.reflect.Array.getLength(array);
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }
        return fromList(list);
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonArray asArray() {
        return this;
    }

    @Override
    public Object toNative() {
        List<Object> list = new ArrayList<>(elements.size());
        for (JsonValue v : elements) {
            list.add(v.toNative());
        }
        return list;
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
        return "array";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JsonArray)) return false;
        return elements.equals(((JsonArray) o).elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public String toString() {
        return toJsonString();
    }
}
