package com.flora.osmetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 编码检查器：扫描源码目录，发现非 UTF-8 编码或包含 GBK 残留字节
 * 的 .java / .ramet 文件，输出错误并终止构建。
 * <p>
 * 用法：java com.flora.osmetes.EncodingChecker {@code <sourceRoot>}
 */
public final class EncodingChecker {

    private EncodingChecker() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("用法: EncodingChecker <sourceRoot>");
            throw new RuntimeException("缺少参数 sourceRoot");
        }
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.out.println("[flora-osmetes] 跳过（目录不存在）: " + root);
            return;
        }
        List<String> errors = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".java") || name.endsWith(".ramet");
                    })
                    .forEach(p -> checkFile(p, root, errors));
        }
        if (errors.isEmpty()) {
            System.out.println("[flora-osmetes] 编码检查通过");
            return;
        } else {
            System.out.println("[flora-osmetes] 编码检查失败：");
            for (String e : errors) {
                System.out.println("  " + e);
            }
            throw new RuntimeException("编码检查失败，共 " + errors.size() + " 个错误");
        }
    }

    private static void checkFile(Path file, Path root, List<String> errors) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            errors.add(relativize(file, root) + "  读取失败: " + e.getMessage());
            return;
        }
        // 检查是否为有效的 UTF-8
        String text;
        try {
            text = new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            errors.add(relativize(file, root) + "  非 UTF-8 编码");
            return;
        }
        // 检查解码后的 C1 控制字符（U+0080-U+009F）。
        // 这些字符在 UTF-8 解码后不应出现——0x80-0x9F 是合法的 UTF-8 续字节，
        // 但如果一个 GBK 文件被误读为 Latin-1，其高位字节会被映射到 U+0080-U+00FF。
        // 解码为 UTF-8 字符串后仍残留 C1 控制字符，说明文件编码有问题。
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (0x80 <= ch && ch <= 0x9F) {
                errors.add(relativize(file, root) + ":" + (i + 1)
                        + "  包含 C1 控制字符 U+" + String.format("%04X", (int) ch)
                        + "（文件可能不是 UTF-8 编码）");
                return;
            }
        }
    }

    private static String relativize(Path file, Path root) {
        Path rel = root.relativize(file);
        return rel.toString().replace('\\', '/');
    }
}
