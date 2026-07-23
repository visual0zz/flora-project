package com.flora.tag;

import java.lang.annotation.*;

/**
 * 标记该类型为只读，类似于String
 * 或者该方法为只读，不会修改任何非局部变量
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ReadOnly {
}
