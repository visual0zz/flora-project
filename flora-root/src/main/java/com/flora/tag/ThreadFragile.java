package com.flora.tag;

import java.lang.annotation.*;

/**
 * 标记该类型或方法为线程不安全的。
 * 多线程环境下使用此代码需自行处理同步或加锁。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ThreadFragile {
    String value() default "";
}
