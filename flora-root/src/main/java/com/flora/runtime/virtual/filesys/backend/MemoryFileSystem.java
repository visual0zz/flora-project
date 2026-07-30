package com.flora.runtime.virtual.filesys.backend;

import com.flora.runtime.virtual.filesys.FileAttributes;
import com.flora.runtime.virtual.filesys.FileOpResult;
import com.flora.runtime.virtual.filesys.SymlinkFSBackend;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 纯内存虚拟文件系统（支持符号链接）。
 * <p>所有数据存储在内存树结构中。线程安全（{@link ReentrantReadWriteLock}）。</p>
 */
public final class MemoryFileSystem implements SymlinkFSBackend {

    private final DirNode root = new DirNode();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public MemoryFileSystem() {}

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
    public SeekableByteChannel openChannel(String path, Set<? extends OpenOption> options) throws IOException {
        boolean write = options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.APPEND)
                || options.contains(StandardOpenOption.CREATE)
                || options.contains(StandardOpenOption.CREATE_NEW);
        boolean append = options.contains(StandardOpenOption.APPEND);
        boolean createNew = options.contains(StandardOpenOption.CREATE_NEW);
        boolean create  = options.contains(StandardOpenOption.CREATE) || createNew;
        boolean truncate = options.contains(StandardOpenOption.TRUNCATE_EXISTING) || createNew;

