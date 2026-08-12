package com.flora.root.codec.jsonschema;

/**
 * 单个校验错误。
 *
 * @param instancePath 实例中的 JSON Pointer 路径，如 {@code "/users/0/name"}
 * @param schemaPath   模式中的路径，如 {@code "#/properties/users/items/required"}
 * @param keyword      失败的关键字，如 {@code "required"}
 * @param message      人类可读的错误描述
 */
public record ValidationError(
        String instancePath,
        String schemaPath,
        String keyword,
        String message) {
}
