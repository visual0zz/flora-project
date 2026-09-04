package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库配置数据（Sanctum = 元数据 + 配置数据 + List&lt;数据树&gt; 的"配置数据"部分）。
 * <p>
 * 承载仓库级设置（type=config 节点，key/value 加密存储于根对象下）：主题/自动锁定时长/剪贴板清空时长。
 * 配置数据以仓库内加密节点存储（见设计"设置存仓库"）；未设置时返回默认值。远程配置与 SSH 密钥是一等数据节点（type=remote / type=ssh_key），
 * 由 {@code RemoteTree}/{@code SshKeyTree} 负责读写，不在此类中重复表示。
 */
public final class LibraryConfig {

    private final TreeContext ctx;

    public LibraryConfig(TreeContext ctx) {
        this.ctx = ctx;
    }

    // ---- 仓库级设置（type=config 节点，加密存储；见设计"设置存仓库"） ----

    public static final String DEFAULT_THEME = "system";
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30;

    /** 读仓库设置；无该配置节点返回 null。 */
    public String getConfig(String key) {
        Map.Entry<UUID, JsonObject> e = findConfigEntry(key);
        return e == null ? null : e.getValue().getString("value");
    }

    /** 写仓库设置（config 节点，加密存储；新建或更新）。 */
    public void setConfig(String key, String value) {
        byte[] dek = ctx.vault().dataDek();
        Map.Entry<UUID, JsonObject> e = findConfigEntry(key);
        if (e != null) {
            e.getValue().put("value", value);
            ctx.writeWithDek(e.getKey(), e.getValue(), dek);
            return;
        }
        JsonObject c = new JsonObject();
        c.put("type", StoredNodeType.CONFIG.tag());
        c.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(ctx.vault().rootObjectUuid()));
        c.put("key", key);
        c.put("value", value);
        ctx.writeWithDek(UUID.randomUUID(), c, dek);
    }

    public String theme() {
        String v = getConfig("theme");
        return v == null ? DEFAULT_THEME : v;
    }

    public void setTheme(String theme) {
        setConfig("theme", theme);
    }

    public int lockTimeoutSeconds() {
        return intConfig("lockTimeoutSeconds", DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    public void setLockTimeoutSeconds(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("lockTimeoutSeconds must be non-negative");
        }
        setConfig("lockTimeoutSeconds", String.valueOf(seconds));
    }

    public int clipboardClearSeconds() {
        return intConfig("clipboardClearSeconds", DEFAULT_CLIPBOARD_CLEAR_SECONDS);
    }

    public void setClipboardClearSeconds(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("clipboardClearSeconds must be non-negative");
        }
        setConfig("clipboardClearSeconds", String.valueOf(seconds));
    }

    /** 导出当前配置为 JSON（theme/lockTimeoutSeconds/clipboardClearSeconds）。 */
    public JsonObject toJson() {
        JsonObject cfg = new JsonObject();
        cfg.put("theme", theme());
        cfg.put("lockTimeoutSeconds", lockTimeoutSeconds());
        cfg.put("clipboardClearSeconds", clipboardClearSeconds());
        return cfg;
    }

    /** 从 JSON 应用配置（仅识别已知键，未知键忽略；缺失键保持默认值/现状）。 */
    public void fromJson(JsonObject src) {
        if (src == null) {
            return;
        }
        String theme = src.getString("theme");
        if (theme != null) {
            setTheme(theme);
        }
        Integer lock = src.getInt("lockTimeoutSeconds");
        if (lock != null) {
            setLockTimeoutSeconds(lock);
        }
        Integer clip = src.getInt("clipboardClearSeconds");
        if (clip != null) {
            setClipboardClearSeconds(clip);
        }
    }

    private int intConfig(String key, int def) {
        String v = getConfig(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ignore) {
            return def;
        }
    }

    private Map.Entry<UUID, JsonObject> findConfigEntry(String key) {
        for (Map.Entry<UUID, JsonObject> e : ctx.objects().entrySet()) {
            JsonObject n = e.getValue();
            if (StoredNodeType.fromTag(n.getString("type")) == StoredNodeType.CONFIG
                    && key.equals(n.getString("key"))) {
                return e;
            }
        }
        return null;
    }
}
