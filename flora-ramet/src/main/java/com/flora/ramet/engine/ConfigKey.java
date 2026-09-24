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
     * <br>默认值: {@code false}（默认容错，null 输出为空串，保持既有行为）
     * <br>设为 {@code true} 时开启严格模式：{@code null} 即抛错，便于尽早暴露缺失数据/参数。
     */
    STRICT_NULL("strictNull");

    private final String key;

    ConfigKey(String key) {
        this.key = key;
    }

    /** 返回该配置项在 @Config 中使用的实际键字符串。 */
    public String key() {
        return key;
    }
}
