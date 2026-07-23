package com.flora.java;

import com.flora.java.converter.ConvertFacade;
import com.flora.java.converter.ConverterRegistry;

/**
 * 自定义转换工具类，仅加载 SPI 转换器（不加载内置转换器）。
 * <p>
 * 适用于需要通过 SPI 机制完全自定义转换行为的场景。
 * </p>
 */
public final class CustvertUtil extends ConvertFacade {

    public static final CustvertUtil INSTANCE = new CustvertUtil();

    private CustvertUtil() {
        super(ConverterRegistry.newInstance(false, true));
    }

    /**
     * 将值转换为指定目标类型，仅使用 SPI 转换器，转换失败时返回默认值。
     *
     * @param targetType   目标类型
     * @param value        待转换的值
     * @param defaultValue 转换失败时的默认值
     * @param <T>          目标类型泛型
     * @return 转换后的值，若转换失败则返回 defaultValue
     */
    public static <T> T convertQuietly(Class<T> targetType, Object value, T defaultValue) {
        return INSTANCE.convertQuietly(value, targetType, defaultValue);
    }
}
