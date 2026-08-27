package com.flora.sanctum.model.ref;

import com.flora.sanctum.model.Ref;
import com.flora.sanctum.model.StoredNodeType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * node 方案解析器：引用仓库内的一个数据节点。
 * <p>
 * {@link Ref#kind()} 必须等于被引用节点的 {@code type} 标签（{@link StoredNodeType}）；
 * 这里用一张受控的 kind→节点类型映射表表达该约束：icon 与节点标签恒等（ICON），
 * key 为 SSH_KEY 的别名（节点持久化标签是 sshKey，枚举不可改，故在引用层做一层别名）。
 * <p>
 * GC 可达性只需 id（被引用节点 uuid）；kind→类型的一致性校验属于独立关注点，
 * 留待物化/一致性检查阶段加载节点验证，此处不加载存储块，保持解析无 IO、健壮。
 */
public final class NodeRefResolver implements RefResolver {

    private static final Map<String, StoredNodeType> KIND = Map.of(
            "icon", StoredNodeType.ICON,
            "key", StoredNodeType.SSH_KEY);

    @Override
    public String scheme() {
        return "node";
    }

    @Override
    public Set<UUID> referencedBlocks(Ref ref) {
        // id 必须为被引用节点 uuid；遗留的名字引用（非 uuid）无法定位块，返回空集，不影响 GC 健壮性
        try {
            return Set.of(UUID.fromString(ref.id()));
        } catch (IllegalArgumentException e) {
            return Set.of();
        }
    }

    /** 该引用 kind 对应的节点存储类型（供一致性校验/物化使用；未知 kind 返回 null）。 */
    public static StoredNodeType expectedType(Ref ref) {
        return KIND.get(ref.kind());
    }
}