        lock.writeLock().lock();
        try {
            if (path.equals("/")) throw new IOException("根目录不是文件");

            Node node = findNode(path, false);

            if (createNew && node != null && !(node instanceof DirNode)) {
                throw new FileAlreadyExistsException(path);
            }
            if ((write || create) && node == null) {
                DirNode parent = ensureParentDir(path);
                if (parent == null) throw new IOException("无法创建父目录: " + path);
                node = new FileNode();
                parent.children.put(namePart(path), node);
            }
            if (node instanceof DirNode) throw new IOException("路径不是文件: " + path);
            if (node == null) throw new NoSuchFileException(path);

            // 符号链接 → 跟随到目标
            if (node instanceof SymlinkNode s) {
                String target = resolveLinkTarget(path, s.target);
                Node tn = findNode(target, false);
                if (tn instanceof FileNode fn) {
                    node = fn;
                } else {
                    throw new FileNotFoundException("符号链接指向的文件: " + target);
                }
            }

            FileNode fn = (FileNode) node;
            byte[] data = truncate ? new byte[0] : fn.data.clone();

            return new MemoryChannel(data, write, fn, append);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public FileOpResult createDirectory(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(path, false) != null) return FileOpResult.ALREADY_EXISTS;
            DirNode parent = ensureParentDir(path);
            if (parent == null) return FileOpResult.FAILED;
            parent.children.put(namePart(path), new DirNode());
            return FileOpResult.SUCCESS;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public FileOpResult delete(String path) throws IOException {
        lock.writeLock().lock();
        try {
            if (path.equals("/")) return FileOpResult.NOT_FOUND;
            Node node = findNode(path, false); // 不跟随：删除链接本身
            if (node == null) return FileOpResult.NOT_FOUND;
            if (node instanceof DirNode d && !d.children.isEmpty()) return FileOpResult.NOT_EMPTY;
            DirNode parent = parentDir(path);
            if (parent == null) return FileOpResult.NOT_FOUND;
            return parent.children.remove(namePart(path)) != null
                    ? FileOpResult.SUCCESS : FileOpResult.NOT_FOUND;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public FileOpResult rename(String source, String dest) throws IOException {
        lock.writeLock().lock();
        try {
            if (findNode(source, false) == null) return FileOpResult.NOT_FOUND;
            if (findNode(dest, false) != null) return FileOpResult.ALREADY_EXISTS;
            Node node = findNode(source, false);
            DirNode srcParent = parentDir(source);
            DirNode dstParent = ensureParentDir(dest);
            if (srcParent == null || dstParent == null) return FileOpResult.FAILED;
            srcParent.children.remove(namePart(source));
            dstParent.children.put(namePart(dest), node);
            return FileOpResult.SUCCESS;
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

    // ===================== 内存字节通道 =====================

    /**
     * 可随机访问的内存字节通道。
     * <p>所有读写在 {@code byte[]} 缓冲上进行，
     * {@link #close()} 时将缓冲数据写回 {@link FileNode}。</p>
     */
    private final class MemoryChannel implements SeekableByteChannel {
        private boolean open = true;
        private byte[] buf;
        private int pos;
        private final boolean writable;
        private final FileNode fileNode;

        MemoryChannel(byte[] initialData, boolean writable, FileNode fileNode, boolean append) {
            this.buf = initialData;
            this.writable = writable;
            this.fileNode = fileNode;
            if (append) this.pos = buf.length;
        }

        @Override public boolean isOpen() { return open; }

        @Override
        public void close() {
            if (!open) return;
            open = false;
            if (!writable) return;
            lock.writeLock().lock();
            try {
                fileNode.data = buf;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public int read(ByteBuffer dst) {
            int remaining = buf.length - pos;
            if (remaining <= 0) return -1;
            int toRead = Math.min(dst.remaining(), remaining);
            dst.put(buf, pos, toRead);
            pos += toRead;
            return toRead;
        }

        @Override
        public int write(ByteBuffer src) {
            if (!writable) throw new UnsupportedOperationException("通道未打开写");
            int len = src.remaining();
            ensureCapacity(pos + len);
            src.get(buf, pos, len);
            pos += len;
            return len;
        }

        @Override public long position() { return pos; }

        @Override
        public SeekableByteChannel position(long newPos) {
            if (newPos < 0) throw new IllegalArgumentException();
            pos = (int) newPos;
            return this;
        }

        @Override public long size() { return buf.length; }

        @Override
        public SeekableByteChannel truncate(long size) {
            if (size < buf.length) {
                byte[] trimmed = new byte[(int) size];
                System.arraycopy(buf, 0, trimmed, 0, (int) size);
                buf = trimmed;
            }
            return this;
        }

        private void ensureCapacity(int minCap) {
            if (minCap <= buf.length) return;
            int newLen = Math.max(buf.length * 2, minCap);
            byte[] newBuf = new byte[newLen];
            System.arraycopy(buf, 0, newBuf, 0, buf.length);
            buf = newBuf;
        }
    }

    // ===================== 路径解析 =====================

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
                String resolved = resolveIntermediate(s.target, parts, i);
                return findNode(resolved, followLinks);
            }
            if (i == parts.length - 1) return child;
            if (child instanceof DirNode d) cur = d;
            else return null;
        }
        return cur;
    }

    /** 解析中间符号链接：用 target 替换当前段之后的路径。 */
    private static String resolveIntermediate(String target, String[] parts, int at) {
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
        int idx = linkPath.lastIndexOf('/');
        String parent = idx <= 0 ? "/" : linkPath.substring(0, idx);
        return parent + "/" + target;
    }

    private DirNode parentDir(String path) {
        String p = parentOf(path);
        if (p == null || p.equals("/")) return root;
        Node n = findNode(p, false);
        return n instanceof DirNode d ? d : null;
    }

    private DirNode ensureParentDir(String path) {
        if (path.equals("/")) return null;
        String parent = parentOf(path);
        if (parent == null || parent.equals("/")) return root;
        Node n = findNode(parent, false);
        if (n instanceof DirNode) return (DirNode) n;
        String pp = parentOf(parent);
        if (pp != null) {
            DirNode gp = ensureParentDir(parent);
            if (gp != null) {
                gp.children.put(namePart(parent), new DirNode());
                return (DirNode) gp.children.get(namePart(parent));
            }
        }
        return null;
    }

    private static String parentOf(String path) {
        if (path == null || path.equals("/")) return null;
        int idx = path.lastIndexOf('/');
        if (idx <= 0) return "/";
        return path.substring(0, idx);
    }

    private static String namePart(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** 路径非符号链接时抛出的异常。 */
    static final class NotLinkException extends IOException {
        NotLinkException(String path) { super("不是符号链接: " + path); }
    }
}
