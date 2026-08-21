package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 库配置数据（Sanctum = 元数据 + 配置数据 + List&lt;数据树&gt; 的"配置数据"部分）。
 * <p>
 * 目前承载远端配置（kind=remote 字段）的只读视图；写操作经 {@code RemoteTree}（根概念 REMOTE）承担。
 * 预留后续库级非密码配置扩展。
 */
public final class LibraryConfig {

    private final TreeContext ctx;

    public LibraryConfig(TreeContext ctx) {
        this.ctx = ctx;
    }

    /** 远端配置列表（只读视图，来自 REMOTE 树的 kind=remote 字段）。 */
    public List<RemoteConfig> remotes() {
        List<RemoteConfig> out = new ArrayList<>();
        for (JsonObject n : ctx.objects().values()) {
            if (NodeType.fromTag(n.getString("type")) == NodeType.FIELD
                    && "remote".equals(n.getString("kind"))) {
                JsonObject value = n.getObject("value");
                out.add(new RemoteConfig(n.getString("fieldName"),
                        value == null ? null : value.getString("url"),
                        value == null ? null : value.getString("keyRef")));
            }
        }
        return out;
    }

    /** 按名称查找远端配置；未找到返回 null。 */
    public RemoteConfig remote(String name) {
        for (RemoteConfig r : remotes()) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return null;
    }

    /** 远端配置（value 对象解构）。 */
    public record RemoteConfig(String name, String url, String keyRef) {
    }
}
