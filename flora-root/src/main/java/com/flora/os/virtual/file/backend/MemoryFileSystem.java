package com.flora.os.virtual.file.backend;

import com.flora.os.virtual.file.FileAttributes;
import com.flora.os.virtual.file.FSBackend;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 纯内存虚拟文件系统（支持符号链接）。
 * <p>所有数据存储在内存树结构中。线程安全（{@link ReentrantReadWriteLock}）。</p>
 */
public final class MemoryFileSystem implements FSBackend {

    private final DirNode root = new DirNode();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MemoryFileSystem() {}

    @Override
    public FileAttributes getAttributes(String path) {
        return getAttributes(path, true);
    }

    @Override
    public FileAttributes getAttributes(String path, boolean followLinks) {
        lock.readLock().lock();
        try {
            Node node = findNode(path, followLinks);
            if (node == null) return FileAttributes.NOT_FOUND;
            long now = System.currentTimeMillis();
            if (node instanceof SymlinkNode s) {
                return new FileAttributes(true, false, false, true,
                        s.target.length(), now, now, true, true);
            }
            if (node instanceof FileNode f) {
                return new FileAttributes(true, true, false, false,
                        f.data.length, now, now, true, true);
            }
            if (node instanceof DirNode) {
                return new FileAttributes(true, false, true, false, 0, 0, 0, true, true);
            }
            return FileAttributes.NOT_FOUND;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public InputStream read(String path) throws IOException {
        lock.readLock().lock();
        try {
            Node node = findNode(path, true);
            if (node instanceof FileNode f) {
                return new ByteArrayInputStream(f.data.clone());
            }
            if (node instanceof SymlinkNode s) {
                // 跟随符号链接
                String resolved = resolveLinkTarget(path, s.target);
                return read(resolved);
            }
            throw new FileNotFoundException("文件不存在: " + path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public OutputStream write(String path, boolean append) throws IOException {
        return new MemoryOutputStream(path, append);
    }

    @Override
    public boolean createFile(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(path, false) != null) return false;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return false;
            parent.children.put(namePart(path), new FileNode());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean createDirectory(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(path, false) != null) return false;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return false;
            parent.children.put(namePart(path), new DirNode());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (path.equals("/")) return false;
            Node node = findNode(path, false); // 不跟随：删除链接本身
            if (node == null) return false;
            if (node instanceof DirNode d && !d.children.isEmpty()) return false;
            DirNode parent = parentDir(path);
            if (parent == null) return false;
            return parent.children.remove(namePart(path)) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean rename(String source, String dest) throws IOException {
        lock.writeLock().lock();
        try {
            Node node = findNode(source, false);
            if (node == null || findNode(dest, false) != null) return false;
            DirNode srcParent = parentDir(source);
            DirNode dstParent = ensureParentDir(dest);
            if (srcParent == null || dstParent == null) return false;
            srcParent.children.remove(namePart(source));
            dstParent.children.put(namePart(dest), node);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> list(String path) throws IOException {
        lock.readLock().lock();
        try {
            Node node = findNode(path, true);
            if (node instanceof DirNode d) {
                return new ArrayList<>(d.children.keySet());
            }
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean createSymbolicLink(String path, String target) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(path, false) != null) return false;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return false;
            parent.children.put(namePart(path), new SymlinkNode(target));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String readSymbolicLink(String path) throws IOException {
        lock.readLock().lock();
        try {
            Node node = findNode(path, false);
            if (node instanceof SymlinkNode s) return s.target;
            throw new NotLinkException(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ===================== 内部节点 =====================

    private interface Node {}
    private static final class FileNode implements Node { byte[] data = new byte[0]; }
    private static final class DirNode implements Node {
        final Map<String, Node> children = new LinkedHashMap<>();
    }
    private static final class SymlinkNode implements Node {
        final String target;
        SymlinkNode(String target) { this.target = target; }
    }

    // ===================== 路径解析 =====================

    /** 查找节点（跟随符号链接）。 */
    private Node findNode(String path) { return findNode(path, true); }

    /** 查找节点，可选是否跟随符号链接。 */
    private Node findNode(String path, boolean followLinks) {
        if (path.equals("/")) return root;
        DirNode cur = root;
        String[] parts = path.split("/", -1);
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            Node child = cur.children.get(p);
            if (child == null) return null;
            // 路径中间遇到符号链接则跟随
            if (followLinks && child instanceof SymlinkNode s) {
                String resolved = resolveIntermediate(path, s.target, parts, i);
                return findNode(resolved, followLinks);
            }
            if (i == parts.length - 1) return child;
            if (child instanceof DirNode d) cur = d;
            else return null;
        }
        return cur;
    }

    /** 解析中间符号链接：用 target 替换当前段之后的路径。 */
    private static String resolveIntermediate(String origPath, String target, String[] parts, int at) {
        StringBuilder sb = new StringBuilder(target);
        for (int j = at + 1; j < parts.length; j++) {
            sb.append('/').append(parts[j]);
        }
        return sb.toString();
    }

    /** 解析最终的符号链接目标（用于 read 操作）。 */
    private String resolveLinkTarget(String linkPath, String target) {
        if (target.startsWith("/")) return target;
        // 相对路径：相对于链接所在目录
        String parent = PathUtil.parent(linkPath);
        return parent != null ? parent + "/" + target : "/" + target;
    }

    private DirNode parentDir(String path) {
        String p = PathUtil.parent(path);
        if (p == null || p.equals("/")) return root;
        Node n = findNode(p, false);
        return n instanceof DirNode d ? d : null;
    }

    private DirNode ensureParentDir(String path) {
        if (path.equals("/")) return null;
        String parent = PathUtil.parent(path);
        if (parent == null || parent.equals("/")) return root;
        Node n = findNode(parent, false);
        if (n instanceof DirNode) return (DirNode) n;
        String pp = PathUtil.parent(parent);
        if (pp != null) {
            DirNode gp = ensureParentDir(parent);
            if (gp != null) {
                gp.children.put(namePart(parent), new DirNode());
                return (DirNode) gp.children.get(namePart(parent));
            }
        }
        return null;
    }

    private static String namePart(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    // ===================== 输出流 =====================

    private class MemoryOutputStream extends OutputStream {
        private final String path;
        private final boolean append;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        MemoryOutputStream(String path, boolean append) {
            this.path = path;
            this.append = append;
        }

        @Override
        public void write(int b) { buf.write(b); }
        @Override
        public void write(byte[] b, int off, int len) { buf.write(b, off, len); }

        @Override
        public void close() throws IOException {
            super.close();
            byte[] newData = buf.toByteArray();
            lock.writeLock().lock();
            try {
                Node node = findNode(path, false);
                if (node instanceof FileNode f) {
                    if (append) {
                        byte[] combined = Arrays.copyOf(f.data, f.data.length + newData.length);
                        System.arraycopy(newData, 0, combined, f.data.length, newData.length);
                        f.data = combined;
                    } else {
                        f.data = newData;
                    }
                    return;
                }
                // 跟随符号链接写入目标
                if (node instanceof SymlinkNode s) {
                    String resolved = resolveLinkTarget(path, s.target);
                    Node target = findNode(resolved, false);
                    if (target instanceof FileNode ft) {
                        ft.data = append ? concat(ft.data, newData) : newData;
                        return;
                    }
                }
                // 创建新文件
                DirNode p = ensureParentDir(path);
                if (p != null) {
                    FileNode f = new FileNode();
                    f.data = newData;
                    p.children.put(namePart(path), f);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        private byte[] concat(byte[] a, byte[] b) {
            byte[] r = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, r, a.length, b.length);
            return r;
        }
    }

    // ===================== 工具 =====================

    private static final class PathUtil {
        static String parent(String path) {
            if (path == null || path.equals("/")) return null;
            int idx = path.lastIndexOf('/');
            if (idx <= 0) return "/";
            return path.substring(0, idx);
        }
    }

    /** 路径非符号链接时抛出的异常。 */
    static final class NotLinkException extends IOException {
        NotLinkException(String path) { super("不是符号链接: " + path); }
    }
}
