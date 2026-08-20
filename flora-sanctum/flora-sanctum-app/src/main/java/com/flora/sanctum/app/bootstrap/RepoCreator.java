package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.json.model.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 新建仓库（见设计"形态与启动"）。
 * <ul>
 *   <li><b>普通仓库</b>：在目标目录直接建数据块结构（两层目录，该目录即 vault 根）。</li>
 *   <li><b>独立仓库</b>：把应用自身复制为 {@code { standalone.json, data/, lib/, start.cmd }}，
 *       复制应用级配置为仓库级配置（不含密钥等加密信息），之后可由该仓库自己的脚本启动。</li>
 * </ul>
 */
public final class RepoCreator {

    private RepoCreator() {
    }

    /** 新建普通仓库：目标目录直接作为 vault 根。返回该目录。 */
    public static Path createNormal(Path dir) throws IOException {
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * 新建独立仓库：把应用自身（lib/ + 启动脚本 + standalone.json）复制到目标目录，并建 data/。
     * 返回独立仓库的 vault 根（{@code dir/data}）。应用自身不打开它。
     *
     * @param dir       独立仓库目标目录
     * @param appConfig 应用级配置（复制为仓库级，不含密钥）
     */
    public static Path createStandalone(Path dir, JsonObject appConfig) throws IOException {
        Files.createDirectories(dir);
        Path lib = dir.resolve("lib");
        Path data = dir.resolve("data");
        Files.createDirectories(lib);
        Files.createDirectories(data);
        // 复制应用自身 jar：以主类 jar 所在目录为源（module-path 分发目录 / fat jar 所在目录）
        Path libSource = mainJarDirectory();
        if (libSource != null && Files.isDirectory(libSource)) {
            try (var stream = Files.list(libSource)) {
                for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                    Files.copy(jar, lib.resolve(jar.getFileName()));
                }
            }
        }
        writeScript(dir);
        VaultForm.writeRepoConfig(dir, appConfig);
        return data;
    }

    /**
     * 定位应用自身 jar 目录（构建独立仓库的 lib/ 复制源）。按多种启动方式兜底：
     * 1) 主类 CodeSource（module-path 的 lib/ 目录 / classpath 的 jar 目录）；
     * 2) 回退到当前工作目录的 lib/（独立仓库分发形态，脚本在仓库根运行）。
     */
    static Path mainJarDirectory() {
        Path fromCode = codeSourceDirectory();
        if (fromCode != null && Files.isDirectory(fromCode)) {
            return fromCode;
        }
        Path cwdLib = Path.of("").toAbsolutePath().normalize().resolve("lib");
        if (Files.isDirectory(cwdLib)) {
            return cwdLib;
        }
        return fromCode;
    }

    private static Path codeSourceDirectory() {
        try {
            var loc = RepoCreator.class.getProtectionDomain().getCodeSource();
            if (loc == null || loc.getLocation() == null) {
                return null;
            }
            Path p = Path.of(loc.getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入跨平台启动脚本（双头脚本：bash 段 + :windows cmd 段，module-path 启动）。
     * 依赖本地 JRE（JAVA_HOME 优先，其次 PATH 的 java）；git 为可选（运行时探测）。
     * standalone.json 在仓库根，jar 启动时自行判定孤立形态。
     */
    private static void writeScript(Path dir) throws IOException {
        // 必须用 LF 换行（bash 段在 CRLF 下无法解析）；cmd 亦兼容 LF 批处理。
        String script = "#!/usr/bin/env bash\n"
                + "@goto :windows || true\n"
                + "# Cross-platform launcher for standalone repo. Requires local JRE; git is optional.\n"
                + "cd \"$(dirname \"$0\")\" || exit 1\n"
                + "if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then\n"
                + "  JAVA=\"$JAVA_HOME/bin/java\"\n"
                + "else\n"
                + "  JAVA=java\n"
                + "fi\n"
                + "exec \"$JAVA\" --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main \"$@\" || exit 1\n"
                + "\n"
                + ":windows\n"
                + "@echo off\n"
                + "cd /d \"%~dp0\"\n"
                + "if defined JAVA_HOME (\n"
                + "  set \"JAVA=%JAVA_HOME%\\bin\\java.exe\"\n"
                + "  if not exist \"%JAVA%\" set \"JAVA=java\"\n"
                + ") else (\n"
                + "  set \"JAVA=java\"\n"
                + ")\n"
                + "\"%JAVA%\" --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main %*\n";
        Path cmdFile = dir.resolve("start.cmd");
        Files.writeString(cmdFile, script);
        cmdFile.toFile().setExecutable(true);
    }
}
