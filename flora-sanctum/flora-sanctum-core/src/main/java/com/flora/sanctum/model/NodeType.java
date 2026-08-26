package com.flora.sanctum.model;

/**
 * 节点类型（对象 JSON 的 {@code type} 字段；见设计 05"数据结构树化"）。
 * <p>
 * 存储值即枚举 tag；remote 也是独立存储 type（不再复用 field+kind）。
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
    /** 仓库级设置项（key/value，存 DATA 根下，不显示为普通对象）。 */
    CONFIG("config"),
    ICON("icon"),
    SSH_KEY("sshKey"),
    /** 远程配置（独立落盘类型，直接存 name/url/keyRef）。 */
    REMOTE("remote"),
    /** 垃圾桶虚拟区段标记（纯 UI 展示，不对应任何真实存储对象/根概念）。 */
    TRASH("trash");

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
