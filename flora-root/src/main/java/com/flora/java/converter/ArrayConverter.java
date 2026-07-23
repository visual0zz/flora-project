package com.flora.java.converter;

import com.flora.java.ConvertUtil;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;

/**
 * 数组转换器，将任意对象转换为数组类型。
 * <p>
 * 支持从数组、{@link java.util.Collection} 或单个对象转换为指定元素类型的数组。
 * 转换时使用 {@link com.flora.java.ConvertUtil} 对每个元素进行类型转换。
 * </p>
 */
public final class ArrayConverter implements Converter {

    public ArrayConverter() {
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Object[].class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        if (!toType.isArray()) {
            throw new IllegalArgumentException("ArrayConverter 仅支持数组目标，收到: " + toType.getName());
        }
        Class<?> comp = elementType != null ? elementType : toType.getComponentType();
        if (from.getClass().isArray()) {
            int srcLen = Array.getLength(from);
            if (from.getClass().getComponentType() == comp) {
                return from;
            }
            Object result = Array.newInstance(comp, srcLen);
            for (int i = 0; i < srcLen; i++) {
                Array.set(result, i, convertElement(Array.get(from, i), comp));
            }
            return result;
        }
        if (from instanceof Collection<?> coll) {
            Object result = Array.newInstance(comp, coll.size());
            int i = 0;
            for (Object elem : coll) {
                Array.set(result, i++, convertElement(elem, comp));
            }
            return result;
        }
        Object result = Array.newInstance(comp, 1);
        Array.set(result, 0, convertElement(from, comp));
        return result;
    }

    /**
     * 转换单个数组元素为目标组件类型。
     * 若目标组件类型为 {@link Object} 则直接返回原元素。
     *
     * @param element               待转换的元素
     * @param targetComponentType 目标组件类型
     * @return 转换后的元素
     */
    private Object convertElement(Object element, Class<?> targetComponentType) {
        if (targetComponentType != Object.class) {
            return ConvertUtil.convert(targetComponentType, element);
        }
        return element;
    }
}
