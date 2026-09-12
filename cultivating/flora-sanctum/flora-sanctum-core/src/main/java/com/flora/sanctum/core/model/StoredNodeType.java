package com.flora.sanctum.core.model;

/**
 * 存储节点类型（JSON 负载的 {@code type} 字段；见设计 05"数据结构树化"）。
 * <p>
 * 本枚举的值会持久化到存储块，不可随意增删（读端按字符串精确匹配）。
 */
public enum StoredNodeType {

    MANIFEST("manifest"),
    /** 顶层根 group（data/icon/sshKey 根，持 root DEK）；结构性根节点，不作为普通数据节点呈现。 */
    ROOT("root"),
    /** 普通文件夹。 */
    GROUP("group"),
    ENTRY("entry"),
    /**
     * 字段块（预设与自定义统一）。预设/自定义的语义由字段名是否在
     * {@link com.flora.sanctum.core.model.EntryFields#PRESET_NAMES} 区分；
     * 块负载字段：name/value/kind/parent。
     */
    FIELD("field"),
    /** 仓库级设置项（key/value，存 DATA 根下，不显示为普通对象）。 */
    CONFIG("config"),
    ICON("icon"),
    SSH_KEY("sshKey"),
    /** 远程配置（独立落盘类型，直接存 name/url/keyRef）。 */
    REMOTE("remote"),
    /**
     * 数据类分隔层节点（root 与顶级对象之间）。每类数据（password/icon/sshKey/remote）各一个，
     * 自身持独立 DEK 对、以其所属数据类的父级（root）为 parent；顶层对象改为挂到对应 category 下。
     * 结构性根节点（UI 不直接呈现，经各树 roots() 跳过）。
     */
    CATEGORY("category");

    private final String tag;

    StoredNodeType(String tag) {
        this.tag = tag;
    }

    /** 存储用字符串（JSON type 字段值）。 */
    public String tag() {
        return tag;
    }

    /** 解析存储 type 字符串；未知或 null 返回 null。 */
    public static StoredNodeType fromTag(String s) {
        if (s == null) {
            return null;
        }
        for (StoredNodeType t : values()) {
            if (t.tag.equals(s)) {
                return t;
            }
        }
        return null;
    }

    /** 是否为结构根（root 或 category 分隔层节点）：不暴露为普通数据节点、不参与 trash/移动等。 */
    public boolean isStructuralRoot() {
        return this == ROOT || this == CATEGORY;
    }
}
