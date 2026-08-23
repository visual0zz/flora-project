package com.flora.sanctum.model;

/**
 * 根概念 tag：仓库顶层引导标识。
 * <p>
 * 仅 DATA（唯一根对象，type=root，由 manifest 记录 uuid 定位）。manifest 为明文引导块，
 * 靠 type=manifest 识别，无需 parent tag。普通节点顶层 parent 一律指向根对象 uuid。
 */
public enum RootTag {

    /** 仓库唯一根对象（type=root，持 root DEK 与 repoKeyIdSeed）。 */
    DATA("data", "密码库");

    private final String tag;
    private final String displayName;

    RootTag(String tag, String displayName) {
        this.tag = tag;
        this.displayName = displayName;
    }

    /** 概念字符串（对象 parent 字段中的实际值）。 */
    public String tag() {
        return tag;
    }

    /** GUI 区段展示名（明文标签，独立于存储值）。 */
    public String displayName() {
        return displayName;
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

    /** parent 是否为根概念 tag（data）。 */
    public static boolean isRoot(String parent) {
        return fromTag(parent) != null;
    }
}
