package com.flora.sanctum.config;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用户配置目录（见设计 07"用户配置目录"）。
 * <p>
 * 全局配置存于 {@code ~/.flora-sanctum/config.json}（用户级，跨所有库一份）：
 * 外观偏好（主题/强调色）、自动锁定时长、剪贴板清空时长、同步开关等。
 * 不存放任何密码学材料/密钥/密文块。
 */
public final class UserConfig {

    private final Path dir;
    private final Path file;
    private JsonObject data;

    public UserConfig() {
        this(Path.of(System.getProperty("user.home"), ".flora-sanctum"));
    }

    public UserConfig(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("config.json");
        this.data = load();
    }

    public Path dir() {
        return dir;
    }

    public Path file() {
        return file;
    }

    /** 主题模式：light / dark / system。 */
    public String theme() {
        String v = data.getString("theme");
        return v == null ? "system" : v;
    }

    public void setTheme(String theme) {
        data.put("theme", theme);
        save();
    }

    /** 自动锁定时长（秒，默认 300）。 */
    public int lockTimeoutSeconds() {
        Integer v = data.getInt("lockTimeoutSeconds");
        return v == null ? 300 : v;
    }

    public void setLockTimeoutSeconds(int seconds) {
        data.put("lockTimeoutSeconds", seconds);
        save();
    }

    /** 剪贴板清空时长（秒，默认 30）。 */
    public int clipboardClearSeconds() {
        Integer v = data.getInt("clipboardClearSeconds");
        return v == null ? 30 : v;
    }

    public void setClipboardClearSeconds(int seconds) {
        data.put("clipboardClearSeconds", seconds);
        save();
    }

    /** 同步开关（默认 true）。 */
    public boolean syncEnabled() {
        Boolean v = data.getBool("syncEnabled");
        return v == null || v;
    }

    public void setSyncEnabled(boolean enabled) {
        data.put("syncEnabled", enabled);
        save();
    }

    private JsonObject load() {
        try {
            if (Files.isRegularFile(file)) {
                String content = Files.readString(file);
                return JsonUtil.parseObject(content);
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
