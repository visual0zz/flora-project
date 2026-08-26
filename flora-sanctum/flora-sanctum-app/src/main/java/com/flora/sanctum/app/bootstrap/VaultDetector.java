package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 仓库形态识别与仓库级配置（见设计"形态与启动"）。
 * <p>
 * 形态由 {@code standalone.json} 判定（判断逻辑落在 jar 内，不依赖脚本传参）：
 * <ul>
 *   <li><b>独立仓库</b>：目录含 {@code standalone.json}（仓库根），结构为
 *       {@code { standalone.json, lib/, 启动脚本 }}，数据块（两层目录 + *.md）直接在仓库根。</li>
 *   <li><b>普通仓库</b>：目录本身就是数据根（两层目录 + {@code *.md} 块文件）。</li>
 *   <li><b>非仓库</b>：无上述结构。</li>
 * </ul>
 * 独立仓库形态只读自身 {@code standalone.json}，不读系统配置；其结构与应用级配置相同
 * （存储"使用习惯"内容，非机密）。
 */
public final class VaultDetector {

    public enum Type {
        /** 普通仓库：目录即数据根（两层目录 + md 块）。 */
        NORMAL,
        /** 独立仓库：目录含 standalone.json（data/ + lib/ + 脚本）。 */
        STANDALONE,
        /** 非仓库：无结构。 */
        NOT_A_VAULT
    }

    /** 独立仓库判定文件（与脚本同目录，jar 启动时检测自身同目录/工作目录是否存在）。 */
    private static final String STANDALONE_JSON = "standalone.json";

    private VaultDetector() {
    }

    /** standalone.json 文件名（供升级/降级独立形态使用）。 */
    public static String standaloneFileName() {
        return STANDALONE_JSON;
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
        if (Files.isRegularFile(dir.resolve(STANDALONE_JSON))) {
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
        if (Files.isRegularFile(dir.resolve(STANDALONE_JSON)) || hasBlockFiles(dir)) {
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

    /** 判定 jar 是否以孤立仓库形态启动：jar 同目录或当前工作目录存在 standalone.json。 */
    public static Path detectStandaloneRoot() {
        // 1) 当前工作目录（启动脚本 cd 到仓库根）
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve(STANDALONE_JSON))) {
            return cwd;
        }
        // 2) jar 自身所在目录（module-path / classpath 定位失败时返回 null）
        Path jarDir = jarDirectory();
        if (jarDir != null && Files.isRegularFile(jarDir.resolve(STANDALONE_JSON))) {
            return jarDir;
        }
        return null;
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

    /** 独立仓库的 standalone.json 路径；非独立返回 null。 */
    public static Path configFile(Path dir) {
        if (dir != null && Files.isRegularFile(dir.resolve(STANDALONE_JSON))) {
            return dir.resolve(STANDALONE_JSON);
        }
        return null;
    }

    /**
     * 读取仓库级配置（独立仓库 standalone.json）。与应用级配置同结构。
     * 返回 JSON 对象；文件不存在/损坏返回空对象（调用方按默认处理）。
     */
    public static JsonObject loadRepoConfig(Path dir) {
        Path cfg = configFile(dir);
        if (cfg == null || !Files.isRegularFile(cfg)) {
            return new JsonObject();
        }
        try {
            return JsonUtil.parseObject(Files.readString(cfg));
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /** 写入独立仓库 standalone.json（不含密钥等加密信息）。 */
    public static void writeRepoConfig(Path dir, JsonObject appConfig) {
        Path cfg = dir.resolve(STANDALONE_JSON);
        try {
            Files.createDirectories(dir);
            Files.writeString(cfg, JsonUtil.toJsonString(appConfig));
        } catch (Exception e) {
            throw new IllegalStateException("cannot write repo config: " + cfg, e);
        }
    }

    /** 找出独立仓库（含 standalone.json）的数据根。 */
    public static Path vaultRoot(Path repoRoot) {
        return dataDir(repoRoot);
    }
}
