package com.flora.root.java;

import com.flora.root.java.converter.ConverterRegistry;
import com.flora.root.java.converter.ConvertFacade;

/**
 * 转换上下文，用于在转换执行期间向转换器传递「当前正在使用的注册中心」。
 * <p>
 * 集合 / 数组转换器在转换元素时，应当优先使用与自身一致的注册中心，
 * 而非全局默认的 {@link ConvertUtil}，以保证自定义注册中心（如仅加载 SPI 的
 * {@link CustvertUtil}）的元素转换也遵循同一套转换器集合。
 * <p>
 * 通过 {@link ThreadLocal} 传递，避免向 {@link Converter} 接口签名注入额外参数；
 * 每个线程独立持有，不存在跨线程共享可变状态，因此线程安全。
 * 由 {@link ConvertFacade} 在转换入口设置、出口（finally）清理。
 */
public final class ConversionContext {

    private static final ThreadLocal<ConverterRegistry> ACTIVE_REGISTRY = new ThreadLocal<>();

    private ConversionContext() {
    }

    /**
     * 设置当前线程正在使用的注册中心（转换入口调用）。
     *
     * @param registry 当前注册中心
     */
    public static void setRegistry(ConverterRegistry registry) {
        ACTIVE_REGISTRY.set(registry);
    }

    /**
     * 清除当前线程的注册中心（转换出口 finally 调用），避免线程复用导致的串扰。
     */
    public static void clear() {
        ACTIVE_REGISTRY.remove();
    }

    /**
     * 获取当前线程正在使用的注册中心；若无则返回 {@code null}
     * （此时调用方应回退到全局默认的 {@link ConvertUtil}）。
     *
     * @return 当前注册中心，或 {@code null}
     */
    public static ConverterRegistry currentRegistry() {
        return ACTIVE_REGISTRY.get();
    }
}
