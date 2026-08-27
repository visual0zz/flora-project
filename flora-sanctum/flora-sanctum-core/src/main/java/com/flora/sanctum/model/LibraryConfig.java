package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库配置数据（Sanctum = 元数据 + 配置数据 + List&lt;数据树&gt; 的"配置数据"部分）。
 * <p>
 * 承载两类：
 * <ul>
 *   <li><b>远程配置</b>（type=remote 节点）只读视图，写操作经 {@code RemoteTree} 承担。</li>
 *   <li><b>仓库级设置</b>（type=config 节点，key/value 加密存储于根对象下）：主题/自动锁定时长/剪贴板清空时长。
 *       不再落全局配置文件；未设置时返回默认值。</li>
 * </ul>
 */
public final class LibraryConfig {

    private final TreeContext ctx;

    public LibraryConfig(TreeContext ctx) {
        this.ctx = ctx;
    }

    /** 远程配置列表（只读视图，来自 REMOTE 树节点）。 */
    public List<RemoteConfig> remotes() {
        List<RemoteConfig> out = new ArrayList<>();
        for (JsonObject n : ctx.objects().values()) {
            if (StoredNodeType.fromTag(n.getString("type")) == StoredNodeType.REMOTE) {
                out.add(new RemoteConfig(n.getString("name"), n.getString("url"),
                        Ref.parse(n.get("keyRef"), "key")));
            }
        }
        return out;
    }

    /** 按名称查找远程配置；未找到返回 null。 */
    public RemoteConfig remote(String name) {
        for (RemoteConfig r : remotes()) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return null;
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
        c.put("parent", ctx.vault().rootObjectUuid().toString());
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
        setConfig("lockTimeoutSeconds", String.valueOf(seconds));
    }

    public int clipboardClearSeconds() {
        return intConfig("clipboardClearSeconds", DEFAULT_CLIPBOARD_CLEAR_SECONDS);
    }

    public void setClipboardClearSeconds(int seconds) {
        setConfig("clipboardClearSeconds", String.valueOf(seconds));
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

    /** 远程配置。 */
    public record RemoteConfig(String name, String url, Ref keyRef) {
    }
}
