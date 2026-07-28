package com.flora.os.virtual.file;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * VFS 路径归一化工具。
 * <p>绝对路径、UNIX 风格：处理 {@code ..} / {@code .} / 重复 {@code /} / 尾部 {@code /}。</p>
 */
final class PathUtil {

    private PathUtil() {}

    /** 归一化绝对路径。入口。 */
    static String normalize(String path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("路径不能为空");
        // 确保以 / 开头
        if (!path.startsWith("/")) throw new IllegalArgumentException("路径必须是绝对路径: " + path);

        // 按 / 分割
        String[] parts = path.split("/", -1);
        Deque<String> stack = new ArrayDeque<>();

        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(part);
        }

        if (stack.isEmpty()) return "/";
        return "/" + String.join("/", stack);
    }

    /** 获取父目录路径。 */
    static String parent(String path) {
        String n = normalize(path);
        if (n.equals("/")) return null;
        int idx = n.lastIndexOf('/');
        if (idx == 0) return "/";
        return n.substring(0, idx);
    }

    /** 获取文件名（最后一段）。 */
    static String name(String path) {
        String n = normalize(path);
        if (n.equals("/")) return "";
        int idx = n.lastIndexOf('/');
        return n.substring(idx + 1);
    }

    /** 拼接子路径。 */
    static String resolve(String base, String child) {
        if (child.startsWith("/")) return normalize(child);
        String b = normalize(base);
        if (b.equals("/")) return normalize("/" + child);
        return normalize(b + "/" + child);
    }
}
