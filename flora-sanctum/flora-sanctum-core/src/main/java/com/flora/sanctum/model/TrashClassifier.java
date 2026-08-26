package com.flora.sanctum.model;

import com.flora.sanctum.model.impl.GarbageCollector;
import com.flora.sanctum.model.impl.TreeContext;
import com.flora.sanctum.model.vault.Vault;
import com.flora.sanctum.store.Block;
import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 垃圾桶分类：从存储扫描结果中识别三类异常节点（见设计 idea20260826-sanctum-trash）。
 * <ul>
 *   <li>不可解锁：节点对应 group 的 DEK 未在解锁阶段解开（块本身或祖先 DEK 不可解）。</li>
 *   <li>不可达：块可解密，但 parent 链指向缺失 uuid（孤儿），无法挂入正常树。</li>
 *   <li>手动删除：节点 JSON 含 "deleted":true 标记（见 TreeNode.markDeleted）。</li>
 * </ul>
 * 仅报告不删除（不改动 {@link GarbageCollector} 的物理行为）。
 */
public final class TrashClassifier {

    private final TreeContext ctx;

    public TrashClassifier(TreeContext ctx) {
        this.ctx = ctx;
    }

    /** 扫描并分类，返回垃圾桶视图（含三类 uuid 集合与「原位置」路径计算）。 */
    public TrashView classify() {
        Vault vault = ctx.vault();
        java.util.UUID rootUuid = vault.manifest() == null ? null : vault.manifest().rootGroupUuid();
        String root = rootUuid == null ? null : rootUuid.toString();

        // 可达集（复用 GarbageCollector 思想，仅判不删）
        Set<UUID> reachable = new HashSet<>();
        List<Block> blocks = ctx.store().scan();
        for (Block b : blocks) {
            if (b.isPlaintext() || (rootUuid != null && rootUuid.equals(b.uuid()))) {
                reachable.add(b.uuid());
            }
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Block b : blocks) {
                if (reachable.contains(b.uuid())) {
                    continue;
                }
                JsonObject n = nodeOf(b);
                if (n == null) {
                    continue;
                }
                String parent = n.getString("parent");
                String icon = n.getString("icon");
                String keyRef = n.getString("keyRef");
                if ((parent != null && isUuid(parent) && reachable.contains(UUID.fromString(parent)))
                        || (icon != null && isUuid(icon) && reachable.contains(UUID.fromString(icon)))
                        || (keyRef != null && isUuid(keyRef) && reachable.contains(UUID.fromString(keyRef)))) {
                    reachable.add(b.uuid());
                    progress = true;
                }
            }
        }

        List<UUID> manual = new ArrayList<>();
        List<UUID> unreachable = new ArrayList<>();
        List<UUID> unlockable = new ArrayList<>();

        for (Map.Entry<UUID, JsonObject> e : ctx.objects().entrySet()) {
            UUID uuid = e.getKey();
            JsonObject d = e.getValue();
            StoredNodeType nt = StoredNodeType.fromTag(d.getString("type"));
            if (nt == StoredNodeType.ROOT || nt == StoredNodeType.MANIFEST || nt == StoredNodeType.CONFIG) {
                continue;
            }
            if (Boolean.TRUE.equals(d.getBool("deleted"))) {
                manual.add(uuid);
                continue;
            }
            // 不可解锁：祖先组 DEK 未解开
            UUID group = ancestorGroupUuid(d);
            if (group != null && vault.groupDek(group) == null) {
                unlockable.add(uuid);
                continue;
            }
            // 不可达：parent 不为根对象且指向缺失 uuid
            String parent = d.getString("parent");
            if (parent != null && !parent.equals(root)
                    && (!isUuid(parent) || !reachable.contains(UUID.fromString(parent)))) {
                unreachable.add(uuid);
            }
        }

        return new TrashView(manual, unreachable, unlockable, ctx, root);
    }

    /** 沿 parent 链找到归属的 group uuid（ROOT 之上的真实 group）。 */
    private UUID ancestorGroupUuid(JsonObject d) {
        String p = d.getString("parent");
        Set<String> seen = new HashSet<>();
        while (p != null && isUuid(p) && seen.add(p)) {
            UUID pid = UUID.fromString(p);
            JsonObject parent = ctx.read(pid);
            if (parent == null) {
                return null;
            }
            StoredNodeType pt = StoredNodeType.fromTag(parent.getString("type"));
            if (pt == StoredNodeType.GROUP) {
                return pid;
            }
            p = parent.getString("parent");
        }
        return null;
    }

    private JsonObject nodeOf(Block b) {
        byte[] plain = ctx.vault().resolve(b.obfuscated(), b.timestampText());
        if (plain == null) {
            return null;
        }
        try {
            return JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
