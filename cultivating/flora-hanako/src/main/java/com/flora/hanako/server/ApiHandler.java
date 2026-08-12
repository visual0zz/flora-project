package com.flora.hanako.server;

import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;
import com.flora.hanako.core.HanakoEngine;
import com.flora.hanako.core.model.Agent;
import com.flora.hanako.core.model.Jian;
import com.flora.hanako.core.model.ModelConfig;
import com.flora.hanako.core.model.ProviderConfig;
import com.flora.hanako.core.model.Session;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API 路由：Agent / 会话 / 模型 / 提供商 / 记忆 / 书桌。
 * <p>对齐 openhanako 的 server/routes/*（agents / sessions / models / providers / skills / desk）。</p>
 */
public final class ApiHandler {

    private final HanakoEngine engine;

    public ApiHandler(HanakoEngine engine) {
        this.engine = engine;
    }

    /** 以 application/json 回写对象（不使用第三方 JSON 库，复用 flora-root codec）。 */
    private static void json(io.javalin.http.Context ctx, Object obj) {
        ctx.contentType("application/json").result(JsonBuilder.toJsonString(obj));
    }

    @SuppressWarnings("unchecked")
    public void register(Javalin app) {

        // ── Agents ──
        app.get("/api/agents", ctx -> json(ctx, engine.listAgents()));
        app.get("/api/agents/{id}", ctx -> {
            Agent a = engine.getAgent(ctx.pathParam("id"));
            if (a == null) {
                ctx.status(404); json(ctx, Map.of("error", "agent 不存在"));
                return;
            }
            json(ctx, a);
        });
        app.post("/api/agents", ctx -> {
            Map<String, Object> body = JsonParser.parse(ctx.body()).toMap();
            Agent a = mapToAgent(body);
            engine.saveAgent(a);
            json(ctx, a);
        });
        app.delete("/api/agents/{id}", ctx -> {
            engine.deleteAgent(ctx.pathParam("id"));
            json(ctx, Map.of("ok", true));
        });

        // ── Sessions ──
        app.get("/api/sessions", ctx -> {
            String agentId = ctx.queryParam("agentId");
            json(ctx, engine.listSessions(agentId));
        });
        app.post("/api/sessions", ctx -> {
            Map<String, Object> body = ctx.body().isBlank() ? Map.of()
                    : JsonParser.parse(ctx.body()).toMap();
            String agentId = body.get("agentId") == null ? null : String.valueOf(body.get("agentId"));
            json(ctx, engine.newSession(agentId));
        });
        app.get("/api/sessions/{id}", ctx -> {
            Session s = engine.getSession(ctx.pathParam("id"));
            if (s == null) {
                ctx.status(404); json(ctx, Map.of("error", "session 不存在"));
                return;
            }
            json(ctx, s);
        });
        app.delete("/api/sessions/{id}", ctx -> {
            engine.deleteSession(ctx.pathParam("id"));
            json(ctx, Map.of("ok", true));
        });

        // ── Models ──
        app.get("/api/models", ctx -> json(ctx, engine.listModels()));
        app.post("/api/models", ctx -> {
            Map<String, Object> body = JsonParser.parse(ctx.body()).toMap();
            ModelConfig m = mapToModel(body);
            engine.saveModel(m);
            engine.applyProviders();
            json(ctx, m);
        });

        // ── Providers ──
        app.get("/api/providers", ctx -> json(ctx, engine.listProviders()));
        app.post("/api/providers", ctx -> {
            Map<String, Object> body = JsonParser.parse(ctx.body()).toMap();
            ProviderConfig p = mapToProvider(body);
            engine.saveProvider(p);
            engine.applyProviders();
            json(ctx, p);
        });

        // ── Memory ──
        app.get("/api/memory", ctx -> {
            String tagParam = ctx.queryParam("tags");
            List<String> tags = tagParam == null ? List.of() : List.of(tagParam.split(","));
            json(ctx, engine.memory().query(tags));
        });
        app.post("/api/memory", ctx -> {
            Map<String, Object> body = JsonParser.parse(ctx.body()).toMap();
            List<String> tags = new java.util.ArrayList<>();
            if (body.get("tags") instanceof List<?> l) {
                for (Object t : l) {
                    tags.add(String.valueOf(t));
                }
            }
            engine.remember(String.valueOf(body.get("text")), tags);
            json(ctx, Map.of("ok", true));
        });
        app.delete("/api/memory/{id}", ctx -> {
            engine.memory().remove(ctx.pathParam("id"));
            json(ctx, Map.of("ok", true));
        });

        // ── Desk / Jian ──
        app.get("/api/jians", ctx -> {
            String agentId = ctx.queryParam("agentId");
            json(ctx, engine.listJians(agentId));
        });
        app.post("/api/jians", ctx -> {
            Map<String, Object> body = JsonParser.parse(ctx.body()).toMap();
            Jian j = new Jian();
            if (body.get("id") != null) {
                j.setId(String.valueOf(body.get("id")));
            }
            j.setAgentId(body.get("agentId") == null ? "hanako" : String.valueOf(body.get("agentId")));
            j.setContent(String.valueOf(body.get("content")));
            j.setDone(Boolean.TRUE.equals(body.get("done")));
            engine.saveJian(j);
            json(ctx, j);
        });
        app.delete("/api/jians/{id}", ctx -> {
            engine.deleteJian(ctx.pathParam("id"));
            json(ctx, Map.of("ok", true));
        });
    }

    // ===================== 映射辅助 =====================

    @SuppressWarnings("unchecked")
    private Agent mapToAgent(Map<String, Object> body) {
        Agent a = new Agent();
        if (body.get("id") != null) {
            a.setId(String.valueOf(body.get("id")));
        }
        a.setName(body.get("name") == null ? "未命名" : String.valueOf(body.get("name")));
        a.setIdentity((String) body.get("identity"));
        a.setIshiki((String) body.get("ishiki"));
        a.setModelId((String) body.get("modelId"));
        a.setDefault(Boolean.TRUE.equals(body.get("default")));
        if (body.get("tags") instanceof List<?> l) {
            for (Object t : l) {
                a.getTags().add(String.valueOf(t));
            }
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    private ModelConfig mapToModel(Map<String, Object> body) {
        ModelConfig.Role role = ModelConfig.Role.valueOf(String.valueOf(body.get("role")));
        ModelConfig m = new ModelConfig(role,
                (String) body.get("endpointId"),
                (String) body.get("displayName"));
        return m;
    }

    @SuppressWarnings("unchecked")
    private ProviderConfig mapToProvider(Map<String, Object> body) {
        ProviderConfig p = new ProviderConfig();
        if (body.get("id") != null) {
            p.setId(String.valueOf(body.get("id")));
        } else {
            p.setId(UUID.randomUUID().toString());
        }
        p.setName((String) body.get("name"));
        p.setApiKind((String) body.get("apiKind"));
        p.setBaseUrl((String) body.get("baseUrl"));
        p.setApiKey((String) body.get("apiKey"));
        p.setEnabled(!Boolean.FALSE.equals(body.get("enabled")));
        return p;
    }
}
