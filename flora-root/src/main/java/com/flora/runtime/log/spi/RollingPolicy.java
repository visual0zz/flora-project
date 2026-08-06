package com.flora.runtime.log.spi;

/**
 * 滚动文件附加器的滚动策略。
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
