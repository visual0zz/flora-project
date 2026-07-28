package com.flora.os.virtual.file.nio;

import com.flora.os.virtual.file.FileAttributes;
import com.flora.os.virtual.file.FSBackend;
import com.flora.os.virtual.file.VFS;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/**
 * VFS 的 {@link FileSystemProvider} 实现。
 * <p>实现 Ramet 所需的 NIO 操作子集：readAttributes / newInputStream / newOutputStream
 * / newDirectoryStream / createDirectory / isSameFile。</p>
 */
public final class VfsFileSystemProvider extends FileSystemProvider {

    private final VFS vfs;

    public VfsFileSystemProvider(VFS vfs) {
        this.vfs = vfs;
    }

    @Override
    public String getScheme() { return "vfs"; }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        return new VfsFileSystem(vfs, this);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return new VfsFileSystem(vfs, this);
    }

    @Override
    public Path getPath(URI uri) {
        return new VfsPath(uri.getPath(), new VfsFileSystem(vfs, this));
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) {
        throw new UnsupportedOperationException();
    }

    // ===================== 文件属性 =====================

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type != BasicFileAttributes.class && type != VfsFileAttributes.class) {
            throw new UnsupportedOperationException("仅支持 BasicFileAttributes");
        }
        String p = path.toString();
        FileAttributes attr = vfs.resolveInternal(p).backend().getAttributes(vfs.resolveInternal(p).path());
        return (A) new VfsFileAttributes(attr);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        FileAttributes attr = resolveAttr(path);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isDirectory", attr.directory());
        map.put("isRegularFile", attr.regularFile());
        map.put("isSymbolicLink", false);
        map.put("isOther", !attr.exists());
        map.put("size", attr.size());
        map.put("lastModifiedTime", attr.lastModifiedTime());
        map.put("creationTime", attr.creationTime());
        return map;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    // ===================== 读写 =====================

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return resolveBackend(path).read(resolveRelative(path));
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        if (path instanceof VfsPath vp && vp.getFileSystem() instanceof VfsFileSystem vfsFs) {
            boolean append = false;
            for (OpenOption o : options) {
                if (o == StandardOpenOption.APPEND) { append = true; break; }
            }
            return vfsFs.vfs().get(vp.toString()).openOutputStream(append);
        }
        throw new IOException("不支持的 Path 类型: " + path.getClass());
    }

    // ===================== 目录操作 =====================

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        String p = dir.toString();
        VfsFileSystem fs = (VfsFileSystem) dir.getFileSystem();
        List<String> names = resolveBackend(dir).list(resolveRelative(dir));
        List<Path> entries = new ArrayList<>();
        for (String name : names) {
            Path child = dir.resolve(name);
            if (filter == null || filter.accept(child)) {
                entries.add(child);
            }
        }
        return new VfsDirectoryStream(entries);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        if (!resolveBackend(dir).createDirectory(resolveRelative(dir))) {
            throw new FileAlreadyExistsException(dir.toString());
        }
    }

    // ===================== 通道 =====================

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        VfsFileSystem vfsFs = (VfsFileSystem) path.getFileSystem();
        boolean append = false;
        for (OpenOption o : options) {
            if (o == StandardOpenOption.APPEND) { append = true; break; }
        }
        // 读模式
        boolean read = true;
        boolean write = false;
        for (OpenOption o : options) {
            if (o == StandardOpenOption.WRITE || o == StandardOpenOption.CREATE
                    || o == StandardOpenOption.APPEND) { write = true; read = false; break; }
        }
        if (!write) {
            // 只读：从 VFS 读取全部字节
            byte[] data = vfsFs.vfs().get(path.toString()).readAllBytes();
            return new VfsByteChannel(data, false);
        } else {
            // 写入模式：使用包装流
            OutputStream out = vfsFs.vfs().get(path.toString()).openOutputStream(append);
            return new VfsByteChannel(out);
        }
    }

    // ===================== 删除/拷贝/移动 =====================

    @Override
    public void delete(Path path) throws IOException {
        resolveBackend(path).delete(resolveRelative(path));
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        try (InputStream in = newInputStream(source); OutputStream out = newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        resolveBackend(source).rename(resolveRelative(source), resolveRelative(target));
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        return path.toString().equals(path2.toString());
    }

    @Override
    public boolean isHidden(Path path) { return false; }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        // 只检查路径是否存在
        FileAttributes attr = resolveAttr(path);
        if (!attr.exists()) throw new NoSuchFileException(path.toString());
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    // ===================== 内部辅助 =====================

    private FileAttributes resolveAttr(Path path) {
        return vfs.resolveInternal(path.toString()).backend().getAttributes(vfs.resolveInternal(path.toString()).path());
    }

    private FSBackend resolveBackend(Path path) {
        return vfs.resolveInternal(path.toString()).backend();
    }

    private String resolveRelative(Path path) {
        return vfs.resolveInternal(path.toString()).path();
    }

    // VfsFileAttributes — 将我们的 FileAttributes 桥接到 NIO 的 BasicFileAttributes
    static final class VfsFileAttributes implements BasicFileAttributes {
        private final FileAttributes attr;
        VfsFileAttributes(FileAttributes attr) { this.attr = attr; }
        @Override public boolean isRegularFile() { return attr.regularFile(); }
        @Override public boolean isDirectory() { return attr.directory(); }
        @Override public boolean isSymbolicLink() { return false; }
        @Override public boolean isOther() { return !attr.exists() && !attr.directory() && !attr.regularFile(); }
        @Override public long size() { return attr.size(); }
        @Override public FileTime lastModifiedTime() { return FileTime.fromMillis(attr.lastModifiedTime()); }
        @Override public FileTime lastAccessTime() { return FileTime.fromMillis(attr.lastModifiedTime()); }
        @Override public FileTime creationTime() { return FileTime.fromMillis(attr.creationTime()); }
        @Override public Object fileKey() { return null; }
    }
}
