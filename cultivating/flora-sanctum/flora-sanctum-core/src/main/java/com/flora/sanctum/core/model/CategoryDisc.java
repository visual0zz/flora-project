package com.flora.sanctum.core.model;

/**
 * 数据类分隔层的判别符（category 层，见设计"category 分隔层"）。
 * <p>
 * 每个取值对应一个 category 节点：其 uuid 登记于根对象 {@code categories} 映射、节点的 {@code category} 字段，
 * 且为该类数据的顶层父与加密归属组。字符串值会持久化到存储块，不可随意改动（持久化判别符）。
 * 集中在此枚举，避免 "password"/"icon"/"sshKey"/"remote"/"config" 作为裸字符串散落多处导致的静默路由错误。
 */
public enum CategoryDisc {

    PASSWORD("password"),
    ICON("icon"),
    SSH_KEY("sshKey"),
    REMOTE("remote"),
    CONFIG("config");

    private final String tag;

    CategoryDisc(String tag) {
        this.tag = tag;
    }

    /** 存储/路由用判别符字符串（JSON 负载 {@code category} 字段、根 {@code categories} 映射键）。 */
    public String tag() {
        return tag;
    }

    /** 解析判别符字符串；未知或 null 返回 null。 */
    public static CategoryDisc fromTag(String s) {
        if (s == null) {
            return null;
        }
        for (CategoryDisc d : values()) {
            if (d.tag.equals(s)) {
                return d;
            }
        }
        return null;
    }
}
