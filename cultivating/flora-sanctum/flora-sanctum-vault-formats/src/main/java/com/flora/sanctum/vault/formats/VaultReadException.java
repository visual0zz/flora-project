package com.flora.sanctum.vault.formats;

/**
 * 第三方保险库读取失败的统一异常（结构化，携带阶段与格式，均为非敏感信息）。
 * <p>与 {@code KdbxReadException} 对齐：上层可按 {@link Stage} 区分失败环节，
 * 但本异常不暴露密码、明文等敏感内容。</p>
 */
public final class VaultReadException extends Exception {

    /** 读取失败的阶段（用于定位是识别、密钥派生还是解密/解析出错）。 */
    public enum Stage {
        MAGIC, HEADER, KDF, DECRYPT, INNER, XML, STRUCTURE, UNSUPPORTED
    }

    private final Stage stage;
    private final VaultFormat format;

    public VaultReadException(Stage stage, VaultFormat format, String message) {
        this(stage, format, message, null);
    }

    public VaultReadException(Stage stage, VaultFormat format, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.format = format;
    }

    public Stage stage() {
        return stage;
    }

    public VaultFormat format() {
        return format;
    }

    /** 便于上层做结构化诊断的便捷构造。 */
    public static VaultReadException of(Stage stage, VaultFormat format, String message) {
        return new VaultReadException(stage, format, message);
    }

    public static VaultReadException of(Stage stage, VaultFormat format, String message, Throwable cause) {
        return new VaultReadException(stage, format, message, cause);
    }
}
