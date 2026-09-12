package com.flora.sanctum.app.config;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonNull;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 用户配置（见设计 07"用户配置目录"）。
 * <p>
 * 统一存于 {@code $XDG_CONFIG_HOME/flora-sanctum/config.json}（默认 {@code ~/.config/flora-sanctum/config.json}），
 * 应用形态与独立仓库形态共用同一份：独立仓库根不再持有自己的 config.json。
 * 仅承载明文偏好（主题/强调色、最近库、上次库、窗口尺寸、分隔线比例等），不含任何机密。
 * 自动锁定/剪贴板清空等仓库策略存于仓库内加密配置（见 LibraryConfig），不在此处重复存储。
 * 不存放任何密码学材料/密钥/密文块。
 */
public final class UserConfig {

    /** 应用名（同时作为 XDG 子目录名与配置目录名）。 */
    private static final String APP_NAME = "flora-sanctum";

    private final Path dir;
    private final Path file;
    private JsonObject data;

    public UserConfig() {
        this.dir = defaultConfigDir();
        this.file = dir.resolve("config.json");
        this.data = load();
    }

    /** 解析用户级配置目录：优先 {@code $XDG_CONFIG_HOME}，未设置回退 {@code ~/.config}。 */
    private static Path defaultConfigDir() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg).resolve(APP_NAME);
        }
        String home = System.getProperty("user.home");
        return Path.of(Objects.requireNonNullElse(home, "."), ".config", APP_NAME);
    }

    /**
     * 界面主题（light / dark / stupid；默认 light）。
     * <p>stupid 主题所有位置颜色在应用主题时随机生成（每次应用不同，仅供娱乐）。</p>
     */
    public String theme() {
        String v = data.getString("theme");
        // "system" 非合法 scheme（UiTheme 无对应分支），视为默认 light，避免悬空值悄悄按 light 渲染造成语义不一致
        if (v == null || "system".equals(v)) {
            return "light";
        }
        return v;
    }

    public void setTheme(String theme) {
        data.put("theme", theme);
        save();
    }

    /** 最近打开的库路径列表（按最近使用在前，最多保留 {@link #recentLimit} 条）。 */
    public java.util.List<String> recentVaults() {
        JsonArray arr = data.getArray("recentVaults");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (arr != null) {
            for (JsonValue v : arr.elements()) {
                out.add(v.asString());
            }
        }
        return out;
    }

    /** 记录一次打开/新建的库路径，去重置顶并保留上限。 */
    public void addRecentVault(String path) {
        java.util.List<String> list = recentVaults();
        list.remove(path);
        list.add(0, path);
        while (list.size() > recentLimit) {
            list.remove(list.size() - 1);
        }
        data.put("recentVaults", JsonArray.fromList(list));
        save();
    }

    /** 从最近库列表移除一条记录。 */
    public void removeRecentVault(String path) {
        java.util.List<String> list = recentVaults();
        if (list.remove(path)) {
            data.put("recentVaults", JsonArray.fromList(list));
            save();
        }
    }

    /** 上次打开的库路径（用于锁定后预选，null 表示无）。 */
    public String lastVault() {
        String v = data.getString("lastVault");
        return v == null || v.isEmpty() ? null : v;
    }

    public void setLastVault(String path) {
        data.put("lastVault", path == null ? JsonNull.INSTANCE : path);
        save();
    }

    /** 最近库列表上限。 */
    public static final int recentLimit = 50;

    /** 主界面分隔线比例（key → 0..1，非机密信息存全局配置）。 */
    public Double dividerRatio(String key) {
        return data.getDouble(key);
    }

    public void setDividerRatio(String key, double ratio) {
        data.put(key, ratio);
        save();
    }

    /** 窗口尺寸（宽高像素；key 如 "ui.window.guide"）。无存储返回 null。 */
    public int[] windowSize(String key) {
        Integer w = data.getInt(key + ".w");
        Integer h = data.getInt(key + ".h");
        return (w == null || h == null) ? null : new int[]{w, h};
    }

    public void setWindowSize(String key, int w, int h) {
        data.put(key + ".w", w);
        data.put(key + ".h", h);
        save();
    }

    private JsonObject load() {
        try {
            if (Files.isRegularFile(file)) {
                return JsonUtil.parseObject(Files.readString(file));
            }
        } catch (Exception ignore) {
            // 配置损坏则回退默认
        }
        return new JsonObject();
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            Files.writeString(file, JsonUtil.toJsonString(data));
        } catch (Exception e) {
            throw new IllegalStateException("cannot save user config", e);
        }
    }
}
