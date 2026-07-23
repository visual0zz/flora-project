package com.flora.data;

import com.flora.java.CheckUtil;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * URL 编解码与路径规范化工具类。
 * <p>提供 URL 的 UTF-8 编解码、路径格式化及协议提取等静态方法。</p>
 */
public final class UrlUtil {

    private UrlUtil() {
    }

    /**
     * 对 URL 进行 UTF-8 编码。
     *
     * @param url 待编码的 URL，不可为 null
     * @return 编码后的字符串
     */
    public static String encode(String url) {
        CheckUtil.notNull(url, "URL 不能为空");
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    /**
     * 对 URL 进行 UTF-8 解码。
     *
     * @param url 待解码的 URL，不可为 null
     * @return 解码后的字符串
     */
    public static String decode(String url) {
        CheckUtil.notNull(url, "URL 不能为空");
        return URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    /**
     * 规范化路径分隔符：将反斜杠替换为正斜杠、压缩连续斜杠、去除末尾斜杠。
     *
     * @param path 原始路径（可为 null 或空串）
     * @return 规范化后的路径，传入 null 或空串时原样返回
     */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String normalized = path.replace('\\', '/')
                .replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 将路径中的反斜杠替换为正斜杠，转换为 Unix 风格路径。
     *
     * @param path 原始路径（可为 null）
     * @return Unix 风格路径，传入 null 时返回 null
     */
    public static String toUnixPath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }

    /**
     * 从 URL 中提取协议名（"://" 之前的部分）。
     *
     * @param url 完整的 URL 字符串
     * @return 协议名（如 "http"、"https"），未找到协议时返回 null
     */
    public static String getProtocol(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int colonIndex = url.indexOf(':');
        if (colonIndex > 0) {
            return url.substring(0, colonIndex);
        }
        return null;
    }
}
