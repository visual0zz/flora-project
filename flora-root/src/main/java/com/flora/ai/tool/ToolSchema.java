package com.flora.ai.tool;

import java.util.Map;

/** JSON Schema 描述的工具参数结构。 */
public record ToolSchema(
    String type,
    Map<String, Object> properties,
    java.util.List<String> required,
    String description
) {
    public static ToolSchema object(Map<String, Object> properties, java.util.List<String> required) {
        return new ToolSchema("object", properties, required, null);
    }
}
