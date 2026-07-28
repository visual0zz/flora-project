package com.flora.os.virtual.file;

/**
 * 虚拟文件元数据记录。
 * <p>单次 backend 调用返回全部元数据，避免多次查询。</p>
 */
public record FileAttributes(
        boolean exists,
        boolean regularFile,
        boolean directory,
        long size,
        long lastModifiedTime,
        long creationTime,
        boolean readable,
        boolean writable
) {
    /** 表示「文件不存在」的常量。 */
    public static final FileAttributes NOT_FOUND = new FileAttributes(
            false, false, false, 0, 0, 0, false, false);
}
