package com.flora.sanctum.model;

/**
 * 根概念 tag：最顶层对象的 parent 值（普通节点的 parent 是父对象 uuid，
 * 最顶层节点的 parent 是根概念 tag，见设计 05）。
 * <p>
 * 集合：manifest（引导根）、data（普通对象树）、icon、sshKey、remote。
 * 根概念不可动态增删；新增根需同步调整 VaultCreator/VaultUnlocker/Sanctum。
 */
public enum RootTag {

    MANIFEST("manifest"),
    DATA("data"),
    ICON("icon"),
    SSH_KEY("sshKey"),
    REMOTE("remote");

    private final String tag;

    RootTag(String tag) {
        this.tag = tag;
    }

    /** 概念字符串（对象 parent 字段中的实际值）。 */
    public String tag() {
        return tag;
    }

    /** 解析概念 tag；null 或非根概念返回 null。 */
    public static RootTag fromTag(String s) {
        if (s == null) {
            return null;
        }
        for (RootTag t : values()) {
            if (t.tag.equals(s)) {
                return t;
            }
        }
        return null;
    }

    /** parent 是否为根概念（而非父对象 uuid）。 */
    public static boolean isRoot(String parent) {
        return fromTag(parent) != null;
    }
}
