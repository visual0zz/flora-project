package com.flora.runtime.virtual.filesys.nio;

import com.flora.runtime.virtual.filesys.FileAttributes;
import com.flora.runtime.virtual.filesys.FileOpResult;
import com.flora.runtime.virtual.filesys.FSBackend;
import com.flora.runtime.virtual.filesys.SymlinkFSBackend;
import com.flora.runtime.virtual.filesys.VfsFileSystem;

import java.io.*;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/**
 * 虚拟文件系统的 {@link FileSystemProvider} 实现。
 * <p>核心通过 {@link FSBackend#openChannel} 与后端通信，
 * {@code InputStream}/{@code OutputStream} 由 {@link Channels} 适配而来。</p>
 */
public final class VfsFileSystemProvider extends FileSystemProvider {

    private final VfsFileSystem fs;

    public VfsFileSystemProvider(VfsFileSystem fs) {
        this.fs = fs;
    }

    @Override public String getScheme() { return "vfs"; }

    @Override public FileSystem newFileSystem(URI uri, Map<String, ?> env) { return fs; }
    @Override public FileSystem getFileSystem(URI uri) { return fs; }

    @Override
    public Path getPath(URI uri) {
        return new VfsPath(uri.getPath(), fs);
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
        FileAttributes attr = resolveBackend(path).getAttributes(resolveRelative(path), followLinks);
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
        if ("lastModifiedTime".equals(attribute) && value instanceof FileTime) {
            // VFS 目前不支持修改时间戳，静默忽略
        }
    }

    // ===================== 流（基于 Channel） =====================

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        Set<? extends OpenOption> opts = options.length > 0
                ? Set.of(options)
                : Set.of(StandardOpenOption.READ);
        return Channels.newInputStream(resolveBackend(path).openChannel(resolveRelative(path), opts));
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        Set<? extends OpenOption> opts = options.length > 0
                ? Set.of(options)
                : Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return Channels.newOutputStream(resolveBackend(path).openChannel(resolveRelative(path), opts));
    }

    // ===================== 目录操作 =====================

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
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
        switch (resolveBackend(dir).createDirectory(resolveRelative(dir))) {
            case SUCCESS -> {}
            case ALREADY_EXISTS -> throw new FileAlreadyExistsException(dir.toString());
            default -> throw new IOException("创建目录失败: " + dir);
        }
    }

    // ===================== 通道（直接透传到后端） =====================

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return resolveBackend(path).openChannel(resolveRelative(path), options);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        SeekableByteChannel ch = resolveBackend(path).openChannel(resolveRelative(path), options);
        return new VfsFileChannel(ch);
    }

    // ===================== 删除/拷贝/移动 =====================

    @Override
    public void delete(Path path) throws IOException {
        switch (resolveBackend(path).delete(resolveRelative(path))) {
            case SUCCESS -> {}
            case NOT_FOUND -> throw new NoSuchFileException(path.toString());
            case NOT_EMPTY -> throw new DirectoryNotEmptyException(path.toString());
            default -> throw new IOException("删除失败: " + path);
        }
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return switch (resolveBackend(path).delete(resolveRelative(path))) {
            case SUCCESS -> true;
            case NOT_FOUND -> false;
            default -> { delete(path); yield true; }
        };
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
        switch (resolveBackend(source).rename(resolveRelative(source), resolveRelative(target))) {
            case SUCCESS -> {}
            case ALREADY_EXISTS -> throw new FileAlreadyExistsException(target.toString());
            default -> {
                // fallback: copy + delete
                copy(source, target, options);
                delete(source);
            }
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
        FSBackend backend = resolveBackend(link);
        if (!(backend instanceof SymlinkFSBackend s)) {
            throw new UnsupportedOperationException("后端不支持符号链接: " + backend.getClass().getSimpleName());
        }
        if (!s.createSymbolicLink(resolveRelative(link), target.toString())) {
            throw new FileAlreadyExistsException(link.toString());
        }
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        FSBackend backend = resolveBackend(link);
        if (!(backend instanceof SymlinkFSBackend s)) {
            throw new UnsupportedOperationException("后端不支持符号链接: " + backend.getClass().getSimpleName());
        }
        String target = s.readSymbolicLink(resolveRelative(link));
        return new VfsPath(target, fs);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return resolveAttr(path, true).exists() && resolveAttr(path2, true).exists()
                && path.toRealPath().toString().equals(path2.toRealPath().toString());
    }

    // ===================== 内部辅助 =====================

    private boolean exists(Path path) {
        try { return resolveAttr(path, true).exists(); } catch (Exception e) { return false; }
    }

    private FileAttributes resolveAttr(Path path, boolean followLinks) {
        FSBackendMatch m = fs.resolveInternal(path.toString());
        return m.backend().getAttributes(m.path(), followLinks);
    }

    private FSBackend resolveBackend(Path path) {
        return fs.resolveInternal(path.toString()).backend();
    }

    private String resolveRelative(Path path) {
        return fs.resolveInternal(path.toString()).path();
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
