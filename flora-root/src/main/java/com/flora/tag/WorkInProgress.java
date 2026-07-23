package com.flora.tag;

import java.lang.annotation.*;

/**
 * 标记该类型或方法仍在开发中，尚未完成。
 * 使用此代码时需注意其可能尚未稳定或功能不完整。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface WorkInProgress {
    String value() default "";
}
