package com.flora.runtime.virtual.filesys.bridge;

import com.flora.os.UnixPathUtil;
import com.flora.runtime.virtual.filesys.FSBackend;
import com.flora.runtime.virtual.filesys.FileAttributes;
import com.flora.runtime.virtual.filesys.FileOpResult;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystemException;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 虚拟文件系统挂载表：管理挂载点并解析虚拟路径到后端。
 * <p>挂载表变更产生新的不可变快照，读者永远看到一致的全量快照，因此是线程安全的。
 * 路径已归一化（绝对路径、无 {@code ..}、无尾部 {@code /}）。</p>
 * <p>由 {@code VfsFileSystem} 创建并注入 {@link VfsFileSystemProvider}，
 * 供 NIO 操作将虚拟路径派发到对应 {@link FSBackend}。</p>
 */
public final class MountTable {

    private volatile List<Mount> mounts = List.of();

    /** 挂载一个后端到指定路径。路径自动归一化。挂载表以最长前缀优先匹配。 */
    public void mount(String path, FSBackend backend) {
        String normalized = UnixPathUtil.normalize(path);
        List<Mount> updated = new ArrayList<>(mounts);
        updated.add(new Mount(normalized, backend));
        updated.sort(Comparator.comparingInt((Mount m) -> m.prefix.length()).reversed());
        mounts = List.copyOf(updated); // 原子发布：读者永远看到一致的全量快照
    }

    /** 卸载指定路径的后端。 */
    public void unmount(String path) {
        String normalized = UnixPathUtil.normalize(path);
        List<Mount> updated = new ArrayList<>(mounts);
        updated.removeIf(m -> m.prefix.equals(normalized));
        mounts = List.copyOf(updated); // 原子发布
    }

    /**
     * 解析虚拟路径到后端。
     * <p>返回当前挂载表快照中匹配最长前缀的 {@link FSBackendMatch}；
     * 根路径 {@code "/"} 解析到虚拟根目录后端（仅列出挂载点）。
     * 未匹配任何挂载点时抛出 {@link IllegalStateException}。</p>
     */
    public FSBackendMatch resolve(String path) {
        List<Mount> snapshot = mounts; // volatile 读，获取当前快照
        if (path.equals("/")) return new FSBackendMatch("/", new RootBackend());
        for (Mount m : snapshot) {
            if (m.prefix.equals("/") || path.equals(m.prefix) || path.startsWith(m.prefix + "/")) {
                String relative;
                if (m.prefix.equals("/")) relative = path;
                else if (path.equals(m.prefix)) relative = "/";
                else relative = path.substring(m.prefix.length());
                return new FSBackendMatch(relative, m.backend);
            }
        }
        throw new IllegalStateException("未找到匹配的挂载点: " + path);
    }

    /** 当前挂载点前缀列表（按名称排序），供虚拟根目录列出。 */
    List<String> roots() {
        return mounts.stream().map(Mount::prefix).sorted().toList();
    }

    /** 当前已挂载的全部后端，供关闭文件系统时释放资源。 */
    public List<FSBackend> backends() {
        return mounts.stream().map(Mount::backend).toList();
    }

    private record Mount(String prefix, FSBackend backend) {}

    /** 虚拟根目录后端 —— 仅返回挂载点列表。 */
    private final class RootBackend implements FSBackend {
        @Override
        public FileAttributes getAttributes(String path, boolean followLinks) {
            if (path.equals("/")) {
                return new FileAttributes(true, false, true, false, 0, 0, 0, true, false);
            }
            return FileAttributes.NOT_FOUND;
        }

        @Override
        public SeekableByteChannel openChannel(String path, Set<? extends OpenOption> options) throws IOException {
            throw new FileSystemException("根目录不支持文件读写");
        }

        @Override
        public FileOpResult createDirectory(String path) {
            return FileOpResult.ALREADY_EXISTS;
        }

        @Override
        public FileOpResult delete(String path) {
            return FileOpResult.NOT_FOUND;
        }

        @Override
        public FileOpResult rename(String src, String dest) {
            return FileOpResult.NOT_FOUND;
        }

        @Override
        public List<String> list(String path) {
            return roots();
        }
    }
}
