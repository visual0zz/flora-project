package com.flora.sanctum.core.io.exporter;

/**
 * 导出失败异常：格式不合法、磁盘写入错误等。UI 捕获后以对话框展示 {@link #getMessage()}。
 */
public class ExportException extends Exception {

    public ExportException(String message) {
        super(message);
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
