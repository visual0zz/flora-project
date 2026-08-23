package com.flora.playground;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 HTML 中提取内嵌图标（{@code data:image/...} data URI）导出为独立图标文件。
 * <p>
 * 输入一个 HTML 文件，扫描全部 {@code <img src="data:image/...">}（含其它标签里的
 * {@code data:image/...} src），按 MIME 类型确定扩展名，逐个写为独立文件。
 * 支持 base64 编码（如 {@code data:image/png;base64,....}）与 URL 编码文本
 * （如 SVG 的 {@code data:image/svg+xml,<svg...>}）；相同 data URI 只导出一次。
 * 命名优先用 {@code alt} 属性（清洗非法字符），重复则追加序号；无 alt 用 {@code icon}。
 */
public final class HtmlIconExtractor {

    /** 匹配一个完整的 {@code <img ...>} 标签（含自闭合）。 */
    private static final Pattern IMG_TAG =
            Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /** 匹配标签内的 src 属性值（含引号）。 */
    private static final Pattern SRC_ATTR =
            Pattern.compile("src\\s*=\\s*(\"[^\"]*\"|'[^']*')", Pattern.CASE_INSENSITIVE);
    /** 匹配标签内的 alt 属性值（含引号）。 */
    private static final Pattern ALT_ATTR =
            Pattern.compile("alt\\s*=\\s*(\"[^\"]*\"|'[^']*')", Pattern.CASE_INSENSITIVE);

    private HtmlIconExtractor() {
    }

    /**
     * 从 HTML 中提取全部内嵌图标并写入输出目录，返回写出的文件名列表。
     *
     * @param html   输入 HTML 文件
     * @param outDir 输出目录（不存在则创建）
     */
    public static List<String> extract(Path html, Path outDir) throws IOException {
        String text = Files.readString(html);
        Files.createDirectories(outDir);

        // data URI -> 目标文件名（去重，保序）
        Map<String, String> toWrite = new LinkedHashMap<>();
        Matcher img = IMG_TAG.matcher(text);
        while (img.find()) {
            String tag = img.group();
            String src = attrValue(SRC_ATTR, tag);
            if (src == null || !src.startsWith("data:image/")) {
                continue;
            }
            String uri = src;
            String baseName = sanitize(attrValue(ALT_ATTR, tag));
            if (baseName == null || baseName.isBlank()) {
                baseName = "icon";
            }
            if (!toWrite.containsKey(uri)) {
                toWrite.put(uri, uniqueName(baseName, toWrite.values()));
            }
        }

        List<String> written = new ArrayList<>();
        int i = 1;
        for (Map.Entry<String, String> e : toWrite.entrySet()) {
            Decoded dec = decodeDataUri(e.getKey());
            Path file = outDir.resolve(e.getValue() + "." + dec.extension);
            Files.write(file, dec.data);
            written.add(file.getFileName().toString());
            i++;
        }
        return written;
    }

    // ===== 解析 =====

    private record Decoded(byte[] data, String extension) {
    }

    /** 解析 data URI：{@code data:<mediatype>[;base64],<data>}。 */
    private static Decoded decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("invalid data uri: missing ','");
        }
        String meta = uri.substring(0, comma);
        String data = uri.substring(comma + 1);
        byte[] bytes;
        if (meta.toLowerCase().contains(";base64")) {
            bytes = Base64.getDecoder().decode(data);
        } else {
            // 非 base64：SVG 等文本，URL 编码（空格/引号/中文可能被编码）
            bytes = URLDecoder.decode(data, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8);
        }
        return new Decoded(bytes, extensionOf(meta));
    }

    /** MIME 类型 → 文件扩展名。 */
    private static String extensionOf(String meta) {
        String mime = meta.substring(meta.indexOf(':') + 1);
        int semi = mime.indexOf(';');
        if (semi >= 0) {
            mime = mime.substring(0, semi);
        }
        return switch (mime.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            case "image/bmp" -> "bmp";
            case "image/x-icon", "image/vnd.microsoft.icon" -> "ico";
            case "image/avif" -> "avif";
            default -> "bin";
        };
    }

    // ===== 工具 =====

    /** 取属性值（去引号），不存在返回 null。 */
    private static String attrValue(Pattern attr, String tag) {
        Matcher m = attr.matcher(tag);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1);
        return raw.substring(1, raw.length() - 1);
    }

    /** 文件名清洗：保留字母数字与常见符号，其余替换为下划线。 */
    private static String sanitize(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = name.replaceAll("[^\\w\\-.]", "_").trim();
        return cleaned.isBlank() ? "icon" : cleaned;
    }

    /** 生成不冲突的文件名（含序号），保留原有名 + 序号。 */
    private static String uniqueName(String baseName, Iterable<String> existing) {
        String candidate = baseName;
        int n = 2;
        while (contains(existing, candidate)) {
            candidate = baseName + "-" + n++;
        }
        return candidate;
    }

    private static boolean contains(Iterable<String> values, String target) {
        for (String v : values) {
            if (v.equals(target)) {
                return true;
            }
        }
        return false;
    }

    // ===== 入口 =====

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("用法: HtmlIconExtractor <html文件> [输出目录]");
            System.err.println("  输出目录缺省为 html 同目录下的 '<html名>-icons/'");
            System.exit(1);
        }
        Path html = Path.of(args[0]).toAbsolutePath();
        Path outDir = args.length > 1
                ? Path.of(args[1]).toAbsolutePath()
                : html.getParent().resolve(stem(html.getFileName().toString()) + "-icons");
        List<String> files = extract(html, outDir);
        System.out.println("提取 " + files.size() + " 个图标到 " + outDir);
        for (String f : files) {
            System.out.println("  " + f);
        }
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
