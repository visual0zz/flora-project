package com.flora.os.virtual.file.nio;

import com.flora.os.virtual.file.FileAttributes;
import com.flora.os.virtual.file.FSBackend;
import com.flora.os.virtual.file.VFS;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/**
 * VFS 的 {@link FileSystemProvider} 实现。
 * <p>支持 NIO 核心操作 + 符号链接。不支持的操作用 {@link UnsupportedOperationException} 占位。</p>
 */
public final class VfsFileSystemProvider extends FileSystemProvider {

    private final VFS vfs;

    public VfsFileSystemProvider(VFS vfs) {
        this.vfs = vfs;
    }

    @Override public String getScheme() { return "vfs"; }

    @Override public FileSystem newFileSystem(URI uri, Map<String, ?> env) { return new VfsFileSystem(vfs, this); }
    @Override public FileSystem getFileSystem(URI uri) { return new VfsFileSystem(vfs, this); }

    @Override
    public Path getPath(URI uri) {
        return new VfsPath(uri.getPath(), new VfsFileSystem(vfs, this));
    }

    @Override public FileSystem newFileSystem(Path path, Map<String, ?> env) { throw new UnsupportedOperationException(); }

    // ===================== 文件属性 =====================

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type != BasicFileAttributes.class && type != VfsFileAttributes.class) {
            throw new UnsupportedOperationException("仅支持 BasicFileAttributes");
        }
        boolean followLinks = !List.of(options).contains(LinkOption.NOFOLLOW_LINKS);
        FileAttributes attr = vfs.resolveInternal(path.toString()).backend()
                .getAttributes(vfs.resolveInternal(path.toString()).path(), followLinks);
        return (A) new VfsFileAttributes(attr);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        boolean followLinks = !List.of(options).contains(LinkOption.NOFOLLOW_LINKS);
        FileAttributes attr = resolveAttr(path, followLinks);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isDirectory", attr.directory());
        map.put("isRegularFile", attr.regularFile());
        map.put("isSymbolicLink", attr.symbolicLink());
        map.put("isOther", !attr.exists() && !attr.directory() && !attr.regularFile() && !attr.symbolicLink());
        map.put("size", attr.size());
        map.put("lastModifiedTime", attr.lastModifiedTime());
        map.put("creationTime", attr.creationTime());
        return map;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        if ("lastModifiedTime".equals(attribute) && value instanceof FileTime ft) {
            // VFS 目前不支持修改时间戳，静默忽略
            return;
        }
        // 其余属性不支持
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
        boolean append = options.contains(StandardOpenOption.APPEND);
        boolean write = options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.CREATE)
                || options.contains(StandardOpenOption.APPEND);
        if (!write) {
            byte[] data = vfsFs.vfs().get(path.toString()).readAllBytes();
            return new VfsByteChannel(data, false);
        } else {
            OutputStream out = vfsFs.vfs().get(path.toString()).openOutputStream(append);
            return new VfsByteChannel(out);
        }
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        SeekableByteChannel sbc = newByteChannel(path, options, attrs);
        return new VfsFileChannel(sbc);
    }

    // ===================== 删除/拷贝/移动 =====================

    @Override
    public void delete(Path path) throws IOException {
        if (!resolveBackend(path).delete(resolveRelative(path))) {
            throw new NoSuchFileException(path.toString());
        }
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        try { delete(path); return true; } catch (NoSuchFileException e) { return false; }
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = List.of(options).contains(StandardCopyOption.REPLACE_EXISTING);
        if (!replaceExisting && exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        try (InputStream in = newInputStream(source); OutputStream out = newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = List.of(options).contains(StandardCopyOption.REPLACE_EXISTING);
        if (!replaceExisting && exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        if (!resolveBackend(source).rename(resolveRelative(source), resolveRelative(target))) {
            // fallback: copy + delete
            copy(source, target, options);
            delete(source);
        }
    }

    @Override
    public boolean isHidden(Path path) { return false; }

    @Override
    public FileStore getFileStore(Path path) { throw new UnsupportedOperationException(); }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        if (!resolveAttr(path, true).exists()) throw new NoSuchFileException(path.toString());
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        return null;
    }

    // ===================== 符号链接 =====================

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        if (!resolveBackend(link).createSymbolicLink(resolveRelative(link), target.toString())) {
            throw new FileAlreadyExistsException(link.toString());
        }
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        String target = resolveBackend(link).readSymbolicLink(resolveRelative(link));
        return new VfsPath(target, (VfsFileSystem) link.getFileSystem());
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        // 跟随符号链接后比较
        return resolveAttr(path, true).exists() && resolveAttr(path2, true).exists()
                && path.toRealPath().toString().equals(path2.toRealPath().toString());
    }

    // ===================== 内部辅助 =====================

    private boolean exists(Path path) {
        try { return resolveAttr(path, true).exists(); } catch (Exception e) { return false; }
    }

    private FileAttributes resolveAttr(Path path, boolean followLinks) {
        return vfs.resolveInternal(path.toString()).backend()
                .getAttributes(vfs.resolveInternal(path.toString()).path(), followLinks);
    }

    private FSBackend resolveBackend(Path path) {
        return vfs.resolveInternal(path.toString()).backend();
    }

    private String resolveRelative(Path path) {
        return vfs.resolveInternal(path.toString()).path();
    }

    // ===================== NIO BasicFileAttributes 桥接 =====================

    static final class VfsFileAttributes implements BasicFileAttributes {
        private final FileAttributes attr;
        VfsFileAttributes(FileAttributes attr) { this.attr = attr; }
        @Override public boolean isRegularFile() { return attr.regularFile(); }
        @Override public boolean isDirectory() { return attr.directory(); }
        @Override public boolean isSymbolicLink() { return attr.symbolicLink(); }
        @Override public boolean isOther() { return !attr.exists(); }
        @Override public long size() { return attr.size(); }
        @Override public FileTime lastModifiedTime() { return FileTime.fromMillis(attr.lastModifiedTime()); }
        @Override public FileTime lastAccessTime() { return FileTime.fromMillis(attr.lastModifiedTime()); }
        @Override public FileTime creationTime() { return FileTime.fromMillis(attr.creationTime()); }
        @Override public Object fileKey() { return null; }
    }
}
