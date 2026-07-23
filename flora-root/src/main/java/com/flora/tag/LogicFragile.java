package com.flora.tag;

import java.lang.annotation.*;
/**
 * 标记该方法的逻辑正确性高度微妙，依赖精密的约束（如执行顺序、
 * 异常处理边界、内存可见性规则），微小改动即可引入隐蔽的正确性缺陷。
 * 修改前必须完整理解所有隐含约束，禁止凭直觉调整代码结构。
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD,ElementType.FIELD})
public @interface LogicFragile {
    String value() default "";
}
