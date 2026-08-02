package com.flora.ai.api;

import java.util.Map;

/**
 * 工具定义：模型可调用的函数声明。
 * <p>{@code parameters} 为 JSON Schema（对象格式），描述函数入参结构。</p>
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public static ToolSpec of(String name, String description, Map<String, Object> parametersSchema) {
        return new ToolSpec(name, description,
                parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema));
    }
}
