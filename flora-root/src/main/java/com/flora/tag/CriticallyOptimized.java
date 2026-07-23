package com.flora.tag;

import java.lang.annotation.*;

/**
 * 标记该方法经过了关键性能优化，修改时需格外谨慎。
 * 微小的改动可能对性能产生重大影响，修改前应充分理解优化细节。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface CriticallyOptimized {
    /**
     * @return 优化原因的简要说明
     */
    String value() default "";
}
