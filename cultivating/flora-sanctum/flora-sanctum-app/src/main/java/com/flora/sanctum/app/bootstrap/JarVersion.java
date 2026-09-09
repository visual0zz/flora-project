package com.flora.sanctum.app.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 从 jar 解析版本并做兼容的语义比较（用于独立仓库运行时升级前的版本校验）。
 *
 * <p>版本来源灵活：优先读 jar Manifest 的 {@code Implementation-Version}
 * （Maven 构建产物自带），取不到再回退到文件名 {@code <artifact>-<version>.jar}。
 * 两者都取不到则视为未知。
 *
 * <p>比较宽松：按 {@code .} 切分后逐段比较，纯数字段按数值比较
 * （故 {@code 0.1} 与 {@code 0.1.0} 等价、{@code 10.0} 大于 {@code 2.0}）；
 * 额外的非数字段（如 {@code -SNAPSHOT} 之类限定符）视为更旧（预发布）。
 * 任一端未知（取不到版本）时，调用方应视为"无法判定更高"而放行，避免误拦截。
 */
final class JarVersion {

    private JarVersion() {
    }

    /** 从单个 jar 解析版本：Manifest 优先，其次文件名；都取不到返回空。 */
    static Optional<String> ofJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        Optional<String> fromManifest = manifestVersion(jar);
        if (fromManifest.isPresent()) {
            return fromManifest;
        }
        return fileNameVersion(jar);
    }

    private static Optional<String> manifestVersion(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest mf = jf.getManifest();
            if (mf == null) {
                return Optional.empty();
            }
            String v = mf.getMainAttributes().getValue("Implementation-Version");
            if (v != null && !v.isBlank()) {
                return Optional.of(v.trim());
            }
        } catch (IOException ignore) {
            // 读不到 manifest 时回退文件名
        }
        return Optional.empty();
    }

    private static Optional<String> fileNameVersion(Path jar) {
        String name = jar.getFileName() == null ? "" : jar.getFileName().toString();
        if (!name.endsWith(".jar")) {
            return Optional.empty();
        }
        String base = name.substring(0, name.length() - 4);
        int dash = base.lastIndexOf('-');
        if (dash <= 0) {
            return Optional.empty();
        }
        String ver = base.substring(dash + 1);
        return ver.isEmpty() ? Optional.empty() : Optional.of(ver);
    }

    /**
     * 从一组 jar 中挑选代表"应用版本"的版本号：优先取主构件
     * （文件名以 {@code primaryArtifact + "-"} 开头，如 {@code flora-sanctum-app-0.1.0.jar}）；
     * 取不到再回退到 flora-sanctum 系列 jar 中的最大版本。都取不到返回空。
     */
    static Optional<String> ofBundle(List<Path> jars, String primaryArtifact) {
        Optional<String> primary = jars.stream()
                .filter(j -> fileNameStarts(j, primaryArtifact + "-"))
                .map(JarVersion::ofJar)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (primary.isPresent()) {
            return primary;
        }
        return jars.stream()
                .filter(j -> fileNameStarts(j, "flora-sanctum-"))
                .map(JarVersion::ofJar)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(JarVersion::compare);
    }

    private static boolean fileNameStarts(Path jar, String prefix) {
        String name = jar.getFileName() == null ? "" : jar.getFileName().toString();
        return name.startsWith(prefix) && name.endsWith(".jar");
    }

    /**
     * 语义比较：a<b 返回负，相等 0，a>b 正。
     * 任一为 null/空按"未知"处理（调用方据此决定是否放行）。
     */
    static int compare(String a, String b) {
        List<String> ta = tokens(a == null ? "" : a);
        List<String> tb = tokens(b == null ? "" : b);
        int n = Math.max(ta.size(), tb.size());
        for (int i = 0; i < n; i++) {
            String x = i < ta.size() ? ta.get(i) : "0";
            String y = i < tb.size() ? tb.get(i) : "0";
            int c = compareToken(x, y);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static List<String> tokens(String v) {
        List<String> out = new ArrayList<>();
        for (String part : v.split("[.\\-+]")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private static int compareToken(String x, String y) {
        Long xi = parseLong(x);
        Long yi = parseLong(y);
        if (xi != null && yi != null) {
            return Long.compare(xi, yi);
        }
        if (xi != null) {
            return 1; // 数字段 > 限定符
        }
        if (yi != null) {
            return -1;
        }
        return x.compareToIgnoreCase(y);
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
