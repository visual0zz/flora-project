package com.flora.root.runtime.virtual.filesys.bridge;

import com.flora.root.runtime.virtual.filesys.VfsFileSystem;

import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * 虚拟文件系统的 {@link Path} 实现。
 * <p>路径为绝对、归一化、UNIX 风格，所有字符串操作委托给底层路径字符串。
 * 配合 {@link VfsFileSystem} 使用，其他代码仅通过 {@code Path} + {@code Files.*}
 * 透明操作 VFS，无需感知其存在。</p>
 */
public final class VfsPath implements Path {

    private final String path;     // 归一化绝对路径，如 "/foo/bar.txt"
    private final VfsFileSystem fs;

    public VfsPath(String path, VfsFileSystem fs) {
        // 去掉尾部 /（根路径 "/" 保留）
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("路径不能为空");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        this.path = path;
        this.fs = fs;
    }

    @Override
    public String toString() { return path; }

    @Override
    public FileSystem getFileSystem() { return fs; }

    @Override
    public boolean isAbsolute() { return path.startsWith("/"); }

    @Override
    public Path getRoot() { return path.startsWith("/") ? new VfsPath("/", fs) : null; }

    @Override
    public Path getFileName() {
        if (path.equals("/")) return null;
        int idx = path.lastIndexOf('/');
        return new VfsPath(path.substring(idx + 1), fs);
    }

    @Override
    public Path getParent() {
        if (path.equals("/")) return null;
        int idx = path.lastIndexOf('/');
        if (idx == 0) return new VfsPath("/", fs);
        return new VfsPath(path.substring(0, idx), fs);
    }

    @Override
    public int getNameCount() {
        if (path.equals("/")) return 0;
        String[] parts = path.split("/", -1);
        return parts.length - 1; // 去掉前导空串
    }

    @Override
    public Path getName(int index) {
        int count = getNameCount();
        if (index < 0 || index >= count) throw new IllegalArgumentException("index: " + index);
        String[] parts = path.split("/", -1);
        return new VfsPath(parts[index + 1], fs);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        int count = getNameCount();
        if (beginIndex < 0 || beginIndex >= count || endIndex <= beginIndex || endIndex > count)
            throw new IllegalArgumentException();
        String[] parts = path.split("/", -1);
        return new VfsPath("/" + String.join("/", Arrays.copyOfRange(parts, beginIndex + 1, endIndex + 1)), fs);
    }

    @Override
    public boolean startsWith(Path other) {
        return startsWith(other.toString());
    }

    @Override
    public boolean startsWith(String other) {
        return path.equals(other) || path.startsWith(other + "/");
    }

    @Override
    public boolean endsWith(Path other) {
        return endsWith(other.toString());
    }

    @Override
    public boolean endsWith(String other) {
        return path.equals(other) || path.endsWith("/" + other);
    }

    @Override
    public Path normalize() { return this; } // 路径已是归一化

    @Override
    public Path resolve(Path other) {
        return resolve(other.toString());
    }

    @Override
    public Path resolve(String other) {
        if (other.startsWith("/")) return new VfsPath(other, fs);
        if (path.equals("/")) return new VfsPath("/" + other, fs);
        return new VfsPath(path + "/" + other, fs);
    }

    @Override
    public Path resolveSibling(Path other) {
        return resolveSibling(other.toString());
    }

    @Override
    public Path resolveSibling(String other) {
        Path parent = getParent();
        return parent != null ? parent.resolve(other) : new VfsPath(other.startsWith("/") ? other : "/" + other, fs);
    }

    @Override
    public Path relativize(Path other) {
        String o = other.toString();
        if (!path.equals("/") && o.startsWith(path + "/")) {
            return new VfsPath(o.substring(path.length() + 1), fs);
        }
        // 回退到简单的字符串裁剪
        if (path.equals(o)) return new VfsPath("", fs);
        String[] src = path.split("/", -1);
        String[] dst = o.split("/", -1);
        int i = 0;
        while (i < src.length && i < dst.length && src[i].equals(dst[i])) i++;
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < src.length; j++) {
            if (!src[j].isEmpty()) sb.append("../");
        }
        for (int j = i; j < dst.length; j++) {
            if (!dst[j].isEmpty()) sb.append(dst[j]).append(j < dst.length - 1 ? "/" : "");
        }
        return new VfsPath(sb.toString(), fs);
    }

    @Override
    public URI toUri() {
        return URI.create("vfs:" + path);
    }

    @Override
    public Path toAbsolutePath() { return this; }

    @Override
    public Path toRealPath(LinkOption... options) { return this; }

    @Override
    public File toFile() { throw new UnsupportedOperationException("VfsPath 不支持 toFile()"); }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
        List<Path> parts = new ArrayList<>();
        for (int i = 0; i < getNameCount(); i++) parts.add(getName(i));
        return parts.iterator();
    }

    @Override
    public int compareTo(Path other) {
        return path.compareTo(other.toString());
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof VfsPath p && path.equals(p.path));
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
