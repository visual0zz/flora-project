package com.flora.sanctum.model;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;

import java.util.Objects;
import java.util.UUID;

/**
 * 统一引用表示：把 icon / keyRef 等"指向另一个数据节点或内置资源"的引用收口为单一结构。
 * <p>
 * {@code type} 为 {@code "<scheme>:<kind>"} 复合串：
 * <ul>
 *   <li>{@code node}：引用仓库内的一个数据节点，{@code kind} 等于被引用节点的 {@code type} 标签
 *       （icon→{@code ICON}，key→{@code SSH_KEY}，受 {@link StoredNodeType} 约束）；{@code id} 为该节点 uuid。</li>
 *   <li>{@code builtin}：引用应用内置资源（不进对象库），{@code kind} 为资源类别（目前仅 icon），
 *       {@code id} 为资源名（如 folder）。</li>
 * </ul>
 * 解析器按 {@code scheme} 前缀注册（{@code node}/{@code builtin}），负责把引用映射到对象库中的目标块
 * （供 GC 可达性判定），其语义见 {@code model.ref} 包。
 */
public final class Ref {

    private final String type;
    private final String id;
    private final String scheme;
    private final String kind;

    public Ref(String type, String id) {
        int i = type.indexOf(':');
        if (i <= 0 || i == type.length() - 1 || type.indexOf(':', i + 1) >= 0) {
            throw new IllegalArgumentException("type must be 'scheme:kind': " + type);
        }
        this.scheme = type.substring(0, i);
        this.kind = type.substring(i + 1);
        this.type = type;
        this.id = Objects.requireNonNull(id, "id");
    }

    /** node 引用（id 为被引用节点 uuid）。 */
    public static Ref node(String kind, UUID uuid) {
        return new Ref("node:" + kind, uuid.toString());
    }

    /** 仓库内图标节点引用（node:icon）。 */
    public static Ref nodeIcon(UUID uuid) {
        return node("icon", uuid);
    }

    /** 仓库内密钥节点引用（node:key；被引用节点的存储 type 标签为 sshKey）。 */
    public static Ref nodeKey(UUID uuid) {
        return node("key", uuid);
    }

    /** 内置图标引用（builtin:icon）。 */
    public static Ref builtinIcon(String name) {
        return new Ref("builtin:icon", name);
    }

    /**
     * 由遗留字符串构造：{@code "builtin:name"} → builtin:icon/name；其余视为 node:icon 的 uuid。
     * 用于 GUI 旧选择回调（仍产出 {@code "builtin:name"} 或 uuid 串）桥接到 Ref。
     */
    public static Ref fromLegacyId(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("builtin:")) {
            return new Ref("builtin:icon", id.substring("builtin:".length()));
        }
        return new Ref("node:icon", id);
    }

    /**
     * 解析存储值：新格式为含 {@code type}/{@code id} 的 JSON 对象；遗留格式为 {@code "builtin:name"}
     * 或 uuid 字符串（{@code defaultKind} 决定 node 种类，iconRef 字段用 "icon"、keyRef 字段用 "key"）。
     */
    public static Ref parse(JsonValue raw, String defaultKind) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        if (raw.isObject()) {
            JsonObject jo = raw.asObject();
            String t = jo.getString("type");
            String i = jo.getString("id");
            if (t != null && i != null) {
                return new Ref(t, i);
            }
            return null;
        }
        if (raw.isString()) {
            String s = raw.asString();
            if (s.startsWith("builtin:")) {
                return new Ref("builtin:icon", s.substring("builtin:".length()));
            }
            return new Ref("node:" + defaultKind, s);
        }
        return null;
    }

    /** 转遗留字符串（用于 UI 兼容显示）：builtin→"builtin:name"，node→uuid 串。 */
    public String legacyId() {
        return "builtin".equals(scheme) ? "builtin:" + id : id;
    }

    /** 序列化为存储对象（含 type/id 两字段）。 */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("type", type);
        o.put("id", id);
        return o;
    }

    public String type() {
        return type;
    }

    public String id() {
        return id;
    }

    public String scheme() {
        return scheme;
    }

    public String kind() {
        return kind;
    }

    /** node 引用的目标 uuid（非 node 方案抛 IllegalArgumentException）。 */
    public UUID nodeUuid() {
        if (!"node".equals(scheme)) {
            throw new IllegalStateException("ref is not a node ref: " + type);
        }
        return UUID.fromString(id);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Ref r)) {
            return false;
        }
        return type.equals(r.type) && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return "Ref[" + type + "," + id + "]";
    }
}
