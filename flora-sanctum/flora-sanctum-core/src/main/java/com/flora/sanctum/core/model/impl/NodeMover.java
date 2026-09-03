package com.flora.sanctum.core.model.impl;

import com.flora.sanctum.core.model.StoredNodeType;
import com.flora.sanctum.core.model.vault.Vault;
import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 改变节点归属（reparent）：把组或条目移动到新父之下，并正确重路由其加密归属。
 * <p>
 * 解密只看块头内嵌的 keyId（绑定加密时所用 DEK），与逻辑 parent 无关。因此移动后必须让
 * "加密所用 DEK" 与 "新父" 重新一致，否则在旧父 DEK 离开 KeyIdIndex（如旧父被删并 GC）后，
 * relock 再解锁时该子树会落入"不可解锁"。具体重加密范围：
 * <ul>
 *   <li>移动组：仅该组自身对象块需改用新父 DEK 重加密（其内嵌 dek 字段存明文 DEK，值不变）；
 *       组块整体用新父 DEK 加密后，组 DEK 即可在新父链下被登记。子孙（子组/条目/字段）
 *       的加密 DEK 基于本组 DEK（或其子组 DEK），移动前后不变，无需重加密。</li>
 *   <li>移动条目：条目块与它的全部字段块都用"所属组 DEK"加密，移动后该 DEK 变了，
 *       须把条目块 + 所有字段块都重加密到新组 DEK 之下（字段 parent 仍指向条目）。</li>
 * </ul>
 * 另含环检测：新父不能是自身或其子孙。
 */
public final class NodeMover {

    private final TreeContext ctx;
    private final Vault vault;

    public NodeMover(TreeContext ctx, Vault vault) {
        this.ctx = ctx;
        this.vault = vault;
    }

    /** 改变节点归属（按节点类型分派到组/条目搬运）。beforeUuid=null 时为追加末尾（原 move 语义）。 */
    public void move(UUID node, UUID newParent) {
        moveTo(node, newParent, null);
    }

    /**
     * 改变节点归属并定位顺序（小数索引）。
     * <p>beforeUuid=null → 追加到新父末尾（initialOrder = max + D）；
     * 否则插入到 beforeUuid 之前（取前驱与 beforeUuid 的中点；间隙耗尽则整段重排后重算）。
     * 同父内纯重排只改写被移动那一个块的 order，数据修改范围最小。</p>
     */
    public void moveTo(UUID node, UUID newParent, UUID beforeUuid) {
        StoredNodeType type = typeOf(node);
        if (type == StoredNodeType.GROUP) {
            moveGroup(node, newParent, beforeUuid);
        } else if (type == StoredNodeType.ENTRY) {
            moveEntry(node, newParent, beforeUuid);
        } else {
            throw new IllegalArgumentException("仅支持移动组或条目，实际类型：" + type);
        }
    }

    private void moveGroup(UUID groupUuid, UUID newParent, UUID beforeUuid) {
        checkNoCycle(groupUuid, newParent);
        UUID oldParent = ctx.parentUuidOf(groupUuid);
        JsonObject g = ctx.read(groupUuid);
        if (g == null) {
            throw new IllegalArgumentException("组不存在：" + groupUuid);
        }
        if (newParent != null && typeOf(newParent) != StoredNodeType.GROUP) {
            throw new IllegalArgumentException("组的父必须是组或根");
        }
        Vault.GroupKeys keys = vault.groupKeys(groupUuid);
        if (keys == null) {
            throw new IllegalStateException("组 DEK 尚未就绪（未解锁或已锁定）");
        }
        // 组块整体改用新父 DEK 重加密（外层保护）；dek1/dek2 直接存明文 base64，无需重新包裹
        g.put("parent", parentStr(newParent));
        g.put("dek1", Base64.getEncoder().encodeToString(keys.dek1()));
        g.put("dek2", Base64.getEncoder().encodeToString(keys.dek2()));
        g.put("order", computeOrder(newParent, beforeUuid, groupUuid));
        g.remove("dek");
        ctx.write(groupUuid, g, newParent);
        // 旧父失去本组这一子节点，其退役 dek1 使用数可能下降 → 尝试轮换
        if (oldParent != null) {
            ctx.maybeRotateGroupKeys(oldParent);
        }
    }

