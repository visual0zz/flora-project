package com.flora.java.converter;

import com.flora.java.CheckUtil;
import com.flora.java.ConversionContext;
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
        // 在转换执行期间暴露当前注册中心，供集合 / 数组转换器做元素级转换时复用同一套转换器集合
        ConversionContext.setRegistry(registry);
        try {
            return targetType.cast(executor.convert(value, targetType, elementType));
        } finally {
            ConversionContext.clear();
        }
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

    /**
     * 探测能否将给定值转换为目标类型。
     * <p>
     * 内部复用 {@link ConverterRegistry#find} 的查找逻辑（含来源/目标匹配、优先级、
     * 继承距离计算），仅判定是否存在可用转换器而不实际执行转换。
     * </p>
     *
     * @param value      待转换的值，若 value 为 null 则返回 true（null 值转换为任意目标均返回 null）
     * @param targetType 目标类型，不能为 null
     * @return 若存在可用转换器则返回 true
     * @throws NullPointerException 若 targetType 为 null
     */
    public boolean canConvert(Object value, Class<?> targetType) {
        CheckUtil.notNull(targetType, "目标类型不能为空");
        if (value == null) {
            return true;
        }
        return registry.find(value.getClass(), targetType, null) != null;
    }

    /**
     * 探测能否将来源类型转换为目标类型（含元素类型），用于编译期已知类型而非具体值的场景。
     *
     * @param sourceType  来源类型，若未知可传 null（此时按“null 值可转换为任意目标”返回 true）
     * @param targetType  目标类型，不能为 null
     * @param elementType 元素类型（用于集合/数组转换），可为 null
     * @return 若存在可用转换器则返回 true
     * @throws NullPointerException 若 targetType 为 null
     */
    public boolean canConvertType(Class<?> sourceType, Class<?> targetType, Class<?> elementType) {
        CheckUtil.notNull(targetType, "目标类型不能为空");
        if (sourceType == null) {
            return true;
        }
        return registry.find(sourceType, targetType, elementType) != null;
    }
}
