package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;

/**
 * 空操作转换器：当来源类型与目标类型相同或可向上转型（upcast）时使用。
 * 优先级设为 Integer.MIN_VALUE，确保仅在无其他匹配时被选中。
 */
public final class NoopConverter implements Converter {

    @Override
    public int declarePriority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Object.class);
    }

    @Override
    public Object convert(Object obj, Class<?> targetType, Class<?> elementType) {
        return obj;
    }
}
