package com.flora.tag;

import java.lang.annotation.*;

/**
 * 标记该方法执行较慢，需要较长的运行时间。
 * 调用方应注意可能的性能影响，避免在关键路径上频繁调用。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface SlowFunction {
    /**
     * @return 测试中发现的最长执行时间（秒）
     */
    int seconds();
}
