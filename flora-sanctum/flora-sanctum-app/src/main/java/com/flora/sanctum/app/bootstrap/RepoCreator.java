package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.io.IOException;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 新建仓库（见设计"形态与启动"）。
 * <ul>
 *   <li><b>普通仓库</b>：在目标目录直接建数据块结构（两层目录，该目录即 vault 根）。</li>
 *   <li><b>独立仓库</b>：把应用自身复制为 {@code { config.json, lib/, edit, edit.bat }}，
 *       复制应用级配置为仓库级配置（不含密钥等加密信息），之后可由该仓库自己的 edit 脚本启动。</li>
 * </ul>
 */
public final class RepoCreator {

    private static final Logger LOG = LoggerFactory.getLogger(RepoCreator.class);

    private RepoCreator() {
    }

    /** 新建普通仓库：目标目录直接作为 vault 根。返回该目录。 */
    public static Path createNormal(Path dir) throws IOException {
        LOG.info("Creating normal repository at {}", dir);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * 新建独立仓库：把应用自身（lib/ + edit 脚本 + config.json）复制到目标目录，
     * 数据块（两层目录 + md）直接建在仓库根（与普通仓库布局一致）。
     * 应用自身不打开它。
     *
     * @param dir       独立仓库目标目录
     * @param appConfig 应用级配置（复制为仓库级，不含密钥）
     */
    public static Path createStandalone(Path dir, JsonObject appConfig) throws IOException {
        LOG.info("Creating standalone repository at {}", dir);
        Files.createDirectories(dir);
        copyLib(dir);
        writeScripts(dir);
        VaultDetector.writeRepoConfig(dir, appConfig);
        LOG.info("Standalone repository created at {}", dir);
        return dir;
    }

    /**
     * 把普通仓库原地升级为独立仓库：仓库根新增 {@code config.json}、{@code lib/} 与 edit 脚本，
     * 数据块不动（普通/独立仓库数据布局一致，无 data 层）。返回仓库根。
     */
    public static Path upgradeToStandalone(Path repoRoot, JsonObject appConfig) throws IOException {
        LOG.info("Upgrading repository to standalone: {}", repoRoot);
        if (VaultDetector.isStandaloneRepo(repoRoot)) {
            throw new IOException("已是独立仓库");
        }
        if (Files.exists(repoRoot.resolve("lib"))) {
            throw new IOException("lib 目录已存在");
        }
        copyLib(repoRoot);
        writeScripts(repoRoot);
        VaultDetector.writeRepoConfig(repoRoot, appConfig);
        LOG.info("Repository upgraded to standalone: {}", repoRoot);
        return repoRoot;
    }

    /**
     * 把独立仓库降级为普通仓库：移除 {@code config.json}、{@code lib/} 与 edit 脚本，
     * 数据块不动。返回仓库根。
     */
    public static Path downgradeToNormal(Path repoRoot) throws IOException {
        LOG.info("Downgrading repository to normal: {}", repoRoot);
        deleteIfExists(repoRoot.resolve("lib"));
        deleteIfExists(repoRoot.resolve("edit"));
        deleteIfExists(repoRoot.resolve("edit.bat"));
        deleteIfExists(repoRoot.resolve("config.json"));
        LOG.info("Repository downgraded to normal: {}", repoRoot);
        return repoRoot;
    }

    /**
     * 判断当前进程是否正从该仓库自带的 lib/ 启动（运行时依赖 jar 位于 repoRoot 之下）。
     * 用于降级/刷新独立仓前的保护：若为真，运行时改动 lib/ 会破坏正在运行的应用，必须禁止。
     */
    public static boolean runsFromRepoLib(Path repoRoot) {
        if (repoRoot == null) {
            return false;
        }
        Path root = repoRoot.toAbsolutePath().normalize();
        return collectRuntimeJars().stream().anyMatch(j -> isUnder(j, root));
    }

    /**
     * 刷新独立仓库内的运行时：用当前应用的运行时 jar 重新复制 {@code lib/} 并覆盖启动脚本，
     * 使仓库自带的运行时版本与当前应用保持一致（数据块不动）。要求仓库已是独立仓。
     */
    public static Path refreshStandaloneRuntime(Path repoRoot) throws IOException {
        LOG.info("Refreshing standalone runtime: {}", repoRoot);
        if (!VaultDetector.isStandaloneRepo(repoRoot)) {
            throw new IOException("不是独立仓库");
        }
        copyLib(repoRoot);
        writeScripts(repoRoot);
        LOG.info("Standalone runtime refreshed: {}", repoRoot);
        return repoRoot;
    }

    /**
     * 复制当前运行进程依赖的全部 jar 到目标仓库的 lib/，使仓库可独立启动。
     * 收集策略（并集、按文件名去重）覆盖各种启动形态：
     * 1) 启动模块层 {@link ModuleLayer#boot()} 中每个已解析模块的真实位置（module-path / IDE 模块启动 / maven 包）；
     * 2) {@code java.class.path} 上的 jar（模块层未覆盖的普通 classpath jar）；
     * 3) 应用 jar 同目录的兄弟 jar（分发 lib/ 形态兜底）。
     * 若以上都取不到（如单一 fat jar，依赖内嵌于 BOOT-INF/lib），再直接抽出内嵌 jar。
     * 排除 JDK 模块（jrt:）与运行 JRE 目录下的 jar。
     */
    private static void copyLib(Path dir) throws IOException {
        Path lib = dir.resolve("lib");
        Files.createDirectories(lib);
        Set<String> copied = new java.util.HashSet<>();
        for (Path jar : collectRuntimeJars()) {
            String name = jar.getFileName() == null ? null : jar.getFileName().toString();
            if (name == null || !copied.add(name)) {
                continue;
            }
            Files.copy(jar, lib.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        // 单一 fat jar 形态：依赖内嵌于 BOOT-INF/lib，模块层/CLASSPATH 取不到，直接抽出
        jarOfAppCodeSource().ifPresent(appJar -> extractFatJarLibs(appJar, lib, copied));
    }

    private static void deleteIfExists(Path p) throws IOException {
        if (Files.exists(p)) {
            Files.walk(p).sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /** 收集文件系统上的依赖 jar（应用模块 + 第三方模块 + classpath jar），排除 JDK 与运行 JRE。 */
    private static List<Path> collectRuntimeJars() {
        Set<Path> jars = new LinkedHashSet<>();
        // 1) 启动模块层：每个已解析模块的真实位置（jar:file:.../x.jar!/... 或 file:.../x.jar）
        for (ResolvedModule rm : ModuleLayer.boot().configuration().modules()) {
            rm.reference().location().ifPresent(loc -> jarOf(loc).ifPresent(jars::add));
        }
        // 2) classpath 上的 jar（模块层未覆盖的普通 classpath jar）
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(java.io.File.pathSeparator)) {
            if (!entry.isEmpty()) {
                Path p = Path.of(entry);
                if (Files.isRegularFile(p) && entry.endsWith(".jar")) {
                    jars.add(p);
                }
            }
        }
        // 3) 应用 jar 同目录的兄弟 jar（分发 lib/ 形态兜底）
        jarOfAppCodeSource().ifPresent(appJar -> {
            Path parent = appJar.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var s = Files.list(parent)) {
                    s.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".jar"))
                            .forEach(jars::add);
                } catch (IOException ignore) {
                }
            }
        });
        // 排除运行 JRE 目录下的 jar
        Path javaHome = Path.of(System.getProperty("java.home"));
        return jars.stream()
                .filter(p -> !isUnder(p, javaHome))
                .map(p -> p.toAbsolutePath().normalize())
                .distinct()
                .toList();
    }

