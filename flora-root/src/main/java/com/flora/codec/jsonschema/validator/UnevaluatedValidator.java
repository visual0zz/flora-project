package com.flora.codec.jsonschema.validator;

import com.flora.codec.json.model.JsonArray;
import com.flora.codec.json.model.JsonObject;
import com.flora.codec.json.model.JsonValue;
import com.flora.codec.jsonschema.impl.CompiledSchema;
import com.flora.codec.jsonschema.impl.SchemaRegistry;
import com.flora.codec.jsonschema.impl.ValidationContext;

/**
 * {@code unevaluatedProperties}/{@code unevaluatedItems} 校验（2020-12）。
 * <p>作用于本实例作用域内未被其它关键字求值的属性/索引；
 * schema 为对象时校验，为 {@code false} 时禁止。</p>
 */
public final class UnevaluatedValidator implements KeywordValidator {

    private final CompiledSchema properties;
    private final boolean propertiesForbidden;
    private final CompiledSchema items;
    private final boolean itemsForbidden;

    private UnevaluatedValidator(CompiledSchema properties, boolean propertiesForbidden,
                                 CompiledSchema items, boolean itemsForbidden) {
        this.properties = properties;
        this.propertiesForbidden = propertiesForbidden;
        this.items = items;
        this.itemsForbidden = itemsForbidden;
    }

    public static UnevaluatedValidator of(JsonObject schema, SchemaRegistry registry, String baseUri) {
        CompiledSchema props = null;
        boolean propsForbidden = false;
        JsonValue up = schema.get("unevaluatedProperties");
        if (up != null && up.isObject()) {
            props = registry.compileNode(up, baseUri);
        } else if (up != null && up.isBool() && !up.asBool()) {
            propsForbidden = true;
        }
        CompiledSchema items = null;
        boolean itemsForbidden = false;
        JsonValue ui = schema.get("unevaluatedItems");
        if (ui != null && ui.isObject()) {
            items = registry.compileNode(ui, baseUri);
        } else if (ui != null && ui.isBool() && !ui.asBool()) {
            itemsForbidden = true;
        }
        return new UnevaluatedValidator(props, propsForbidden, items, itemsForbidden);
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (instance instanceof JsonObject obj) {
            if (propertiesForbidden || properties != null) {
                for (String key : obj.keySet()) {
                    if (!ctx.evaluation.isPropertyEvaluated(key)) {
                        if (propertiesForbidden) {
                            ctx.addError("unevaluatedProperties", "未求值的属性: " + key);
                        } else {
                            properties.validate(obj.get(key), ctx.childProperty(key, "unevaluatedProperties"));
                        }
                    }
                }
            }
        }
        if (instance instanceof JsonArray array) {
            if (itemsForbidden || items != null) {
                for (int i = 0; i < array.size(); i++) {
                    if (!ctx.evaluation.isIndexEvaluated(i)) {
                        if (itemsForbidden) {
                            ctx.addError("unevaluatedItems", "未求值的数组索引: " + i);
                        } else {
                            items.validate(array.get(i), ctx.childIndex(i, "unevaluatedItems"));
                        }
                    }
                }
            }
        }
    }
}
