package com.flora.root.codec.json.model;

import com.flora.root.codec.json.JsonBuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 标记字段或 getter 方法在 JSON 序列化时忽略。
 * <p>可作用于字段或 getter 方法上，当 {@link JsonBuilder} 序列化 Bean 时将跳过带此注解的成员。</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonIgnore {
}