    /** 由模块位置 URI 取 jar 文件路径；JDK 模块（jrt:）或非文件位置返回空。 */
    private static Optional<Path> jarOf(URI uri) {
        if (uri == null) {
            return Optional.empty();
        }
        String s = uri.toString();
        if (s.startsWith("jrt:")) {
            return Optional.empty();
        }
        String file = s.startsWith("jar:") ? s.substring(4) : s;
        int sep = file.indexOf("!/");
        if (sep >= 0) {
            file = file.substring(0, sep);
        }
        if (!file.startsWith("file:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(new URI(file)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 应用自身（RepoCreator 所在类）的 jar 文件位置；非 jar 形态（如 IDE 爆炸类）返回空。 */
    private static Optional<Path> jarOfAppCodeSource() {
        try {
            var loc = RepoCreator.class.getProtectionDomain().getCodeSource();
            if (loc == null || loc.getLocation() == null) {
                return Optional.empty();
            }
            Path p = Path.of(loc.getLocation().toURI());
            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) {
                return Optional.of(p.toAbsolutePath().normalize());
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 从单一 fat jar 的 BOOT-INF/lib 抽出依赖 jar 到 lib/（fat jar 形态兜底）。 */
    private static void extractFatJarLibs(Path appJar, Path lib, Set<String> copied) {
        try (JarFile jf = new JarFile(appJar.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry en = entries.nextElement();
                String n = en.getName();
                if (n.startsWith("BOOT-INF/lib/") && n.endsWith(".jar") && !en.isDirectory()) {
                    String fname = n.substring("BOOT-INF/lib/".length());
                    if (fname.indexOf('/') >= 0) {
                        continue;
                    }
                    if (!copied.add(fname) || Files.exists(lib.resolve(fname))) {
                        continue;
                    }
                    Files.copy(jf.getInputStream(en), lib.resolve(fname));
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to extract fat-jar libs into {}: {}", lib, e.getMessage());
        }
    }

    /** p 是否位于 base 之下（含相等）。 */
    private static boolean isUnder(Path p, Path base) {
        try {
            return p.toRealPath().startsWith(base.toRealPath());
        } catch (IOException e) {
            return p.startsWith(base);
        }
    }

    /**
     * 写入独立仓库启动脚本：posix 版 {@code edit} 与 windows 版 {@code edit.bat}（分开两个文件）。
     * 二者均经 {@code --module-path lib} 启动应用。依赖本地 JRE（JAVA_HOME 优先，其次 PATH 的 java）；
     * git 为可选（运行时探测）。jar 启动时自行判定孤立形态（自身位于 lib/ 且仓库根含 edit 脚本）。
     * <p>
     * 必须用 LF 换行（bash 段在 CRLF 下无法解析；cmd 亦兼容 LF 批处理）。
     */
    private static void writeScripts(Path dir) throws IOException {
        String posix = "#!/usr/bin/env bash\n"
                + "# Launcher for standalone repo. Requires local JRE; git is optional.\n"
                + "cd \"$(dirname \"$0\")\" || exit 1\n"
                + "if [ -n \"$JAVA_HOME\" ] && [ -x \"$JAVA_HOME/bin/java\" ]; then\n"
                + "  JAVA=\"$JAVA_HOME/bin/java\"\n"
                + "else\n"
                + "  JAVA=java\n"
                + "fi\n"
                + "exec \"$JAVA\" --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main \"$@\" || exit 1\n";
        Path editFile = dir.resolve("edit");
        Files.writeString(editFile, posix);
        editFile.toFile().setExecutable(true);

        String windows = "@echo off\n"
                + "cd /d \"%~dp0\"\n"
                + "if defined JAVA_HOME (\n"
                + "  set \"JAVA=%JAVA_HOME%\\bin\\java.exe\"\n"
                + "  if not exist \"%JAVA%\" set \"JAVA=java\"\n"
                + ") else (\n"
                + "  set \"JAVA=java\"\n"
                + ")\n"
                + "\"%JAVA%\" --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main %*\n";
        Path batFile = dir.resolve("edit.bat");
        Files.writeString(batFile, windows);
        batFile.toFile().setExecutable(true);
    }
}