    private void moveEntry(UUID entryUuid, UUID newParentGroup, UUID beforeUuid) {
        if (newParentGroup == null) {
            throw new IllegalArgumentException("条目必须移动到某个组内");
        }
        StoredNodeType parentType = typeOf(newParentGroup);
        // 允许落到组内，或落到密码库根（顶层条目 parent 即根对象，加密走 rootDek）
        if (parentType != StoredNodeType.GROUP && parentType != StoredNodeType.ROOT) {
            throw new IllegalArgumentException("条目只能移动到组内或密码库根");
        }
        JsonObject e = ctx.read(entryUuid);
        if (e == null) {
            throw new IllegalArgumentException("条目不存在：" + entryUuid);
        }
        UUID oldParentGroup = ctx.parentGroupUuid(e);
        e.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(newParentGroup));
        e.put("order", computeOrder(newParentGroup, beforeUuid, entryUuid));
        ctx.write(entryUuid, e, newParentGroup);
        // 字段块随条目改归属到新组 DEK 之下重加密（field.parent 仍指向条目，不变）
        for (UUID f : ctx.childrenOf(entryUuid)) {
            JsonObject field = ctx.read(f);
            if (field != null) {
                ctx.write(f, field, newParentGroup);
            }
        }
        // 旧父组失去本条目这一子节点，其退役 dek1 使用数可能下降 → 尝试轮换
        if (oldParentGroup != null) {
            ctx.maybeRotateGroupKeys(oldParentGroup);
        }
    }

    /**
     * 计算被移动节点在新父下的 order：beforeUuid=null 追加末尾（溢出则先重排）；
     * 否则取前驱与 beforeUuid 中点，间隙耗尽则整段重排后重算。
     * 自身若已在目标父下，重排前先排除。
     */
    private long computeOrder(UUID newParent, UUID beforeUuid, UUID self) {
        if (beforeUuid == null) {
            return ctx.appendOrder(newParent);
        }
        if (beforeUuid.equals(self)) {
            return ctx.orderOf(self); // 拖到自身之前：保持原位
        }
        List<UUID> sibs = new ArrayList<>(ctx.childrenOf(newParent));
        sibs.remove(self);
        sibs.sort((a, b) -> Long.compare(ctx.orderOf(a), ctx.orderOf(b)));
        int idx = sibs.indexOf(beforeUuid);
        if (idx < 0) {
            return ctx.appendOrder(newParent);
        }
        long nextOrder = ctx.orderOf(beforeUuid);
        long prevOrder = (idx == 0) ? 0L : ctx.orderOf(sibs.get(idx - 1));
        if (FractionalIndex.collapsed(prevOrder, nextOrder)) {
            ctx.reassignOrders(newParent);
            // 重排后子列表 order 为 {(i+1)*D}，beforeUuid 位于 idx，故前后邻居即 idx*D 与 (idx+1)*D
            prevOrder = idx * FractionalIndex.D;
            nextOrder = (idx + 1L) * FractionalIndex.D;
        }
        return FractionalIndex.between(prevOrder, nextOrder);
    }

    /** 环检测：newParent 不能是 moved 自身或其后代（沿父链向上会经过 moved 即冲突）。 */
    private void checkNoCycle(UUID moved, UUID newParent) {
        for (UUID p = newParent; p != null; p = ctx.parentUuidOf(p)) {
            if (p.equals(moved)) {
                throw new IllegalArgumentException("不能移动到自身或其子孙之下");
            }
        }
    }

    private String parentStr(UUID newParent) {
        return com.flora.sanctum.core.util.UuidHex.toHex(newParent == null ? vault.rootObjectUuid() : newParent);
    }

    private StoredNodeType typeOf(UUID uuid) {
        JsonObject obj = ctx.read(uuid);
        return obj == null ? null : StoredNodeType.fromTag(obj.getString("type"));
    }
}
