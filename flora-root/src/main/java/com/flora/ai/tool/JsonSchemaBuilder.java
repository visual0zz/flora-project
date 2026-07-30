package com.flora.ai.tool;

import java.util.*;

/**
 * 从 Java 类型构建 JSON Schema。
 * <p>纯算法，无反射。接受手动描述的类型结构。</p>
 */
public class JsonSchemaBuilder {

    private JsonSchemaBuilder() {}

    /** 从类型名和属性描述构建 JSON Schema Map。 */
    public static Map<String, Object> build(
            String type,
            Map<String, Map<String, Object>> properties,
            List<String> required
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (!properties.isEmpty()) {
            schema.put("properties", new LinkedHashMap<>(properties));
        }
        if (!required.isEmpty()) {
            schema.put("required", new ArrayList<>(required));
        }
        return schema;
    }

    /** 快速创建 string 类型属性的描述。 */
    public static Map<String, Object> stringProp(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        return p;
    }

    /** 快速创建 integer 类型属性的描述。 */
    public static Map<String, Object> integerProp(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "integer");
        p.put("description", description);
        return p;
    }

    /** 快速创建 boolean 类型属性的描述。 */
    public static Map<String, Object> booleanProp(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("description", description);
        return p;
    }

    /** 快速创建 array 类型属性的描述。 */
    public static Map<String, Object> arrayProp(String description, Map<String, Object> items) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "array");
        p.put("description", description);
        p.put("items", items);
        return p;
    }

    /** 快速创建 enum 类型属性的描述。 */
    public static Map<String, Object> enumProp(String description, List<String> values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        p.put("enum", new ArrayList<>(values));
        return p;
    }
}
