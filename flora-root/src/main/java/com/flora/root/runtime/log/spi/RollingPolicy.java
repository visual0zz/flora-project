package com.flora.root.runtime.log.spi;

/**
 * 滚动文件附加器的滚动策略，决定何时归档当前文件以及如何命名归档文件。
 * <p>
 * 两种策略共享 {@code filePattern}（log4j 风格归档命名模式）与历史保留参数，但触发条件不同：
 * <ul>
 *   <li>{@link #TIME_BASED}：按日期切换；跨日时把当前文件归档为带日期后缀的文件。
 *       依赖 {@code datePattern} 决定日期粒度（如 {@code yyyy-MM-dd} 按天滚动）。</li>
 *   <li>{@link #SIZE_BASED}：按大小切换；当前文件字节数达到 {@code maxSize} 时触发，
 *       旧归档整体后移（{@code .1 → .2 → …}），最新归档为 {@code .1}，最多保留 {@code maxHistory} 个。</li>
 * </ul>
 * 未设置 {@code filePattern} 时，两种策略都回退到默认命名：
 * 时间策略为 {@code base.log.日期}，大小策略为 {@code base.log.N}；
 * 设置了 {@code filePattern} 时则按其中的 {@code %d{...}}/{@code %d}（日期）与 {@code %i}（序号）渲染归档名。
 */
public enum RollingPolicy {
    /**
     * 基于时间滚动：按日期切换归档文件。
     */
    TIME_BASED,
    /**
     * 基于大小滚动：按文件大小上限切换归档文件。
     */
    SIZE_BASED
}
