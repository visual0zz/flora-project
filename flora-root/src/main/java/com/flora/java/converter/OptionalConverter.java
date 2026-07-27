package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Optional 转换器：将任意对象包装为 {@link Optional}。
 * <p>目标类型固定为 {@link Optional}，其匹配器仅在目标类型为 Optional 时命中，
 * 不会与其它转换器产生歧义，故使用默认优先级 0 即可（无需设为最低优先级）。</p>
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
