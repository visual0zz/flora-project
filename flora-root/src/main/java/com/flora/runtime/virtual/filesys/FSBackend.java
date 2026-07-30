package com.flora.runtime.virtual.filesys;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.util.List;
import java.util.Set;

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

    /**
     * 打开 {@link SeekableByteChannel} 读写文件内容。
     * <p>选项集 {@code options} 直接来自 NIO {@link java.nio.file.spi.FileSystemProvider}，
     * 后端应解释 {@code READ}、{@code WRITE}、{@code APPEND}、{@code CREATE}、
     * {@code CREATE_NEW}、{@code TRUNCATE_EXISTING} 等标准选项。
     * {@link java.nio.channels.Channel#close()} 时，
     * 实现应将所有待定更改持久化到存储层。</p>
     *
     * @param path    已归一化的虚拟路径
     * @param options 标准 NIO 打开选项
     * @return 可随机访问的字节通道
     */
    SeekableByteChannel openChannel(String path, Set<? extends OpenOption> options) throws IOException;

    /** 创建单层目录。父目录必须存在。 */
    FileOpResult createDirectory(String path) throws IOException;

    /** 删除文件或空目录。 */
    FileOpResult delete(String path) throws IOException;

    /** 重命名/移动文件或目录。 */
    FileOpResult rename(String source, String dest) throws IOException;

    /** 列出目录下的子项名称（不含路径）。返回空列表若不存在或非目录。 */
    List<String> list(String path) throws IOException;

    /** 释放后端持有的资源（如文件句柄、连接池）。默认空实现。 */
    default void close() throws IOException {}
}
