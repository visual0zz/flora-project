package com.flora.codec.jsonschema.validator;

import com.flora.codec.jsonschema.ValidationContext;

/**
 * 单个关键字校验器。schema 编译阶段由各关键字构造，校验阶段按序执行。
 */
public interface KeywordValidator {

    /** 校验实例，失败时向 ctx 添加错误。 */
    void validate(Object instance, ValidationContext ctx);
}
