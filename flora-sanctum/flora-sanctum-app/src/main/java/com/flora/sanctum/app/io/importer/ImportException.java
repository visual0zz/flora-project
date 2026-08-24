package com.flora.sanctum.app.io.importer;

/**
 * 导入失败（文件损坏、不支持的版本、主密码错误、格式不兼容等）。
 * <p>由具体格式解析器抛出，UI 层捕获后向用户给出可读提示。</p>
 */
public class ImportException extends Exception {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 主密码/密钥文件错误（可提示用户重试）。 */
    public boolean isAuthFailure() {
        return getMessage() != null
                && (getMessage().contains("主密码") || getMessage().contains("密钥"));
    }
}
