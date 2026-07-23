package com.flora.codegen;

/**
 * 模板 @Config 块的配置项名称常量。
 *
 * <p>每个枚举常量对应 {@code @Config{ ... }} 中的一个有效键。
 * 使用 {@link #key()} 获取实际字符串值，避免到处写硬编码字符串。
 */
public enum ConfigKey {

    /**
     * 是否在输出文件头部自动注入"此文件由模板生成"的警告注释。
     * <p>类型: {@code boolean}
     * <br>默认值: {@code true}
     */
    AUTO_WARNING("autoWarning");

    private final String key;

    ConfigKey(String key) {
        this.key = key;
    }

    /** 返回该配置项在 @Config 中使用的实际键字符串。 */
    public String key() {
        return key;
    }
}
