package com.flora.codec.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 标记字段在 JSON 序列化时忽略。
 * <p>作用于字段上，当 {@link JsonBuilder} 序列化 Bean 时将跳过带此注解的字段。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonIgnore {
}
