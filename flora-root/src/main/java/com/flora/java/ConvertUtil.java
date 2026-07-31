package com.flora.java;

import com.flora.java.converter.ConvertFacade;
import com.flora.java.converter.ConverterRegistry;

/**
 * 类型转换工具类，提供静态方法便捷地执行类型转换。
 * <p>
 * 内部通过组合持有默认的 {@link ConvertFacade} 实例（加载内置和 SPI 转换器），
 * 适用于大多数通用转换场景。采用组合而非继承，使外部 API 不暴露转换实现细节。
 * </p>
 */
public final class ConvertUtil {

    private static final ConvertUtil INSTANCE = new ConvertUtil();

    private final ConvertFacade facade;

    private ConvertUtil() {
        this.facade = new ConvertFacade(ConverterRegistry.newInstance());
    }

    /**
     * 将值转换为指定目标类型。
     *
     * @param targetType 目标类型
     * @param value      待转换的值
     * @param <T>        目标类型泛型
     * @return 转换后的值
     */
    public static <T> T convert(Class<T> targetType, Object value) {
        return INSTANCE.facade.convertElements(value, targetType, null);
    }

    /**
     * 将值转换为指定目标类型，转换失败时返回默认值。
     *
     * @param targetType   目标类型
     * @param value        待转换的值
     * @param defaultValue 转换失败时的默认值
     * @param <T>          目标类型泛型
     * @return 转换后的值，若转换失败则返回 defaultValue
     */
    public static <T> T convertQuietly(Class<T> targetType, Object value, T defaultValue) {
        return INSTANCE.facade.convertQuietly(value, targetType, defaultValue);
    }

    /**
     * 将值转换为指定目标类型，同时转换元素类型。
     *
     * @param targetType  目标类型
     * @param value       待转换的值
     * @param elementType 元素类型
     * @param <T>         目标类型泛型
     * @return 转换后的值
     */
    public static <T> T convertElements(Class<T> targetType, Object value, Class<?> elementType) {
        return INSTANCE.facade.convertElements(value, targetType, elementType);
    }

    /**
     * 探测能否将给定值转换为目标类型。
     *
     * @param targetType 目标类型
     * @param value      待转换的值
     * @return 若存在可用转换器则返回 true
     */
    public static boolean canConvert(Class<?> targetType, Object value) {
        return INSTANCE.facade.canConvert(value, targetType);
    }

    /**
     * 探测能否将来源类型转换为目标类型（含元素类型）。
     *
     * @param sourceType  来源类型
     * @param targetType  目标类型
     * @param elementType 元素类型，可为 null
     * @return 若存在可用转换器则返回 true
     */
    public static boolean canConvert(Class<?> sourceType, Class<?> targetType, Class<?> elementType) {
        return INSTANCE.facade.canConvertType(sourceType, targetType, elementType);
    }
}
