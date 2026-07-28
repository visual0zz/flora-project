package com.flora.os.virtual.file.backend;

import com.flora.os.virtual.file.FileAttributes;
import com.flora.os.virtual.file.FSBackend;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 纯内存虚拟文件系统。
 * <p>所有数据存储在内存树结构中。线程安全（{@link ReentrantReadWriteLock}）。</p>
 */
public final class MemoryFileSystem implements FSBackend {

    private final DirNode root = new DirNode();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MemoryFileSystem() {}

    @Override
    public FileAttributes getAttributes(String path) {
        lock.readLock().lock();
        try {
            Node node = findNode(path);
            if (node == null) return FileAttributes.NOT_FOUND;
            if (node instanceof FileNode f) {
                long now = System.currentTimeMillis();
                return new FileAttributes(true, true, false, f.data.length, now, now, true, true);
            }
            if (node instanceof DirNode) {
                return new FileAttributes(true, false, true, 0, 0, 0, true, true);
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
            Node node = findNode(path);
            if (node instanceof FileNode f) {
                return new ByteArrayInputStream(f.data.clone());
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
            if (findNode(path) != null) return false;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return false;
            String name = namePart(path);
            parent.children.put(name, new FileNode());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean createDirectory(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(path) != null) return false;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return false;
            String name = namePart(path);
            parent.children.put(name, new DirNode());
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
            Node node = findNode(path);
            if (node == null) return false;
            if (node instanceof DirNode d && !d.children.isEmpty()) return false; // 非空目录
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
            Node node = findNode(source);
            if (node == null || findNode(dest) != null) return false;
            DirNode srcParent = parentDir(source);
            DirNode dstParent = ensureParentDir(dest);
            if (srcParent == null || dstParent == null) return false;
            String srcName = namePart(source);
            String dstName = namePart(dest);
            srcParent.children.remove(srcName);
            dstParent.children.put(dstName, node);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> list(String path) throws IOException {
        lock.readLock().lock();
        try {
            Node node = findNode(path);
            if (node instanceof DirNode d) {
                return new ArrayList<>(d.children.keySet());
            }
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ===================== 内部节点 =====================

    private interface Node {}
    private static final class FileNode implements Node {
        byte[] data = new byte[0];
    }
    private static final class DirNode implements Node {
        final Map<String, Node> children = new LinkedHashMap<>();
    }

    // ===================== 路径解析 =====================

    private Node findNode(String path) {
        if (path.equals("/")) return root;
        DirNode cur = root;
        String[] parts = path.split("/", -1);
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            Node child = cur.children.get(p);
            if (child == null) return null;
            if (i == parts.length - 1) return child;
            if (child instanceof DirNode d) cur = d;
            else return null;
        }
        return cur;
    }

    private DirNode parentDir(String path) {
        String p = PathUtil.parent(path);
        if (p == null || p.equals("/")) return root;
        Node n = findNode(p);
        return n instanceof DirNode d ? d : null;
    }

    private DirNode ensureParentDir(String path) {
        if (path.equals("/")) return null;
        String parent = PathUtil.parent(path);
        if (parent == null || parent.equals("/")) return root;
        Node n = findNode(parent);
        if (n instanceof DirNode) return (DirNode) n;
        // 尝试创建父目录
        String pp = PathUtil.parent(parent);
        if (pp != null) {
            DirNode gp = ensureParentDir(parent);
            if (gp != null) {
                String name = namePart(parent);
                DirNode d = new DirNode();
                gp.children.put(name, d);
                return d;
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
        private ByteArrayOutputStream buf;

        MemoryOutputStream(String path, boolean append) {
            this.path = path;
            this.append = append;
            this.buf = new ByteArrayOutputStream();
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
                Node node = findNode(path);
                if (node instanceof FileNode f) {
                    if (append) {
                        byte[] combined = Arrays.copyOf(f.data, f.data.length + newData.length);
                        System.arraycopy(newData, 0, combined, f.data.length, newData.length);
                        f.data = combined;
                    } else {
                        f.data = newData;
                    }
                } else {
                    DirNode p = ensureParentDir(path);
                    if (p != null) {
                        FileNode f = new FileNode();
                        f.data = newData;
                        p.children.put(namePart(path), f);
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    private static final class PathUtil {
        static String parent(String path) {
            if (path == null || path.equals("/")) return null;
            int idx = path.lastIndexOf('/');
            if (idx <= 0) return "/";
            return path.substring(0, idx);
        }
    }
}
