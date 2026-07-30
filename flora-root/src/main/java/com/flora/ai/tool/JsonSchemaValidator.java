package com.flora.ai.tool;

import java.util.*;

/**
 * JSON Schema 参数校验器。
 * <p>纯算法，校验参数 Map 是否符合 ToolSchema 定义。</p>
 */
public class JsonSchemaValidator {

    private JsonSchemaValidator() {}

    /** 校验结果。 */
    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
        public static ValidationResult fail(String error) {
            return new ValidationResult(false, List.of(error));
        }
    }

    /** 校验参数是否符合工具定义。 */
    public static ValidationResult validate(Map<String, Object> args, ToolDefinition def) {
        if (args == null) {
            return ValidationResult.fail("参数为 null");
        }
        List<String> errors = new ArrayList<>();
        ToolSchema schema = def.inputSchema();
        if (schema == null) return ValidationResult.ok();

        // 检查必需参数
        if (schema.required() != null) {
            for (String r : schema.required()) {
                if (!args.containsKey(r) || args.get(r) == null) {
                    errors.add("缺少必需参数: " + r);
                }
            }
        }

        // 检查参数类型
        if (schema.properties() != null) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> propDef = (Map<String, Object>) schema.properties().get(key);
                if (propDef == null) continue;
                String expectedType = (String) propDef.get("type");
                if (expectedType != null && val != null) {
                    if (!typeMatches(expectedType, val)) {
                        errors.add("参数 '" + key + "' 期望类型 " + expectedType + "，实际: " + val.getClass().getSimpleName());
                    }
                }
            }
        }

        return errors.isEmpty()
                ? ValidationResult.ok()
                : new ValidationResult(false, Collections.unmodifiableList(errors));
    }

    private static boolean typeMatches(String expected, Object value) {
        return switch (expected) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List;
            case "object" -> value instanceof Map;
            default -> true;
        };
    }
}
