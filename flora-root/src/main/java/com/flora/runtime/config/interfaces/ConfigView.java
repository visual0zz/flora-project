package com.flora.runtime.config.interfaces;

/**
 * 轻量只读配置视图：只提供按点号路径的下钻查询，不含类型化取值与底层数据导出。
 * <p>语义约定：{@link #get(String)} 对子结构（Map）返回可继续下钻的 {@link ConfigView}，
 * 对标量返回原值，路径缺失返回 {@code null}；{@link #getSubConfig(String)} 对标量路径抛
 * {@link com.flora.runtime.config.ConfigException}。</p>
 */
public interface ConfigView {

    /** 按点号路径获取值（如 {@code "a.b.c"}）；子结构返回可继续下钻的子视图，标量返回原值，缺失返回 null。 */
    Object get(String path);

    /** 按点号路径获取子视图；路径不存在返回 null，路径对应值为标量时抛 {@link com.flora.runtime.config.ConfigException}。 */
    ConfigView getSubConfig(String path);
}
