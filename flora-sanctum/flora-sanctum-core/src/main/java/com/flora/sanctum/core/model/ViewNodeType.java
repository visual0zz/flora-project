package com.flora.sanctum.core.model;

/**
 * 展示节点类型（纯 UI 区段 / 虚拟根标记，不持久化、不对应任何存储对象）。
 * <p>
 * 与存储节点类型（{@link StoredNodeType}）解耦；左树区段、垃圾桶虚拟根等展示概念
 * 以此为 userObject。存储类型经 {@link StoredNodeType#view()} 指向其展示归属。
 */
public enum ViewNodeType {
    /** 密码库区段（group/entry/field/customField 的展示归属）。 */
    PASSWORD("密码库"),
    ICON("图标"),
    SSH_KEY("SSH 密钥"),
    REMOTE("远程"),
    /** 仓库配置区段（自动锁定/剪贴板清空等，存仓库内加密 config 节点）。 */
    SETTINGS("仓库配置"),
    /**
     * 全局配置区段（界面主题等，存全局配置文件：应用级 ~/.flora-sanctum/config.json
     * 或独立仓库级 standalone.json）。
     */
    GLOBAL("全局配置"),
    /** 垃圾桶虚拟根（与数据根平级，见设计 idea20260826-sanctum-trash）。 */
    TRASH("垃圾桶");

    private final String displayName;

    ViewNodeType(String displayName) {
        this.displayName = displayName;
    }

    /** 区段展示名。 */
    public String displayName() {
        return displayName;
    }
}
