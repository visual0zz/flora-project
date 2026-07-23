package com.flora.java.converter;

import com.flora.java.CheckUtil;
import com.flora.java.Converter;

/**
 * 类型转换门面，封装了 {@link ConverterRegistry} 的查找和转换调用逻辑。
 * <p>
 * 提供带异常抛出、静默模式（返回默认值）以及支持元素类型转换三种基本操作。
 * </p>
 */
public class ConvertFacade {

    private final ConverterRegistry registry;

    /**
     * 使用指定的转换器注册中心创建门面实例。
     *
     * @param registry 转换器注册中心
     */
    public ConvertFacade(ConverterRegistry registry) {
        this.registry = registry;
    }


    /**
     * 将指定值转换为目标类型，同时转换元素类型（用于集合/数组等泛型元素转换）。
     *
     * @param value       待转换的值
     * @param targetType  目标类型
     * @param elementType 元素类型
     * @param <T>         目标类型泛型
     * @return 转换后的值，若 value 为 null 则返回 null
     * @throws IllegalArgumentException 若未找到合适的转换器
     */
    public <T> T convertElements(Object value, Class<T> targetType, Class<?> elementType) {
        CheckUtil.notNull(targetType, "目标类型不能为空");
        if (value == null) {
            return null;
        }
        Converter executor = registry.find(value.getClass(), targetType, elementType);
        if (executor == null) {
            throw new IllegalArgumentException("未找到将 " + value.getClass().getName()
                    + " 转换为 " + targetType.getName() + " 的转换器");
        }
        return targetType.cast(executor.convert(value, targetType, elementType));
    }

    /**
     * 将指定值转换为目标类型，转换失败时返回默认值而非抛出异常。
     *
     * @param value       待转换的值
     * @param targetType  目标类型
     * @param defaultValue 转换失败时的默认值
     * @param <T>         目标类型泛型
     * @return 转换后的值，若转换失败则返回 defaultValue
     */
    public <T> T convertQuietly(Object value, Class<T> targetType, T defaultValue) {
        try {
            return convertElements(value, targetType, null);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
