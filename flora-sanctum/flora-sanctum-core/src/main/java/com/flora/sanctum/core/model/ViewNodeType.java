package com.flora.sanctum.core.model;

/**
 * 展示节点类型（纯 UI 区段 / 虚拟根标记，不持久化、不对应任何存储对象）。
 * <p>
 * 与存储节点类型（{@link StoredNodeType}）解耦；左树区段、垃圾桶虚拟根等展示概念
 * 以此为 userObject。存储类型经 {@link StoredNodeType#view()} 指向其展示归属。
 */
public enum ViewNodeType {
    /** 密码库区段（group/entry/field 的展示归属）。 */
    PASSWORD,
    ICON,
    SSH_KEY,
    REMOTE,
    /** 仓库配置区段（自动锁定/剪贴板清空等，存仓库内加密 config 节点）。 */
    SETTINGS,
    /**
     * 全局配置区段（界面主题等，存全局配置文件：应用级 ~/.flora-sanctum/config.json
     * 或独立仓库级 config.json）。
     */
    GLOBAL,
    /** 垃圾桶虚拟根（与数据根平级，见设计 idea20260826-sanctum-trash）。 */
    TRASH,
    /**
     * 外部密钥虚拟根（与数据根平级，只读聚合展示所有 {@code kind:"externalKey"} 字段；
     * 见需求：主界面展示用虚拟顶层节点，展示实际存储路径 + 脱敏密钥）。
     */
    EXTERNAL_KEY,
    /**
     * 动态码虚拟根（与数据根平级，只读聚合展示所有 {@code kind:"totp"} 字段；
     * 中间条目列表动态刷新每条的动态码，选中后右侧只读展示所属条目路径等信息）。
     */
    TOTP;
}
