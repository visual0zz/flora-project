package com.flora.codec.jsonschema.generator;

/**
 * 生成失败异常（如遇到 {@code false} schema、不可满足的约束）。
 */
public class JsonGenerationException extends RuntimeException {

    public JsonGenerationException(String message) {
        super(message);
    }

    public JsonGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
