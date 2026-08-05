package com.flora.runtime.virtual.filesys.bridge;

import com.flora.runtime.virtual.filesys.FileAttributes;
import com.flora.runtime.virtual.filesys.FSBackend;
import com.flora.runtime.virtual.filesys.VfsFileSystem;
import com.flora.runtime.virtual.filesys.SymlinkFSBackend;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/**
 * 虚拟文件系统的私有 {@link FileSystemProvider} 实现。
 * <p>由 {@code VfsFileSystem} 构造时创建，只服务该文件系统：NIO 操作通过
 * {@link FSBackend#openChannel} 委托给挂载表解析到的后端，
 * {@code InputStream}/{@code OutputStream} 由 {@link Channels} 适配。</p>
 * <p>不参与 ServiceLoader 注册，也不支持按 URI 创建/查找文件系统——文件系统实例由
 * {@code new VfsFileSystem()} 直接构建，其他代码仅通过 {@code Path} + {@code Files.*}
 * 透明使用。</p>
 */
public final class VfsFileSystemProvider extends FileSystemProvider {

    private final MountTable mountTable;

    /** 由 {@code VfsFileSystem} 构造时调用。 */
    public VfsFileSystemProvider(MountTable mountTable) {
        this.mountTable = mountTable;
    }

    @Override public String getScheme() { return "vfs"; }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        throw new UnsupportedOperationException("VFS 不支持按 URI 创建文件系统，请直接 new VfsFileSystem()");
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException("VFS 不参与 URI 发现");
    }

    @Override
    public @NotNull Path getPath(URI uri) {
        throw new UnsupportedOperationException("VFS 不参与 URI 发现");
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
        BackendRef ref = resolveOne(path);
        FileAttributes attr = ref.backend.getAttributes(ref.relative, followLinks);
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
        BackendRef ref = resolveOne(path);
        return Channels.newInputStream(ref.backend.openChannel(ref.relative, opts));
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        Set<? extends OpenOption> opts = options.length > 0
                ? Set.of(options)
                : Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        BackendRef ref = resolveOne(path);
        return Channels.newOutputStream(ref.backend.openChannel(ref.relative, opts));
    }

    // ===================== 目录操作 =====================

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        BackendRef ref = resolveOne(dir);
        List<String> names = ref.backend.list(ref.relative);
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
        BackendRef ref = resolveOne(dir);
        switch (ref.backend.createDirectory(ref.relative)) {
            case SUCCESS -> {}
            case ALREADY_EXISTS -> throw new FileAlreadyExistsException(dir.toString());
            default -> throw new IOException("创建目录失败: " + dir);
        }
    }

    // ===================== 通道 =====================

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        BackendRef ref = resolveOne(path);
        return ref.backend.openChannel(ref.relative, options);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        BackendRef ref = resolveOne(path);
        return new VfsFileChannel(ref.backend.openChannel(ref.relative, options));
    }

    // ===================== 删除/拷贝/移动 =====================

    @Override
    public void delete(Path path) throws IOException {
        BackendRef ref = resolveOne(path);
        switch (ref.backend.delete(ref.relative)) {
            case SUCCESS -> {}
            case NOT_FOUND -> throw new NoSuchFileException(path.toString());
            case NOT_EMPTY -> throw new DirectoryNotEmptyException(path.toString());
            default -> throw new IOException("删除失败: " + path);
        }
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        BackendRef ref = resolveOne(path);
        return switch (ref.backend.delete(ref.relative)) {
            case SUCCESS -> true;
            case NOT_FOUND -> false;
            default -> { delete(path); yield true; }
        };
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = List.of(options).contains(StandardCopyOption.REPLACE_EXISTING);
        BackendRef targetRef = resolveOne(target);
        if (!replaceExisting && targetRef.backend.getAttributes(targetRef.relative).exists()) {
            throw new FileAlreadyExistsException(target.toString());
        }
        try (InputStream in = newInputStream(source); OutputStream out = newOutputStream(target)) {
            in.transferTo(out);
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = List.of(options).contains(StandardCopyOption.REPLACE_EXISTING);
        BackendRef targetRef = resolveOne(target);
        if (!replaceExisting && targetRef.backend.getAttributes(targetRef.relative).exists()) {
            throw new FileAlreadyExistsException(target.toString());
        }
        BackendRef sourceRef = resolveOne(source);
        switch (sourceRef.backend.rename(sourceRef.relative, targetRef.relative)) {
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
        BackendRef ref = resolveOne(link);
        FSBackend backend = ref.backend;
        if (!(backend instanceof SymlinkFSBackend s)) {
            throw new UnsupportedOperationException("后端不支持符号链接: " + backend.getClass().getSimpleName());
        }
        if (!s.createSymbolicLink(ref.relative, target.toString())) {
            throw new FileAlreadyExistsException(link.toString());
        }
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        BackendRef ref = resolveOne(link);
        FSBackend backend = ref.backend;
        if (!(backend instanceof SymlinkFSBackend s)) {
            throw new UnsupportedOperationException("后端不支持符号链接: " + backend.getClass().getSimpleName());
        }
        return new VfsPath(s.readSymbolicLink(ref.relative),
                (VfsFileSystem) link.getFileSystem());
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return resolveAttr(path, true).exists() && resolveAttr(path2, true).exists()
                && path.toRealPath().toString().equals(path2.toRealPath().toString());
    }

    // ===================== 内部辅助 =====================

    /** 单次解析的结果缓存，消除同一个方法内多次挂载解析的 TOCTOU。 */
    private record BackendRef(FSBackend backend, String relative) {}

    /** 返回本 provider 服务的挂载表，并校验文件系统已打开。 */
    private MountTable tableOf(Path path) {
        if (!path.getFileSystem().isOpen()) throw new ClosedFileSystemException();
        return mountTable;
    }

    private BackendRef resolveOne(Path path) {
        FSBackendMatch m = tableOf(path).resolve(path.toString());
        return new BackendRef(m.backend(), m.path());
    }

    private FileAttributes resolveAttr(Path path, boolean followLinks) {
        FSBackendMatch m = tableOf(path).resolve(path.toString());
        return m.backend().getAttributes(m.path(), followLinks);
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
