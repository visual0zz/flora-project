package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 仓库形态识别与仓库级配置（见设计"形态与启动"）。
 * <p>
 * 形态由「lib/ 目录 + edit 脚本」判定（判断逻辑落在 jar 内，不依赖脚本传参）：
 * <ul>
 *   <li><b>独立仓库</b>：仓库根含 {@code lib/}（全量 jar）与 {@code edit}/{@code edit.bat} 脚本；
 *       jar 启动时若自身位于某 {@code lib/} 目录且其父目录（仓库根）存在 edit 脚本，即判定为孤立形态，
 *       直接进入该仓库解锁页。数据块（两层目录 + *.md）直接在仓库根。</li>
 *   <li><b>普通仓库</b>：目录本身就是数据根（两层目录 + {@code *.md} 块文件）。</li>
 *   <li><b>非仓库</b>：无上述结构。</li>
 * </ul>
 * 独立仓库形态只读自身 {@code config.json}（仓库根），不读系统配置；其结构与应用级配置相同
 * （存储"使用习惯"内容，非机密）。
 */
public final class VaultDetector {

    public enum Type {
        /** 普通仓库：目录即数据根（两层目录 + md 块）。 */
        NORMAL,
        /** 独立仓库：目录含 lib/ 与 edit 脚本（数据块直接在仓库根）。 */
        STANDALONE,
        /** 非仓库：无结构。 */
        NOT_A_VAULT
    }

    /** 独立仓库判定脚本（posix / windows；与 lib/ 同目录，即仓库根）。 */
    private static final String EDIT_SCRIPT = "edit";
    private static final String EDIT_BAT = "edit.bat";

    /** 独立仓库的仓库级配置（位于仓库根，与应用级 config.json 同结构）。 */
    private static final String REPO_CONFIG = "config.json";

    private VaultDetector() {
    }

    /** 目录是否为独立仓库布局（lib/ 与 edit 脚本齐全）。 */
    public static boolean isStandaloneRepo(Path dir) {
        return dir != null
                && Files.isDirectory(dir.resolve("lib"))
                && hasEditScript(dir);
    }

    /** 目录是否含有 edit 启动脚本（posix edit 或 windows edit.bat）。 */
    public static boolean hasEditScript(Path dir) {
        return Files.isRegularFile(dir.resolve(EDIT_SCRIPT))
                || Files.isRegularFile(dir.resolve(EDIT_BAT));
    }

    /**
     * 判定目录的仓库形态。
     *
     * @param dir 待判定目录
     */
    public static Type detect(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return Type.NOT_A_VAULT;
        }
        if (isStandaloneRepo(dir)) {
            return Type.STANDALONE;
        }
        if (hasBlockFiles(dir)) {
            return Type.NORMAL;
        }
        return Type.NOT_A_VAULT;
    }

    /** 仓库的数据根：独立仓库与普通仓库均为 {@code dir} 本身（无 data 层）；非仓库返回 null。 */
    public static Path dataDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        if (isStandaloneRepo(dir) || hasBlockFiles(dir)) {
            return dir;
        }
        return null;
    }

    /** 目录下是否存在数据块文件（两层目录内的任意 *.md）。 */
    public static boolean hasBlockFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(p -> p.getFileName().toString().endsWith(".md"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判定 jar 是否以孤立仓库形态启动：jar 自身位于某 {@code lib/} 目录且其父目录（仓库根）
     * 存在 edit 脚本。
     */
    public static Path detectStandaloneRoot() {
        Path jarDir = jarDirectory();
        if (jarDir == null) {
            return null;
        }
        // jar 必须直接位于名为 lib 的目录内（独立仓库的 lib/）
        if (!"lib".equals(jarDir.getFileName().toString())) {
            return null;
        }
        Path repoRoot = jarDir.getParent();
        if (repoRoot == null) {
            return null;
        }
        return hasEditScript(repoRoot) ? repoRoot : null;
    }

    /** 尝试定位主类所在 jar 的父目录（不可行时返回 null）。 */
    private static Path jarDirectory() {
        try {
            var loc = VaultDetector.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            Path p = Path.of(loc.toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    /** 独立仓库的仓库级配置路径（config.json）；非独立返回 null。 */
    public static Path configFile(Path dir) {
        if (dir != null && isStandaloneRepo(dir)) {
            return dir.resolve(REPO_CONFIG);
        }
        return null;
    }

    /**
     * 读取仓库级配置（独立仓库 config.json）。与应用级配置同结构。
     * 返回 JSON 对象；文件不存在/损坏返回空对象（调用方按默认处理）。
     */
    public static JsonObject loadRepoConfig(Path dir) {
        Path cfg = dir.resolve(REPO_CONFIG);
        if (!Files.isRegularFile(cfg)) {
            return new JsonObject();
        }
        try {
            return JsonUtil.parseObject(Files.readString(cfg));
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /** 写入独立仓库 config.json（不含密钥等加密信息）。 */
    public static void writeRepoConfig(Path dir, JsonObject appConfig) {
        Path cfg = dir.resolve(REPO_CONFIG);
        try {
            Files.createDirectories(dir);
            Files.writeString(cfg, JsonUtil.toJsonString(appConfig));
        } catch (Exception e) {
            throw new IllegalStateException("cannot write repo config: " + cfg, e);
        }
    }

    /** 找出独立仓库（lib/ + edit 脚本）的数据根。 */
    public static Path vaultRoot(Path repoRoot) {
        return dataDir(repoRoot);
    }
}
