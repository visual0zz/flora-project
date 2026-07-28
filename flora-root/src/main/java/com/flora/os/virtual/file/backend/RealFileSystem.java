package com.flora.os.virtual.file.backend;

import com.flora.os.virtual.file.FileAttributes;
import com.flora.os.virtual.file.FSBackend;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 封装 JDK NIO 真实文件系统的后端。
 * <p>将虚拟路径映射到真实文件系统路径。线程安全（JDK 文件操作本身是线程安全的）。</p>
 */
public final class RealFileSystem implements FSBackend {

    private final Path rootDir;

    /**
     * @param rootDir 真实文件系统的根目录（如 {@code Path.of("/tmp/vfs-root")}）
     */
    public RealFileSystem(Path rootDir) {
        this.rootDir = rootDir.normalize().toAbsolutePath();
    }

    private Path realPath(String vfsPath) {
        // vfsPath 是归一化的绝对路径，如 "/foo/bar.txt"
        // 映射到 rootDir/foo/bar.txt
        String relative = vfsPath.startsWith("/") ? vfsPath.substring(1) : vfsPath;
        return rootDir.resolve(relative).normalize();
    }

    @Override
    public FileAttributes getAttributes(String path) {
        Path p = realPath(path);
        try {
            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
            return new FileAttributes(
                    true,
                    attr.isRegularFile(),
                    attr.isDirectory(),
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
    public InputStream read(String path) throws IOException {
        return Files.newInputStream(realPath(path));
    }

    @Override
    public OutputStream write(String path, boolean append) throws IOException {
        Path p = realPath(path);
        Path parent = p.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (append) return Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return Files.newOutputStream(p);
    }

    @Override
    public boolean createFile(String path) throws IOException {
        Path p = realPath(path);
        Path parent = p.getParent();
        if (parent != null) Files.createDirectories(parent);
        return p.toFile().createNewFile();
    }

    @Override
    public boolean createDirectory(String path) throws IOException {
        return realPath(path).toFile().mkdir();
    }

    @Override
    public boolean delete(String path) throws IOException {
        return realPath(path).toFile().delete();
    }

    @Override
    public boolean rename(String source, String dest) throws IOException {
        return realPath(source).toFile().renameTo(realPath(dest).toFile());
    }

    @Override
    public List<String> list(String path) throws IOException {
        Path p = realPath(path);
        if (!Files.isDirectory(p)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(p)) {
            return stream.map(p2 -> p2.getFileName().toString()).toList();
        }
    }
}
