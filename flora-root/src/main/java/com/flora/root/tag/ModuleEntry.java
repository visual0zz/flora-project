package com.flora.root.tag;

import java.lang.annotation.*;

/**
 * 标记该类为某个功能模块的入口。
 * <p>使用方接触该模块时，首先打交道的通常就是这个类，
 * 例如日志系统的 {@code LogFactory}、配置中心的 {@code ConfigProvider} 等。</p>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ModuleEntry {
    /**
     * @return 所属模块的简要说明（如 "Logging"、"Config"）
     */
    String value() default "";
}
