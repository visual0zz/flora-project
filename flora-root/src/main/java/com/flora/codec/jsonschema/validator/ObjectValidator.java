package com.flora.codec.jsonschema.validator;

import com.flora.codec.jsonschema.CompiledSchema;
import com.flora.codec.jsonschema.SchemaRegistry;
import com.flora.codec.jsonschema.ValidationContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对象关键字校验：{@code properties}/{@code patternProperties}/{@code additionalProperties}/
 * {@code required}/{@code dependentRequired}/{@code dependentSchemas}/{@code propertyNames}/
 * {@code minProperties}/{@code maxProperties}（2020-12）。
 * <p>被求值的属性名记录到求值状态，供 {@code unevaluatedProperties} 使用。</p>
 */
public final class ObjectValidator implements KeywordValidator {

    private final Map<String, CompiledSchema> properties;
    private final List<PatternEntry> patternProperties;
    private final CompiledSchema additionalProperties;   // null 表示无
    private final boolean additionalForbidden;           // additionalProperties: false
    private final List<String> required;
    private final Map<String, List<String>> dependentRequired;
    private final Map<String, CompiledSchema> dependentSchemas;
    private final CompiledSchema propertyNames;
    private final Integer minProperties;
    private final Integer maxProperties;

    private ObjectValidator(Map<String, CompiledSchema> properties,
                            List<PatternEntry> patternProperties,
                            CompiledSchema additionalProperties,
                            boolean additionalForbidden,
                            List<String> required,
                            Map<String, List<String>> dependentRequired,
                            Map<String, CompiledSchema> dependentSchemas,
                            CompiledSchema propertyNames,
                            Integer minProperties,
                            Integer maxProperties) {
        this.properties = properties;
        this.patternProperties = patternProperties;
        this.additionalProperties = additionalProperties;
        this.additionalForbidden = additionalForbidden;
        this.required = required;
        this.dependentRequired = dependentRequired;
        this.dependentSchemas = dependentSchemas;
        this.propertyNames = propertyNames;
        this.minProperties = minProperties;
        this.maxProperties = maxProperties;
    }

    public static ObjectValidator of(Map<String, Object> schema, SchemaRegistry registry, String baseUri) {
        Map<String, CompiledSchema> props = new LinkedHashMap<>();
        if (schema.get("properties") instanceof Map<?, ?> pm) {
            for (Map.Entry<?, ?> e : pm.entrySet()) {
                props.put(String.valueOf(e.getKey()), registry.compileNode(e.getValue(), baseUri));
            }
        }
        List<PatternEntry> patterns = new ArrayList<>();
        if (schema.get("patternProperties") instanceof Map<?, ?> ppm) {
            for (Map.Entry<?, ?> e : ppm.entrySet()) {
                patterns.add(new PatternEntry(Pattern.compile(String.valueOf(e.getKey())),
                        registry.compileNode(e.getValue(), baseUri)));
            }
        }
        CompiledSchema additional = null;
        boolean forbidden = false;
        if (schema.get("additionalProperties") instanceof Map) {
            additional = registry.compileNode(schema.get("additionalProperties"), baseUri);
        } else if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            forbidden = true;
        }
        List<String> required = new ArrayList<>();
        if (schema.get("required") instanceof List<?> rl) {
            for (Object o : rl) {
                if (o instanceof String s) {
                    required.add(s);
                }
            }
        }
        Map<String, List<String>> depRequired = new LinkedHashMap<>();
        if (schema.get("dependentRequired") instanceof Map<?, ?> dm) {
            for (Map.Entry<?, ?> e : dm.entrySet()) {
                List<String> names = new ArrayList<>();
                if (e.getValue() instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof String s) {
                            names.add(s);
                        }
                    }
                }
                depRequired.put(String.valueOf(e.getKey()), names);
            }
        }
        Map<String, CompiledSchema> depSchemas = new LinkedHashMap<>();
        if (schema.get("dependentSchemas") instanceof Map<?, ?> dm) {
            for (Map.Entry<?, ?> e : dm.entrySet()) {
                depSchemas.put(String.valueOf(e.getKey()), registry.compileNode(e.getValue(), baseUri));
            }
        }
        CompiledSchema names = schema.get("propertyNames") instanceof Map
                ? registry.compileNode(schema.get("propertyNames"), baseUri) : null;
        return new ObjectValidator(props, patterns, additional, forbidden, required,
                depRequired, depSchemas, names,
                intOf(schema.get("minProperties")), intOf(schema.get("maxProperties")));
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (!(instance instanceof Map<?, ?> map)) {
            return;
        }
        int size = map.size();
        if (minProperties != null && size < minProperties) {
            ctx.addError("minProperties", "属性数 " + size + " 小于 " + minProperties);
        }
        if (maxProperties != null && size > maxProperties) {
            ctx.addError("maxProperties", "属性数 " + size + " 大于 " + maxProperties);
        }
        for (String name : required) {
            if (!map.containsKey(name)) {
                ctx.addError("required", "缺少必需属性: " + name);
            }
        }
        if (propertyNames != null) {
            for (Object key : map.keySet()) {
                propertyNames.validate(String.valueOf(key), ctx.childProperty(String.valueOf(key), "propertyNames"));
            }
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            boolean matched = false;
            CompiledSchema prop = properties.get(name);
            if (prop != null) {
                matched = true;
                ctx.evaluation.evaluateProperty(name);
                prop.validate(value, ctx.childProperty(name, "properties/" + escape(name)));
            }
            for (PatternEntry pe : patternProperties) {
                if (pe.pattern.matcher(name).find()) {
                    matched = true;
                    ctx.evaluation.evaluateProperty(name);
                    pe.schema.validate(value, ctx.childProperty(name, "patternProperties"));
                }
            }
            if (!matched) {
                if (additionalForbidden) {
                    ctx.addError("additionalProperties", "不允许的属性: " + name);
                } else if (additionalProperties != null) {
                    ctx.evaluation.evaluateProperty(name);
                    additionalProperties.validate(value, ctx.childProperty(name, "additionalProperties"));
                }
            }
        }
        for (Map.Entry<String, List<String>> dep : dependentRequired.entrySet()) {
            if (map.containsKey(dep.getKey())) {
                for (String need : dep.getValue()) {
                    if (!map.containsKey(need)) {
                        ctx.addError("dependentRequired", "属性 " + dep.getKey() + " 需要依赖属性: " + need);
                    }
                }
            }
        }
        for (Map.Entry<String, CompiledSchema> dep : dependentSchemas.entrySet()) {
            if (map.containsKey(dep.getKey())) {
                dep.getValue().validate(instance, ctx.childProperty(dep.getKey(), "dependentSchemas"));
            }
        }
    }

    private static String escape(String name) {
        return name.replace("~", "~0").replace("/", "~1");
    }

    private static Integer intOf(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private record PatternEntry(Pattern pattern, CompiledSchema schema) {
    }
}
