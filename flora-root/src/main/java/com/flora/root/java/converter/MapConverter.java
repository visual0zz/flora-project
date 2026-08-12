package com.flora.root.java.converter;

import com.flora.root.java.Converter;
import com.flora.root.java.TypeMatcher;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Bean（POJO 或 record）转换为 {@link Map} 的转换器。
 * <p>
 * 来源匹配器仅对「可映射 Bean 候选类型」返回 true（排除 Map 自身与 JDK 内置类型），
 * 目标类型固定为 {@link Map}；与 {@link BeanConverter} 的源集合不相交，无重复冲突。
 * 字段映射支持扁平属性、嵌套 Bean（递归为本转换器）及集合/数组中的 Bean 递归。
 * </p>
 */
public final class MapConverter implements Converter {

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Map.class);
    }

    @Override
    public TypeMatcher declareSourceMatcher() {
        return (sourceType, elementType) -> BeanSupport.isBeanType(sourceType);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        Class<?> beanType = from.getClass();
        if (!BeanSupport.isBeanType(beanType)) {
            throw new IllegalArgumentException("MapConverter 仅支持 Bean 来源，收到: "
                    + beanType.getName());
        }
        return BeanSupport.guardCycle(from, () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            for (BeanSupport.Property property : BeanSupport.describe(beanType).values()) {
                Object raw = BeanSupport.read(property, from);
                result.put(property.name(),
                        BeanSupport.convertValue(raw, Map.class, Map.class, BeanSupport.currentRegistry()));
            }
            return result;
        });
    }
}
