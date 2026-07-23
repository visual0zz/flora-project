package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Optional 转换器：将任意对象包装为 {@link Optional}。
 * 优先级设为 Integer.MIN_VALUE，确保仅在无其他匹配时被选中。
 */
public final class OptionalConverter implements Converter {

    @Override
    public int declarePriority() {
        return 0;
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Optional.class);
    }

    @Override
    public Object convert(Object obj, Class<?> targetType, Class<?> elementType) {
        return Optional.ofNullable(obj);
    }
}
