package com.flora.os.virtual.file;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 虚拟文件系统入口。
 * <p>管理挂载表，将路径路由到对应 {@link FSBackend}，返回 {@link VFile} 句柄。
 * 可创建隔离的实例，也可使用全局默认实例 {@link #system()}。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 全局默认实例
 * VFS.system().mount("/mem", new MemoryFileSystem());
 * VFile f = VFS.system().get("/mem/hello.txt");
 *
 * // 隔离实例
 * VFS vfs = new VFS();
 * vfs.mount("/data", new MemoryFileSystem());
 * }</pre>
 */
public final class VFS {

    private static final VFS SYSTEM = new VFS();
    private final List<Mount> mounts = new CopyOnWriteArrayList<>();

    /** 创建隔离的 VFS 实例（挂载表独立，不与 {@link #system()} 共享）。 */
    public VFS() {}

    /** 全局默认 VFS 实例。 */
    public static VFS system() {
        return SYSTEM;
    }

    /** 挂载一个后端到指定路径。路径已归一化。 */
    public void mount(String path, FSBackend backend) {
        String normalized = PathUtil.normalize(path);
        mounts.add(new Mount(normalized, backend));
        mounts.sort(Comparator.comparingInt((Mount m) -> m.prefix().length()).reversed());
    }

    /** 卸载指定路径的后端。 */
    public void unmount(String path) {
        String normalized = PathUtil.normalize(path);
        mounts.removeIf(m -> m.prefix().equals(normalized));
    }

    /** 获取指定路径的 VFile 句柄。 */
    public VFile get(String path) {
        String normalized = PathUtil.normalize(path);
        FSBackendMatch match = resolve(normalized);
        return new VFile(normalized, match.path, match.backend);
    }

    /** 获取指定父路径+子路径的 VFile 句柄。 */
    public VFile get(String parent, String child) {
        return get(PathUtil.resolve(parent, child));
    }

    // ===================== 内部 =====================

    private record Mount(String prefix, FSBackend backend) {}

    private record FSBackendMatch(String path, FSBackend backend) {}

    /** 根据路径找到匹配的 backend，返回去掉前缀后的相对路径。 */
    private FSBackendMatch resolve(String path) {
        if (path.equals("/")) return new FSBackendMatch("/", new RootBackend());
        for (Mount m : mounts) {
            if (path.equals(m.prefix) || path.startsWith(m.prefix + "/")) {
                String relative = path.equals(m.prefix) ? "/" : path.substring(m.prefix.length());
                return new FSBackendMatch(relative, m.backend);
            }
        }
        throw new IllegalStateException("未找到匹配的挂载点: " + path);
    }

    /** 虚拟根目录后端 —— 仅返回挂载点列表。 */
    private class RootBackend implements FSBackend {
        @Override public FileAttributes getAttributes(String path) {
            if (path.equals("/")) return new FileAttributes(true, false, true, 0, 0, 0, true, false);
            return FileAttributes.NOT_FOUND;
        }
        @Override public java.io.InputStream read(String path) { throw new UnsupportedOperationException("根目录不支持读"); }
        @Override public java.io.OutputStream write(String path, boolean append) { throw new UnsupportedOperationException("根目录不支持写"); }
        @Override public boolean createFile(String path) { return false; }
        @Override public boolean createDirectory(String path) { return false; }
        @Override public boolean delete(String path) { return false; }
        @Override public boolean rename(String src, String dest) { return false; }
        @Override public List<String> list(String path) {
            return mounts.stream().map(Mount::prefix).sorted().toList();
        }
    }
}
