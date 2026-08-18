package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.json.model.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 新建仓库（见设计"形态与启动"）。
 * <ul>
 *   <li><b>普通仓库</b>：在目标目录直接建数据块结构（该目录即 vault 根）。</li>
 *   <li><b>独立仓库</b>：把应用自身复制为 {@code { lib/, data/, start.cmd, config.json }}，
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
     * 新建独立仓库：把应用自身（lib/ + 启动脚本 + 配置）复制到目标目录，并建 data/。
     * 返回独立仓库的 vault 根（{@code dir/data}）。应用自身不打开它。
     *
     * @param dir         独立仓库目标目录
     * @param libSource   当前应用 lib 目录（全量 jar 所在）
     * @param appConfig   应用级配置（复制为仓库级，不含密钥）
     */
    public static Path createStandalone(Path dir, Path libSource, JsonObject appConfig) throws IOException {
        Files.createDirectories(dir);
        Path lib = dir.resolve("lib");
        Path data = dir.resolve("data");
        Files.createDirectories(lib);
        Files.createDirectories(data);
        // 复制全量 jar
        if (libSource != null && Files.isDirectory(libSource)) {
            try (var stream = Files.list(libSource)) {
                for (Path jar : stream.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                    Files.copy(jar, lib.resolve(jar.getFileName()));
                }
            }
        }
        // 写启动脚本
        writeScript(dir);
        // 复制应用级配置为仓库级
        VaultForm.writeRepoConfig(dir, appConfig);
        return data;
    }

    /** 写入跨平台启动脚本（双头脚本：bash 段 + :windows cmd 段，module-path 启动；经 -Dflora.repo 传入仓库根）。 */
    private static void writeScript(Path dir) throws IOException {
        // 脚本位于独立仓库根，config.json 也在仓库根；仓库根经 -Dflora.repo 传给 Main。
        // 必须用 LF 换行（bash 段在 CRLF 下无法解析）；cmd 亦兼容 LF 批处理。
        String script = "#!/usr/bin/env bash\n"
                + "@goto :windows || true\n"
                + "# Cross-platform launcher for standalone repo; repo root is this script's directory.\n"
                + "cd \"$(dirname \"$0\")\" || exit 1\n"
                + "exec java -Dflora.repo=\"$PWD\" --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main \"$@\" || exit 1\n"
                + "\n"
                + ":windows\n"
                + "@echo off\n"
                + "cd /d \"%~dp0\"\n"
                + "java -Dflora.repo=%CD% --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main %*\n";
        Path cmdFile = dir.resolve("start.cmd");
        Files.writeString(cmdFile, script);
        cmdFile.toFile().setExecutable(true);
    }
}
