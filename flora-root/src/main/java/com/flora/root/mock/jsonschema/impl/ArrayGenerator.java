package com.flora.root.mock.jsonschema.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * array 节点生成：长度取 {@code minItems..maxItems} 区间（受硬上限约束），
 * 依次填充 {@code prefixItems}、{@code contains}、{@code items}。
 * <p>{@code uniqueItems} 采用有限重试：值空间被耗尽时，若尚未满足 {@code minItems}
 * 则放弃唯一性（硬约束优先），否则截断长度——不会因值空间不足而空转。</p>
 */
final class ArrayGenerator {

    /** 无 maxItems 时，数组长度在 minItems 之上追加的额度。 */
    private static final int DEFAULT_ARRAY_SPAN = 3;
    /** 数组长度硬上限（防溢出；schema 声明更大的 maxItems 时以本值为准）。 */
    private static final int HARD_MAX_ITEMS = 32;
    /** 单个位置的唯一值尝试次数上限。 */
    private static final int UNIQUE_ATTEMPTS = 32;

    private ArrayGenerator() {
    }

    static Object generate(GenerationNode node, GenerationContext ctx) {
        int min = Nodes.intOf(node.schema.get("minItems"), 0);
        int max = Math.min(Nodes.intOf(node.schema.get("maxItems"), min + DEFAULT_ARRAY_SPAN),
                HARD_MAX_ITEMS);
        if (max < min) {
            max = min;
        }
        int length = ctx.random().intBetween(min, max);
        boolean unique = Boolean.TRUE.equals(node.schema.get("uniqueItems"));

        List<Object> result = new ArrayList<>();
        Set<Object> used = new LinkedHashSet<>();
        if (node.schema.get("prefixItems") instanceof List<?> prefix) {
            for (int i = 0; i < prefix.size() && result.size() < length; i++) {
                Object item = node.compiler.compile(prefix.get(i), node.baseUri)
                        .generate(ctx.deeper());
                add(result, used, item, unique, min);
            }
        }
        if (node.schema.get("contains") instanceof Map && result.size() < length) {
            Object item = node.compiler.compile(node.schema.get("contains"), node.baseUri)
                    .generate(ctx.deeper());
            add(result, used, item, unique, min);
        }
        GenerationNode itemsNode = node.schema.get("items") instanceof Map itemsMap
                ? node.compiler.compile(itemsMap, node.baseUri) : null;
        int attempts = 0;
        while (result.size() < length) {
            Object item = itemsNode != null
                    ? itemsNode.generate(ctx.deeper())
                    : Nodes.randomScalar(ctx.random());
            if (!unique || Nodes.uniqueAdd(used, item)) {
                result.add(item);
                attempts = 0;
                continue;
            }
            if (++attempts < UNIQUE_ATTEMPTS) {
                continue;
            }
            if (result.size() < min) {
                result.add(item); // 值空间耗尽：硬约束优先，放弃唯一性
                attempts = 0;
                continue;
            }
            break; // 值空间耗尽且已满足 minItems：截断
        }
        return result;
    }

    private static void add(List<Object> result, Set<Object> used, Object item,
                            boolean unique, int min) {
        if (!unique || Nodes.uniqueAdd(used, item)) {
            result.add(item);
        } else if (result.size() < min) {
            result.add(item); // 硬约束优先
        }
    }
}
