package com.flora.sanctum.model;

/**
 * 自定义字段 kind 的已知集合（见设计 05"自定义字段统一格式"）。
 * <p>
 * 存储仍是字符串（未知 kind 原样保留，向后兼容），enum 仅作为已知 kind 的规范
 * 与 GUI 下拉列表数据源。判断未知 kind 用 {@link #fromTag(String)} 返回 null。
 */
public enum FieldKind {

    TEXT("text"),
    PASSWORD("password"),
    TOTP("totp"),
    URL("url"),
    EXTERNAL_KEY("externalKey"),
    REMOTE("remote");

    private final String tag;

    FieldKind(String tag) {
        this.tag = tag;
    }

    /** kind 存储值（field 负载中的字符串）。 */
    public String tag() {
        return tag;
    }

    /** 解析已知 kind；未知/未定义/null 返回 null（不丢原字符串，见类注释）。 */
    public static FieldKind fromTag(String s) {
        if (s == null) {
            return null;
        }
        for (FieldKind k : values()) {
            if (k.tag.equals(s)) {
                return k;
            }
        }
        return null;
    }
}
