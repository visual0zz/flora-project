package com.flora.sanctum.core.model;

import com.flora.sanctum.core.model.impl.TreeContext;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 垃圾桶视图（见设计 idea20260826-sanctum-trash）。
 * <p>持有三类异常节点 uuid 集合（手动删除 / 不可达 / 不可解锁），并提供「原位置」路径的临时计算
 * （沿 parent 链回溯到根对象，拼出如 "密码库/A/被删组" 的可读路径；不持久化）。</p>
 */
public final class TrashView {

    private final List<UUID> manual;
    private final List<UUID> unreachable;
    private final List<UUID> unlockable;
    private final TreeContext ctx;
    private final String root;

    TrashView(List<UUID> manual, List<UUID> unreachable, List<UUID> unlockable,
              TreeContext ctx, String root) {
        this.manual = manual;
        this.unreachable = unreachable;
        this.unlockable = unlockable;
        this.ctx = ctx;
        this.root = root;
    }

    /** 手动删除节点 uuid（JSON "deleted":true）。 */
    public List<UUID> manual() {
        return manual;
    }

    /** 不可达节点 uuid（parent 链指向缺失对象）。 */
    public List<UUID> unreachable() {
        return unreachable;
    }

    /** 不可解锁节点 uuid（祖先 group DEK 未解开）。 */
    public List<UUID> unlockable() {
        return unlockable;
    }

    /** 某 uuid 是否落在任一类垃圾桶集合中。 */
    public boolean contains(UUID uuid) {
        return manual.contains(uuid) || unreachable.contains(uuid) || unlockable.contains(uuid);
    }

    /** 节点所属垃圾桶类别（null 表示不在垃圾桶）。 */
    public TrashKind kindOf(UUID uuid) {
        if (manual.contains(uuid)) {
            return TrashKind.MANUAL;
        }
        if (unreachable.contains(uuid)) {
            return TrashKind.UNREACHABLE;
        }
        if (unlockable.contains(uuid)) {
            return TrashKind.UNLOCKABLE;
        }
        return null;
    }

    /** 三类垃圾桶类别（展示名由 app 侧提供，core 仅作分类）。 */
    public enum TrashKind {
        MANUAL,
        UNREACHABLE,
        UNLOCKABLE
    }

    /**
     * 临时计算「原位置」路径段（从根对象到该节点，根在前）。不持久化、不含任何展示前缀；
     * 链路中名称缺失或空白的节点以其 uuid 前 8 位作中性占位。展示前缀（如 "密码库/"）与兜底
     * 文案由 app 侧负责拼接。
     */
    public List<String> originalPathSegments(UUID uuid) {
        List<String> segments = new ArrayList<>();
        String cur = uuid.toString();
        Set<String> seen = new HashSet<>();
        while (cur != null && !cur.equals(root) && seen.add(cur)) {
            if (!isUuid(cur)) {
                break;
            }
            UUID id = com.flora.sanctum.core.util.UuidHex.fromHex(cur);
            JsonObject d = ctx.read(id);
            if (d == null) {
                segments.add(0, cur.substring(0, Math.min(8, cur.length())));
                break;
            }
            String name = d.getString("name");
            segments.add(0, name == null || name.isBlank()
                    ? cur.substring(0, Math.min(8, cur.length())) : name);
            cur = d.getString("parent");
        }
        return segments;
    }

    private static boolean isUuid(String s) {
        try {
            com.flora.sanctum.core.util.UuidHex.fromHex(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
