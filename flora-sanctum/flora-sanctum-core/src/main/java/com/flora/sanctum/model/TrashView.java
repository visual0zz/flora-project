package com.flora.sanctum.model;

import com.flora.sanctum.model.impl.TreeContext;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** 三类垃圾桶类别。 */
    public enum TrashKind {
        MANUAL("手动删除"),
        UNREACHABLE("不可达"),
        UNLOCKABLE("不可解锁");

        private final String label;

        TrashKind(String label) {
            this.label = label;
        }

        /** 中文展示名。 */
        public String label() {
            return label;
        }
    }

    /**
     * 临时计算「原位置」路径：沿 parent 链回溯至根对象，拼出 "密码库/..." 形式。
     * 链路中存在未知节点时以 "未知(uuid前8位)" 占位；无法回溯返回空串。
     */
    public String originalPath(UUID uuid) {
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<UUID, JsonObject> e : ctx.objects().entrySet()) {
            JsonObject d = e.getValue();
            if (d == null) {
                continue;
            }
            String name = d.getString("name");
            names.put(e.getKey().toString(), name == null || name.isBlank() ? "未命名" : name);
        }
        List<String> parts = new ArrayList<>();
        String cur = uuid.toString();
        Set<String> seen = new HashSet<>();
        while (cur != null && !cur.equals(root) && seen.add(cur)) {
            if (!isUuid(cur)) {
                break;
            }
            UUID id = UUID.fromString(cur);
            JsonObject d = ctx.read(id);
            if (d == null) {
                parts.add(0, "未知(" + cur.substring(0, Math.min(8, cur.length())) + ")");
                break;
            }
            String name = d.getString("name");
            parts.add(0, name == null || name.isBlank() ? "未命名" : name);
            cur = d.getString("parent");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "密码库/" + String.join("/", parts);
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
