package com.flora.runtime.virtual.filesys;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 虚拟文件系统后端接口。
 * <p>实现者提供底层存储能力：内存、数据库、远程、真实文件系统等。
 * 路径已归一化（绝对路径、无 {@code ..}、无尾部 {@code /}）。
 * 支持符号链接的后端应额外实现 {@link SymlinkFSBackend}。</p>
 */
public interface FSBackend {

    /** 获取文件/目录元数据（跟随符号链接）。路径不存在返回 {@link FileAttributes#NOT_FOUND}。 */
    default FileAttributes getAttributes(String path) {
        return getAttributes(path, true);
    }

    /** 获取文件/目录元数据。 */
    FileAttributes getAttributes(String path, boolean followLinks);

    /** 打开输入流读取文件内容。路径必须为合法文件。 */
    InputStream read(String path) throws IOException;

    /**
     * 打开输出流写入文件内容。
     * @param append true 追加到末尾，false 覆盖
     */
    OutputStream write(String path, boolean append) throws IOException;

    /** 创建单层目录。父目录必须存在。返回 false 若已存在。 */
    boolean createDirectory(String path) throws IOException;

    /** 删除文件或空目录。返回 false 若不存在。 */
    boolean delete(String path) throws IOException;

    /** 重命名/移动文件或目录。返回 false 若源不存在或目标已存在。 */
    boolean rename(String source, String dest) throws IOException;

    /** 列出目录下的子项名称（不含路径）。返回空列表若不存在或非目录。 */
    List<String> list(String path) throws IOException;
}
