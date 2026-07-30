package com.flora.runtime.virtual.filesys;

/**
 * 文件操作结果，精确描述操作完成的状况。
 * <p>替代 {@code boolean} 返回值，区分不同的失败原因。</p>
 */
public enum FileOpResult {
    /** 操作成功完成。 */
    SUCCESS,

    /** 目标路径已存在（如 {@code createDirectory} 目录已存在、{@code rename} 目标已存在）。 */
    ALREADY_EXISTS,

    /** 操作路径不存在（如 {@code delete} 文件不存在、{@code rename} 源不存在）。 */
    NOT_FOUND,

    /** 目录非空无法删除（{@code delete}）。 */
    NOT_EMPTY,

    /** 其他失败。 */
    FAILED
}
