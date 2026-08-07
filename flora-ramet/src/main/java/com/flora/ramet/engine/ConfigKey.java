package com.flora.ramet.engine;

/**
 * 模板 @Config 块的配置项名称常量。
 *
 * <p>每个枚举常量对应 {@code @Config{ ... }} 中的一个有效键。
 * 使用 {@link #key()} 获取实际字符串值，避免到处写硬编码字符串。
 */
public enum ConfigKey {

    /**
     * 是否在输出文件头部自动注入“此文件由模板生成”的警告注释。
     * <p>类型: {@code boolean}
     * <br>默认值: {@code true}
     */
    AUTO_WARNING("autoWarning"),

    /**
     * 是否启用严格的 null 求值：当 {@code ${表达式}} 求值为 {@code null} 时直接抛错。
     * <p>类型: {@code boolean}
     * <br>默认值: {@code true}（默认严格，null 即报错）
     * <br>设为 {@code false} 时恢复为容错行为：{@code null} 输出为空串。
     */
    STRICT_NULL("strictNull"),

    /**
     * 输出转义方案：对渲染后的「最终输出」整体按指定方案转义。
     * <p>类型: {@code String}
     * <br>支持值: {@code html} / {@code xml} / {@code js} / {@code none}
     * <br>默认值: 不设置（即不转义）。显式设为 {@code none} 也等同于不转义。
     * <br>该转义作用于模板渲染产物，警告注释在转义之后注入，不会被二次转义。
     */
    ESCAPE("escape");

    private final String key;

    ConfigKey(String key) {
        this.key = key;
    }

    /** 返回该配置项在 @Config 中使用的实际键字符串。 */
    public String key() {
        return key;
    }
}
