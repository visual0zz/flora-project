package com.flora.java.converter;

import com.flora.java.Converter;
import com.flora.java.TargetMatcher;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link Map} 转换为 Bean（POJO 或 record）的转换器。
 * <p>
 * 来源集合仅含 {@link Map}，目标匹配器仅对「可映射 Bean 候选类型」返回 true，
 * 与 {@link MapConverter} 的源集合不相交，故无需优先级、无重复转换器冲突。
 * 字段映射支持扁平属性、嵌套 Bean（子 Map 递归）及集合/数组中的 Bean 递归。
 * </p>
 */
public final class BeanConverter implements Converter {

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Map.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Map.class);
    }

    @Override
    public TargetMatcher declareTargetMatcher() {
        return (targetType, elementType) -> targetType != null && BeanSupport.isBeanType(targetType);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        if (!(from instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("BeanConverter 仅支持 Map 来源，收到: "
                    + from.getClass().getName());
        }
        Class<?> beanType = toType;
        return BeanSupport.guardCycle(from, () -> instantiate(map, beanType));
    }

    private static Object instantiate(Map<?, ?> map, Class<?> beanType) {
        if (beanType.isRecord()) {
            return instantiateRecord(map, beanType);
        }
        Object bean = newInstance(beanType);
        for (BeanSupport.Property property : BeanSupport.describe(beanType).values()) {
            Object raw = map.get(property.name());
            if (raw == null) {
                continue;
            }
            Object value = BeanSupport.convertValue(raw, property.type(), property.elementType(),
                    BeanSupport.currentRegistry());
            BeanSupport.write(property, bean, value);
        }
        return bean;
    }

    private static Object instantiateRecord(Map<?, ?> map, Class<?> beanType) {
        RecordComponent[] components = beanType.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            Object raw = map.get(components[i].getName());
            args[i] = BeanSupport.convertValue(raw, components[i].getType(),
                    BeanSupport.elementTypeOf(components[i].getGenericType()),
                    BeanSupport.currentRegistry());
        }
        try {
            Constructor<?> ctor = beanType.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法实例化 record " + beanType.getName(), e);
        }
    }

    private static Object newInstance(Class<?> beanType) {
        try {
            Constructor<?> ctor = beanType.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Bean " + beanType.getName()
                    + " 缺少可访问的无参构造器，无法实例化", e);
        }
    }
}
