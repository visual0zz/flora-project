package com.flora.sanctum.model;

/**
 * 节点类型（对象 JSON 的 {@code type} 字段；见设计 05"数据结构树化"）。
 * <p>
 * 存储值即枚举 tag；remote 特殊：存储为 {@code type=field, kind=remote}（见 RemoteTree），
 * 枚举中的 REMOTE 是语义类型（{@code RemoteNode.type()} 返回），不直接落盘为 type。
 * 新增根概念需同步调整 VaultCreator / VaultUnlocker / Sanctum。
 */
public enum NodeType {

    MANIFEST("manifest"),
    /** 顶层根 group（data/icon/sshKey 根，持 root DEK）。 */
    ROOT("root"),
    /** 普通文件夹。 */
    GROUP("group"),
    ENTRY("entry"),
    FIELD("field"),
    /** 自定义字段（kind 可为 null）。 */
    CUSTOM_FIELD("customField"),
    ICON("icon"),
    SSH_KEY("sshKey"),
    /** 语义类型：存储为 field + kind=remote。 */
    REMOTE("remote");

    private final String tag;

    NodeType(String tag) {
        this.tag = tag;
    }

    /** 存储用字符串（JSON type 字段值）。 */
    public String tag() {
        return tag;
    }

    /** 解析存储 type 字符串；未知或 null 返回 null。 */
    public static NodeType fromTag(String s) {
        if (s == null) {
            return null;
        }
        for (NodeType t : values()) {
            if (t.tag.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
