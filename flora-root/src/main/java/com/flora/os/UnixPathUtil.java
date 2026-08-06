package com.flora.os;

import com.flora.tag.ModuleEntry;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * UNIX 风格绝对路径的归一化与操作工具。
 * <p>处理 {@code ..} / {@code .} / 重复 {@code /} / 尾部 {@code /}。</p>
 */
@ModuleEntry
public final class UnixPathUtil {

    private UnixPathUtil() {}

    /** 归一化绝对路径。 */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("路径不能为空");
        if (!path.startsWith("/")) throw new IllegalArgumentException("路径必须是绝对路径: " + path);

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

    /** 获取父目录路径。根目录返回 null。 */
    public static String parent(String path) {
        String n = normalize(path);
        if (n.equals("/")) return null;
        int idx = n.lastIndexOf('/');
        if (idx == 0) return "/";
        return n.substring(0, idx);
    }

    /** 获取文件名（最后一段）。根目录返回空串。 */
    public static String name(String path) {
        String n = normalize(path);
        if (n.equals("/")) return "";
        int idx = n.lastIndexOf('/');
        return n.substring(idx + 1);
    }

    /** 拼接子路径。 */
    public static String resolve(String base, String child) {
        if (child.startsWith("/")) return normalize(child);
        String b = normalize(base);
        if (b.equals("/")) return normalize("/" + child);
        return normalize(b + "/" + child);
    }
}
