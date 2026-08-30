package com.flora.sanctum.core.model.ref;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.StoredNodeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 声明式引用遍历表：节点类型 → 其持有的引用字段名，以及字段名 → 引用默认 kind。
 * <p>
 * GC / 垃圾桶分类复用本表，从节点 JSON 收集全部 {@link Ref} 并经 {@link RefResolverRegistry}
 * 求可达块集合，取代此前散落在各处的 {@code icon}/{@code keyRef} 字符串读取逻辑。
 * 新增引用类型时，只需在此处登记字段，无需改动 GC 遍历代码。
 */
public final class RefScan {

    private static final RefResolverRegistry REGISTRY = new RefResolverRegistry();

    /** 节点类型 → 其引用的字段名列表。 */
    private static final Map<StoredNodeType, List<String>> FIELDS = Map.of(
            StoredNodeType.ENTRY, List.of("iconRef"),
            StoredNodeType.GROUP, List.of("iconRef"),
            StoredNodeType.REMOTE, List.of("keyRef"));

    /** 引用字段名 → 默认 kind（遗留字符串解析时决定 node 种类）。 */
    private static final Map<String, String> FIELD_KIND = Map.of(
            "iconRef", "icon",
            "keyRef", "key");

    private RefScan() {
    }

    /**
     * 收集节点 JSON 中全部引用指向的对象库块 uuid（供可达性判定）。
     * 无引用或引用无法定位时返回空集。
     */
    public static Set<UUID> referencedBlocks(JsonObject node) {
        StoredNodeType type = StoredNodeType.fromTag(node.getString("type"));
        Set<UUID> out = new HashSet<>();
        for (String field : FIELDS.getOrDefault(type, List.of())) {
            Ref ref = Ref.parse(node.get(field), FIELD_KIND.get(field));
            if (ref != null) {
                out.addAll(REGISTRY.referencedBlocks(ref));
            }
        }
        return out;
    }
}
