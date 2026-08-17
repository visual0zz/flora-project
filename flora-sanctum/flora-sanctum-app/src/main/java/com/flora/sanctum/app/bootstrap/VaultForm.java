package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 仓库形态识别与仓库级配置（见设计"形态与启动"）。
 * <p>
 * 仓库形态由目录结构判定：
 * <ul>
 *   <li><b>普通仓库</b>：仓库根直接存放 {@code *.md} 数据块（无 lib/ 无仓库配置）。</li>
 *   <li><b>独立仓库</b>：仓库根含 {@code data/}（数据块所在）+ {@code lib/}（全量 jar）+ 启动脚本 + 仓库级 {@code config.json}。</li>
 *   <li><b>非仓库</b>：无上述结构（可能是别的代码仓库 / 空目录）。</li>
 * </ul>
 * 独立仓库形态只读自身仓库级配置，不读系统配置；仓库级配置与应用级配置同结构（见 {@code UserConfig}）。
 */
public final class VaultForm {

    public enum Type {
        /** 普通仓库：data 即仓库根（一堆 md）。 */
        NORMAL,
        /** 独立仓库：仓库根含 data/ + lib/ + 脚本 + 配置。 */
        STANDALONE,
        /** 非仓库：无结构（可能是别的代码仓库 / 空目录）。 */
        NOT_A_VAULT
    }

    private static final String CONFIG = "config.json";
    private static final String DATA_DIR = "data";
    private static final String LIB_DIR = "lib";

    private VaultForm() {
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
        if (Files.isDirectory(dir.resolve(DATA_DIR))) {
            return Type.STANDALONE;
        }
        if (hasMarkdown(dir) || Files.isRegularFile(dir.resolve(CONFIG))) {
            return Type.NORMAL;
        }
        return Type.NOT_A_VAULT;
    }

    /** 仓库根的 data 目录（独立仓库）；普通仓库 data 即仓库根本身；非仓库返回 null。 */
    public static Path dataDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        if (Files.isDirectory(dir.resolve(DATA_DIR))) {
            return dir.resolve(DATA_DIR);
        }
        if (hasMarkdown(dir)) {
            return dir;
        }
        return null;
    }

    /** 目录下是否存在 markdown 数据文件。 */
    public static boolean hasMarkdown(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith(".md"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 独立仓库的仓库级配置文件路径；非独立返回 null。 */
    public static Path configFile(Path dir) {
        if (dir != null && Files.isDirectory(dir.resolve(DATA_DIR))) {
            return dir.resolve(CONFIG);
        }
        return null;
    }

    /**
     * 读取仓库级配置（独立仓库）。与应用级配置同结构。
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

    /** 把应用级配置复制为独立仓库的仓库级配置（不含密钥等加密信息）。 */
    public static void writeRepoConfig(Path dir, JsonObject appConfig) {
        Path cfg = dir.resolve(CONFIG);
        try {
            Files.createDirectories(dir);
            Files.writeString(cfg, JsonUtil.toJsonString(appConfig));
        } catch (Exception e) {
            throw new IllegalStateException("cannot write repo config: " + cfg, e);
        }
    }

    /**
     * 找出某个独立仓库（含 data/ 目录）下的数据根。当前 jar 直接加载它即可。
     * 与 {@link #dataDir} 一致，但明确返回"可被 Sanctum.open 使用的根"。
     */
    public static Path vaultRoot(Path repoRoot) {
        return dataDir(repoRoot);
    }
}
