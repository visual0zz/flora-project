package com.flora.codec.jsonschema.validator;

import com.flora.codec.json.JsonArray;
import com.flora.codec.json.JsonObject;
import com.flora.codec.json.JsonValue;
import com.flora.codec.jsonschema.impl.CompiledSchema;
import com.flora.codec.jsonschema.impl.SchemaNumbers;
import com.flora.codec.jsonschema.impl.SchemaRegistry;
import com.flora.codec.jsonschema.impl.ValidationContext;

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

    public static ObjectValidator of(JsonObject schema, SchemaRegistry registry, String baseUri) {
        Map<String, CompiledSchema> props = new LinkedHashMap<>();
        JsonObject properties = schema.getObject("properties");
        if (properties != null) {
            for (Map.Entry<String, JsonValue> e : propertiesMembers(properties)) {
                props.put(e.getKey(), registry.compileNode(e.getValue(), baseUri));
            }
        }
        List<PatternEntry> patterns = new ArrayList<>();
        JsonObject patternProps = schema.getObject("patternProperties");
        if (patternProps != null) {
            for (Map.Entry<String, JsonValue> e : propertiesMembers(patternProps)) {
                patterns.add(new PatternEntry(Pattern.compile(e.getKey()),
                        registry.compileNode(e.getValue(), baseUri)));
            }
        }
        CompiledSchema additional = null;
        boolean forbidden = false;
        JsonValue additionalProps = schema.get("additionalProperties");
        if (additionalProps != null && additionalProps.isObject()) {
            additional = registry.compileNode(additionalProps, baseUri);
        } else if (additionalProps != null && additionalProps.isBool() && !additionalProps.asBool()) {
            forbidden = true;
        }
        List<String> required = new ArrayList<>();
        JsonArray requiredArr = schema.getArray("required");
        if (requiredArr != null) {
            for (JsonValue o : requiredArr.elements()) {
                if (o.isString()) {
                    required.add(o.asString());
                }
            }
        }
        Map<String, List<String>> depRequired = new LinkedHashMap<>();
        JsonObject depReq = schema.getObject("dependentRequired");
        if (depReq != null) {
            for (Map.Entry<String, JsonValue> e : propertiesMembers(depReq)) {
                List<String> names = new ArrayList<>();
                JsonValue l = e.getValue();
                if (l.isArray()) {
                    for (JsonValue o : l.asArray().elements()) {
                        if (o.isString()) {
                            names.add(o.asString());
                        }
                    }
                }
                depRequired.put(e.getKey(), names);
            }
        }
        Map<String, CompiledSchema> depSchemas = new LinkedHashMap<>();
        JsonObject depSch = schema.getObject("dependentSchemas");
        if (depSch != null) {
            for (Map.Entry<String, JsonValue> e : propertiesMembers(depSch)) {
                depSchemas.put(e.getKey(), registry.compileNode(e.getValue(), baseUri));
            }
        }
        JsonValue names = schema.get("propertyNames");
        CompiledSchema propertyNames = (names != null && names.isObject())
                ? registry.compileNode(names, baseUri) : null;
        return new ObjectValidator(props, patterns, additional, forbidden, required,
                depRequired, depSchemas, propertyNames,
                SchemaNumbers.intOf(schema.get("minProperties")), SchemaNumbers.intOf(schema.get("maxProperties")));
    }

    private static java.util.Set<Map.Entry<String, JsonValue>> propertiesMembers(JsonObject obj) {
        return obj.entrySet();
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

    private record PatternEntry(Pattern pattern, CompiledSchema schema) {
    }
}
