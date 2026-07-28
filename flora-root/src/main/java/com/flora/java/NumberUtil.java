package com.flora.java;

import java.util.regex.Pattern;

/**
 * 数值处理工具类，提供数值的边界约束、范围判断、安全解析与极值计算等常用操作。
 * <p>所有方法均为 null 安全：对于返回数值的解析方法，源为 null 或无法解析时返回默认值；
 * 对于返回布尔的判断方法，任一参数为 null 时返回 false。区间方法要求 min ≤ max，
 * 违反时抛出 {@link IllegalArgumentException}。</p>
 */
public final class NumberUtil {

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private NumberUtil() {
    }

    // ==================== clamp ====================

    /**
     * 将 int 值约束在 [min, max] 闭区间内。
     *
     * @param value 待约束的值
     * @param min   下界
     * @param max   上界
     * @return 约束后的值（min ≤ 结果 ≤ max）
     * @throws IllegalArgumentException 若 min &gt; max
     */
    public static int clamp(int value, int min, int max) {
        checkOrder(min, max);
        return Math.min(Math.max(value, min), max);
    }

    /**
     * 将 long 值约束在 [min, max] 闭区间内。
     *
     * @param value 待约束的值
     * @param min   下界
     * @param max   上界
     * @return 约束后的值（min ≤ 结果 ≤ max）
     * @throws IllegalArgumentException 若 min &gt; max
     */
    public static long clamp(long value, long min, long max) {
        checkOrder(min, max);
        return Math.min(Math.max(value, min), max);
    }

    /**
     * 将 double 值约束在 [min, max] 闭区间内。
     *
     * @param value 待约束的值
     * @param min   下界
     * @param max   上界
     * @return 约束后的值（min ≤ 结果 ≤ max）
     * @throws IllegalArgumentException 若 min &gt; max
     */
    public static double clamp(double value, double min, double max) {
        checkOrder(min, max);
        return Math.min(Math.max(value, min), max);
    }

    // ==================== isNumber ====================

    /**
     * 判断字符序列是否为合法数值（整数或小数，可带正负号与科学计数法）。
     * <p>空白串、null 或非数值格式均返回 false。不识别十六进制与 NaN/Infinity。</p>
     *
     * @param cs 待检查的字符序列
     * @return 若为合法数值则返回 true
     */
    public static boolean isNumber(CharSequence cs) {
        if (cs == null || cs.isEmpty()) {
            return false;
        }
        return NUMBER_PATTERN.matcher(cs).matches();
    }

    // ==================== 安全解析（带默认值） ====================

    /**
     * 将对象安全转换为 int，转换失败时返回默认值。
     * <p>支持 {@link Number} 与字符串（自动修剪前后空白）。</p>
     *
     * @param value        待转换的对象
     * @param defaultValue 转换失败或 value 为 null 时的默认值
     * @return 转换后的 int 或默认值
     */
    public static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 将对象安全转换为 long，转换失败时返回默认值。
     *
     * @param value        待转换的对象
     * @param defaultValue 转换失败或 value 为 null 时的默认值
     * @return 转换后的 long 或默认值
     */
    public static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 将对象安全转换为 double，转换失败时返回默认值。
     *
     * @param value        待转换的对象
     * @param defaultValue 转换失败或 value 为 null 时的默认值
     * @return 转换后的 double 或默认值
     */
    public static double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 将对象安全转换为 float，转换失败时返回默认值。
     *
     * @param value        待转换的对象
     * @param defaultValue 转换失败或 value 为 null 时的默认值
     * @return 转换后的 float 或默认值
     */
    public static float toFloat(Object value, float defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.floatValue();
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return defaultValue;
            }
            try {
                return Float.parseFloat(t);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== 范围判断 ====================

    /**
     * 判断数值是否在 [min, max] 闭区间内（含端点）。
     *
     * @param value 待判断的数值
     * @param min   下界
     * @param max   上界
     * @return 若 min ≤ value ≤ max 返回 true；任一参数为 null 时返回 false
     * @throws IllegalArgumentException 若 min &gt; max
     */
    public static boolean between(Number value, Number min, Number max) {
        if (value == null || min == null || max == null) {
            return false;
        }
        checkOrder(min.doubleValue(), max.doubleValue());
        double v = value.doubleValue();
        return v >= min.doubleValue() && v <= max.doubleValue();
    }

    /**
     * 判断 Comparable 值是否在 [min, max] 闭区间内（含端点），适用于任意可比较类型。
     *
     * @param value 待判断的值
     * @param min   下界
     * @param max   上界
     * @param <T>   可比较类型
     * @return 若 min ≤ value ≤ max 返回 true；任一参数为 null 时返回 false
     * @throws IllegalArgumentException 若 min &gt; max
     */
    public static <T extends Comparable<? super T>> boolean isInRange(T value, T min, T max) {
        if (value == null || min == null || max == null) {
            return false;
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min 必须小于等于 max: " + min + " > " + max);
        }
        return value.compareTo(min) >= 0 && max.compareTo(value) >= 0;
    }

    // ==================== 极值 ====================

    /**
     * 返回数组中的最大值（忽略 null 元素）。
     *
     * @param values 数值数组
     * @return 最大值；数组为空或全为 null 时返回 null
     */
    public static Number max(Number... values) {
        Number best = null;
        if (values != null) {
            for (Number n : values) {
                if (n == null) {
                    continue;
                }
                if (best == null || n.doubleValue() > best.doubleValue()) {
                    best = n;
                }
            }
        }
        return best;
    }

    /**
     * 返回数组中的最小值（忽略 null 元素）。
     *
     * @param values 数值数组
     * @return 最小值；数组为空或全为 null 时返回 null
     */
    public static Number min(Number... values) {
        Number best = null;
        if (values != null) {
            for (Number n : values) {
                if (n == null) {
                    continue;
                }
                if (best == null || n.doubleValue() < best.doubleValue()) {
                    best = n;
                }
            }
        }
        return best;
    }

    private static void checkOrder(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min 必须小于等于 max: " + min + " > " + max);
        }
    }

    private static void checkOrder(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min 必须小于等于 max: " + min + " > " + max);
        }
    }

    private static void checkOrder(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min 必须小于等于 max: " + min + " > " + max);
        }
    }
}
