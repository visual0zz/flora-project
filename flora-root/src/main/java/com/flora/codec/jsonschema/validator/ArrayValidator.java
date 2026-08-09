package com.flora.codec.jsonschema.validator;

import com.flora.codec.json.JsonArray;
import com.flora.codec.json.JsonObject;
import com.flora.codec.json.JsonValue;
import com.flora.codec.jsonschema.CompiledSchema;
import com.flora.codec.jsonschema.JsonTypes;
import com.flora.codec.jsonschema.SchemaNumbers;
import com.flora.codec.jsonschema.SchemaRegistry;
import com.flora.codec.jsonschema.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 数组关键字校验：{@code prefixItems}/{@code items}/{@code contains}/
 * {@code minContains}/{@code maxContains}/{@code minItems}/{@code maxItems}/{@code uniqueItems}（2020-12）。
 * <p>被求值的索引记录到求值状态，供 {@code unevaluatedItems} 使用。</p>
 */
public final class ArrayValidator implements KeywordValidator {

    private final List<CompiledSchema> prefixItems;
    private final CompiledSchema items;        // null 表示无 items
    private final boolean itemsForbidden;      // items: false
    private final CompiledSchema contains;
    private final Integer minContains;
    private final Integer maxContains;
    private final Integer minItems;
    private final Integer maxItems;
    private final boolean uniqueItems;

    private ArrayValidator(List<CompiledSchema> prefixItems, CompiledSchema items, boolean itemsForbidden,
                           CompiledSchema contains, Integer minContains, Integer maxContains,
                           Integer minItems, Integer maxItems, boolean uniqueItems) {
        this.prefixItems = prefixItems;
        this.items = items;
        this.itemsForbidden = itemsForbidden;
        this.contains = contains;
        this.minContains = minContains;
        this.maxContains = maxContains;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.uniqueItems = uniqueItems;
    }

    public static ArrayValidator of(JsonObject schema, SchemaRegistry registry, String baseUri) {
        List<CompiledSchema> prefix = new ArrayList<>();
        JsonArray prefixItems = schema.getArray("prefixItems");
        if (prefixItems != null) {
            for (JsonValue item : prefixItems.elements()) {
                prefix.add(registry.compileNode(item, baseUri));
            }
        }
        CompiledSchema itemsSchema = null;
        boolean forbidden = false;
        JsonValue items = schema.get("items");
        if (items != null && items.isObject()) {
            itemsSchema = registry.compileNode(items, baseUri);
        } else if (items != null && items.isBool() && !items.asBool()) {
            forbidden = true;
        }
        CompiledSchema containsSchema = null;
        JsonValue contains = schema.get("contains");
        if (contains != null && contains.isObject()) {
            containsSchema = registry.compileNode(contains, baseUri);
        }
        return new ArrayValidator(prefix, itemsSchema, forbidden, containsSchema,
                SchemaNumbers.intOf(schema.get("minContains")), SchemaNumbers.intOf(schema.get("maxContains")),
                SchemaNumbers.intOf(schema.get("minItems")), SchemaNumbers.intOf(schema.get("maxItems")),
                SchemaNumbers.boolOf(schema.get("uniqueItems")));
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (!(instance instanceof List<?> list)) {
            return;
        }
        int size = list.size();
        if (minItems != null && size < minItems) {
            ctx.addError("minItems", "数组长度 " + size + " 小于 " + minItems);
        }
        if (maxItems != null && size > maxItems) {
            ctx.addError("maxItems", "数组长度 " + size + " 大于 " + maxItems);
        }
        if (uniqueItems) {
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    if (JsonTypes.deepEquals(list.get(i), list.get(j))) {
                        ctx.addError("uniqueItems", "数组元素重复（索引 " + i + " 与 " + j + "）");
                        break;
                    }
                }
            }
        }
        validateElements(list, ctx);
        validateContains(list, ctx);
    }

    private void validateElements(List<?> list, ValidationContext ctx) {
        int size = list.size();
        int prefixLen = prefixItems.size();
        int i = 0;
        for (; i < size && i < prefixLen; i++) {
            ctx.evaluation.evaluateIndex(i);
            prefixItems.get(i).validate(list.get(i), ctx.childIndex(i, "prefixItems/" + i));
        }
        if (items != null) {
            for (; i < size; i++) {
                ctx.evaluation.evaluateIndex(i);
                items.validate(list.get(i), ctx.childIndex(i, "items"));
            }
        } else if (itemsForbidden && size > prefixLen) {
            for (int j = prefixLen; j < size; j++) {
                ctx.addError("items", "额外的数组元素不被允许（索引 " + j + "）");
            }
        }
    }

    private void validateContains(List<?> list, ValidationContext ctx) {
        if (contains == null) {
            return;
        }
        int count = 0;
        for (int i = 0; i < list.size(); i++) {
            int before = ctx.errorCount();
            contains.validate(list.get(i), ctx.childIndex(i, "contains"));
            if (ctx.errorCount() == before) {
                count++;
                ctx.evaluation.evaluateIndex(i);
            } else {
                ctx.truncateErrors(before);
            }
        }
        int lower = minContains != null ? minContains : 1;
        if (count < lower) {
            ctx.addError("contains", "匹配 contains 的元素数 " + count + " 小于 " + lower);
        }
        if (maxContains != null && count > maxContains) {
            ctx.addError("maxContains", "匹配 contains 的元素数 " + count + " 大于 " + maxContains);
        }
    }
}
