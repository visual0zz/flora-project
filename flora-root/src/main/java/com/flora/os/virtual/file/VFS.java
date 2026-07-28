package com.flora.os.virtual.file;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 虚拟文件系统入口。
 * <p>管理挂载表，将路径路由到对应 {@link FSBackend}，返回 {@link VFile} 句柄。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * VFS.mount("/mem", new MemoryFileSystem());
 * VFile f = VFS.get("/mem/hello.txt");
 * f.writeString("Hello VFS!");
 * String s = f.readString(); // "Hello VFS!"
 * }</pre>
 */
public final class VFS {

    private static final VFS INSTANCE = new VFS();
    private final List<Mount> mounts = new CopyOnWriteArrayList<>();

    private VFS() {}

    /** 挂载一个后端到指定路径。路径已归一化。 */
    public static void mount(String path, FSBackend backend) {
        String normalized = PathUtil.normalize(path);
        INSTANCE.mounts.add(new Mount(normalized, backend));
        // 按路径长度从长到短排序（最长前缀优先匹配）
        INSTANCE.mounts.sort(Comparator.comparingInt((Mount m) -> m.prefix().length()).reversed());
    }

    /** 卸载指定路径的后端。 */
    public static void unmount(String path) {
        String normalized = PathUtil.normalize(path);
        INSTANCE.mounts.removeIf(m -> m.prefix().equals(normalized));
    }

    /** 获取指定路径的 VFile 句柄。 */
    public static VFile get(String path) {
        String normalized = PathUtil.normalize(path);
        FSBackendMatch match = INSTANCE.resolve(normalized);
        return new VFile(normalized, match.path, match.backend);
    }

    /** 获取指定父路径+子路径的 VFile 句柄。 */
    public static VFile get(String parent, String child) {
        return get(PathUtil.resolve(parent, child));
    }

    /** 虚拟根目录 —— 列出所有挂载点。 */
    static List<String> listMounts() {
        return INSTANCE.mounts.stream()
                .map(Mount::prefix)
                .sorted()
                .toList();
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
    private static class RootBackend implements FSBackend {
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
        @Override public List<String> list(String path) { return listMounts(); }
    }
}
