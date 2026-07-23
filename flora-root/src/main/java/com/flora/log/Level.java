package com.flora.log;


/**
 * 日志级别枚举，定义了 TRACE、DEBUG、INFO、WARN、ERROR 五个级别。
 * <p>
 * 级别按严重程度递增排列，提供整数表示和启用判断方法。
 */
public enum Level {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    private final int intValue;

    Level(int intValue) {
        this.intValue = intValue;
    }

    /**
     * 返回该级别的整数值，用于比较。
     *
     * @return 级别对应的整数值
     */
    public int toInt() {
        return intValue;
    }

    /**
     * 判断当前级别是否允许输出指定级别 {@code other} 的日志。
     * <p>
     * 规则：当前级别的整数值 <= 目标级别的整数值时允许输出。
     *
     * @param other 要判断的目标日志级别
     * @return 如果当前级别允许输出目标级别，返回 true
     */
    public boolean isEnabled(Level other) {
        return intValue <= other.intValue;
    }
}
