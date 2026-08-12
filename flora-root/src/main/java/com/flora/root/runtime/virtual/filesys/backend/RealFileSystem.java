package com.flora.root.runtime.virtual.filesys.backend;

import com.flora.root.runtime.virtual.filesys.FileAttributes;
import com.flora.root.runtime.virtual.filesys.FileOpResult;
import com.flora.root.runtime.virtual.filesys.SymlinkFSBackend;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 封装 JDK NIO 真实文件系统的后端。
 * <p>将虚拟路径映射到真实文件系统路径。线程安全（JDK 文件操作本身是线程安全的）。</p>
 */
public final class RealFileSystem implements SymlinkFSBackend {

    private final Path rootDir;

    /**
     * @param rootDir 真实文件系统的根目录（如 {@code Path.of("/tmp/vfs-root")}）
     */
    public RealFileSystem(Path rootDir) {
        this.rootDir = rootDir.normalize().toAbsolutePath();
    }

    private Path realPath(String vfsPath) {
        String relative = vfsPath.startsWith("/") ? vfsPath.substring(1) : vfsPath;
        return rootDir.resolve(relative).normalize();
    }

    @Override
    public FileAttributes getAttributes(String path, boolean followLinks) {
        Path p = realPath(path);
        try {
            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
            return new FileAttributes(
                    true,
                    attr.isRegularFile(),
                    attr.isDirectory(),
                    attr.isSymbolicLink(),
                    attr.size(),
                    attr.lastModifiedTime().toMillis(),
                    attr.creationTime().toMillis(),
                    Files.isReadable(p),
                    Files.isWritable(p)
            );
        } catch (IOException e) {
            return FileAttributes.NOT_FOUND;
        }
    }

    @Override
    public SeekableByteChannel openChannel(String path, Set<? extends OpenOption> options) throws IOException {
        Path p = realPath(path);
        Path parent = p.getParent();
        if (parent != null && needsParent(options)) {
            Files.createDirectories(parent);
        }
        return Files.newByteChannel(p, options);
    }

    private static boolean needsParent(Set<? extends OpenOption> options) {
        return options.contains(StandardOpenOption.CREATE)
                || options.contains(StandardOpenOption.CREATE_NEW)
                || options.contains(StandardOpenOption.WRITE)
                || options.contains(StandardOpenOption.APPEND);
    }

    @Override
    public FileOpResult createDirectory(String path) throws IOException {
        return realPath(path).toFile().mkdir() ? FileOpResult.SUCCESS : FileOpResult.ALREADY_EXISTS;
    }

    @Override
    public FileOpResult delete(String path) throws IOException {
        Path p = realPath(path);
        if (!Files.exists(p)) return FileOpResult.NOT_FOUND;
        if (Files.isDirectory(p) && !isEmptyDir(p)) return FileOpResult.NOT_EMPTY;
        return p.toFile().delete() ? FileOpResult.SUCCESS : FileOpResult.OTHER_FAILED;
    }

    private boolean isEmptyDir(Path p) {
        try (var s = Files.list(p)) { return s.findAny().isEmpty(); }
        catch (IOException e) { return false; }
    }

    @Override
    public FileOpResult rename(String source, String dest) throws IOException {
        Path src = realPath(source);
        if (!Files.exists(src)) return FileOpResult.NOT_FOUND;
        if (Files.exists(realPath(dest))) return FileOpResult.ALREADY_EXISTS;
        return src.toFile().renameTo(realPath(dest).toFile()) ? FileOpResult.SUCCESS : FileOpResult.OTHER_FAILED;
    }

    @Override
    public List<String> list(String path) throws IOException {
        Path p = realPath(path);
        if (!Files.isDirectory(p)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(p)) {
            return stream.map(p2 -> p2.getFileName().toString()).toList();
        }
    }

    @Override
    public boolean createSymbolicLink(String path, String target) throws IOException {
        Path link = realPath(path);
        Path parent = link.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.createSymbolicLink(link, Path.of(target));
        return true;
    }

    @Override
    public String readSymbolicLink(String path) throws IOException {
        return Files.readSymbolicLink(realPath(path)).toString();
    }
}
