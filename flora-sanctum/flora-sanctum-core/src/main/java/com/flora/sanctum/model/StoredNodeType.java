package com.flora.sanctum.model;

import java.util.Optional;

/**
 * 存储节点类型（JSON 负载的 {@code type} 字段；见设计 05"数据结构树化"）。
 * <p>
 * 与纯 UI 展示概念（{@link ViewNodeType}）解耦：每个存储类型声明自己的展示归属
 * {@link #view()}，即"存储节点内部字段指定其展示节点是什么"。本枚举的值会持久化到
 * 存储块，不可随意增删（旧库兼容）。
 */
public enum StoredNodeType {

    MANIFEST("manifest", ViewNodeType.SETTINGS),
    /** 顶层根 group（data/icon/sshKey 根，持 root DEK），无展示归属。 */
    ROOT("root", null),
    /** 普通文件夹。 */
    GROUP("group", ViewNodeType.PASSWORD),
    ENTRY("entry", ViewNodeType.PASSWORD),
    FIELD("field", ViewNodeType.PASSWORD),
    /**
     * 自定义字段（kind 可为 null）。
     * 与 {@link #FIELD} 的负载字段完全相同（name/value/kind/parent），但刻意分两种 type：
     * 预设字段由系统创建/管理（条目元数据），自定义字段由用户创建；按 type 直接过滤对
     * GC 可达性判定与遍历（仅自定义字段、仅预设字段）更方便，无需依赖 name 是否在预设集合。
     */
    CUSTOM_FIELD("customField", ViewNodeType.PASSWORD),
    /** 仓库级设置项（key/value，存 DATA 根下，不显示为普通对象）。 */
    CONFIG("config", ViewNodeType.SETTINGS),
    ICON("icon", ViewNodeType.ICON),
    SSH_KEY("sshKey", ViewNodeType.SSH_KEY),
    /** 远程配置（独立落盘类型，直接存 name/url/keyRef）。 */
    REMOTE("remote", ViewNodeType.REMOTE);

    private final String tag;
    private final ViewNodeType view;

    StoredNodeType(String tag, ViewNodeType view) {
        this.tag = tag;
        this.view = view;
    }

    /** 存储用字符串（JSON type 字段值）。 */
    public String tag() {
        return tag;
    }

    /** 该存储类型的展示归属（ROOT 等无展示归属返回 empty）。 */
    public Optional<ViewNodeType> view() {
        return Optional.ofNullable(view);
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
}
