package com.flora.os.virtual.file;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟文件句柄。
 * <p>不可变对象，每次操作委托给对应 {@link FSBackend}。不是数据容器。</p>
 */
public final class VFile {

    private final String vfsPath;      // 完整虚拟路径（如 /mem/a/b.txt）
    private final String backendPath;  // 后端相对路径（如 /a/b.txt）
    private final FSBackend backend;

    VFile(String vfsPath, String backendPath, FSBackend backend) {
        this.vfsPath = vfsPath;
        this.backendPath = backendPath;
        this.backend = backend;
    }

    // ===================== 导航 =====================

    /** 文件名（最后一段）。空串表示根目录。 */
    public String getName() {
        return PathUtil.name(vfsPath);
    }

    /** 完整归一化虚拟路径。 */
    public String getPath() {
        return vfsPath;
    }

    /** 父目录句柄；根目录返回 null。 */
    public VFile getParent() {
        String p = PathUtil.parent(vfsPath);
        if (p == null) return null;
        String bp = PathUtil.parent(backendPath);
        return bp != null ? new VFile(p, bp, backend) : null;
    }

    /** 拼接子路径。 */
    public VFile resolve(String subpath) {
        return new VFile(
                PathUtil.resolve(vfsPath, subpath),
                PathUtil.resolve(backendPath, subpath),
                backend);
    }

    /** 同级文件（替换最后一段）。 */
    public VFile resolveSibling(String name) {
        String p = PathUtil.parent(vfsPath);
        String bp = PathUtil.parent(backendPath);
        if (p == null) return new VFile("/" + name, "/" + name, backend);
        return new VFile(
                PathUtil.resolve(p, name),
                PathUtil.resolve(bp, name),
                backend);
    }

    // ===================== 元数据 =====================

    /** 文件是否存在。 */
    public boolean exists() {
        return backend.getAttributes(backendPath).exists();
    }

    /** 批量获取文件元数据。 */
    public FileAttributes getAttributes() {
        return backend.getAttributes(backendPath);
    }

    /** 是否为普通文件。 */
    public boolean isRegularFile() {
        return backend.getAttributes(backendPath).regularFile();
    }

    /** 是否为目录。 */
    public boolean isDirectory() {
        return backend.getAttributes(backendPath).directory();
    }

    // ===================== 读取 =====================

    /** 打开输入流。 */
    public InputStream openInputStream() throws IOException {
        return backend.read(backendPath);
    }

    /** 读入全部字节。 */
    public byte[] readAllBytes() throws IOException {
        try (InputStream in = backend.read(backendPath)) {
            return in.readAllBytes();
        }
    }

    /** 读入全部文本（UTF-8）。 */
    public String readString() throws IOException {
        return new String(readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    // ===================== 写入 =====================

    /** 打开输出流（覆盖模式）。 */
    public OutputStream openOutputStream() throws IOException {
        return backend.write(backendPath, false);
    }

    /** 打开输出流（指定追加/覆盖）。 */
    public OutputStream openOutputStream(boolean append) throws IOException {
        return backend.write(backendPath, append);
    }

    /** 写入全部字节（覆盖）。 */
    public void writeBytes(byte[] data) throws IOException {
        try (OutputStream out = backend.write(backendPath, false)) {
            out.write(data);
        }
    }

    /** 写入全部文本（UTF-8，覆盖）。 */
    public void writeString(String content) throws IOException {
        writeBytes(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ===================== 目录操作 =====================

    /** 列出目录下的文件（非递归）。 */
    public List<VFile> list() throws IOException {
        List<String> names = backend.list(backendPath);
        List<VFile> files = new ArrayList<>(names.size());
        for (String n : names) {
            files.add(new VFile(
                    PathUtil.resolve(vfsPath, n),
                    PathUtil.resolve(backendPath, n),
                    backend));
        }
        return files;
    }

    /** 创建目录（父目录必须存在）。 */
    public boolean mkDir() throws IOException {
        return backend.createDirectory(backendPath);
    }

    /** 创建目录，不存在的父目录一并创建。 */
    public boolean mkDirs() throws IOException {
        String pbp = PathUtil.parent(backendPath);
        if (pbp != null) {
            VFile parent = new VFile(
                    PathUtil.parent(vfsPath),
                    pbp,
                    backend);
            if (!parent.exists()) parent.mkDirs();
        }
        return backend.createDirectory(backendPath);
    }

    // ===================== 文件操作 =====================

    /** 创建空文件（含父目录）。 */
    public boolean createFile() throws IOException {
        return backend.createFile(backendPath);
    }

    /** 删除文件或空目录。 */
    public boolean delete() throws IOException {
        return backend.delete(backendPath);
    }

    /** 重命名为目标路径。目标不能已存在。 */
    public boolean renameTo(VFile dest) throws IOException {
        return backend.rename(backendPath, dest.backendPath);
    }

    // ===================== 拷贝/移动 =====================

    /** 拷贝到目标。 */
    public void copyTo(VFile dest, boolean replaceExisting) throws IOException {
        if (!replaceExisting && dest.exists()) {
            throw new IOException("目标已存在: " + dest.vfsPath);
        }
        try (InputStream in = backend.read(backendPath);
             OutputStream out = dest.backend.write(dest.backendPath, false)) {
            in.transferTo(out);
        }
    }

    /** 移动到目标。 */
    public void moveTo(VFile dest, boolean replaceExisting) throws IOException {
        if (!replaceExisting && dest.exists()) {
            throw new IOException("目标已存在: " + dest.vfsPath);
        }
        if (!backend.rename(backendPath, dest.backendPath)) {
            copyTo(dest, replaceExisting);
            backend.delete(backendPath);
        }
    }

    // ===================== Object =====================

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof VFile f && vfsPath.equals(f.vfsPath));
    }

    @Override
    public int hashCode() {
        return vfsPath.hashCode();
    }

    @Override
    public String toString() {
        return vfsPath;
    }
}
