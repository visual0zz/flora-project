package com.flora.data;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * 属性描述类。
 * <p>封装 Java 反射字段（Field）及其对应的值，提供字段名称、类型、泛型类型和值的统一访问方式。</p>
 */
public final class PropDesc {

    private final Field field;
    private final Object value;

    PropDesc(Field field, Object value) {
        this.field = field;
        this.value = value;
    }

    /**
     * 获取属性名称。
     *
     * @return 字段名称
     */
    public String getName() {
        return field.getName();
    }

    /**
     * 获取属性的原始类型。
     *
     * @return 字段的 Class 类型
     */
    public Class<?> getType() {
        return field.getType();
    }

    /**
     * 获取属性的泛型类型。
     *
     * @return 字段的泛型 Type
     */
    public Type getGenericType() {
        return field.getGenericType();
    }

    /**
     * 获取属性的值。
     *
     * @return 字段的值
     */
    public Object getValue() {
        return value;
    }

    /**
     * 获取对应的反射 Field 对象。
     *
     * @return java.lang.reflect.Field 实例
     */
    public Field getField() {
        return field;
    }

    @Override
    public String toString() {
        return "PropDesc{name='" + field.getName() + "', type="
                + field.getType().getSimpleName() + ", value=" + value + "}";
    }
}
