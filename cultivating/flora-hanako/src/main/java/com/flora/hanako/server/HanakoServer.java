package com.flora.hanako.server;

import com.flora.hanako.core.HanakoEngine;
import io.javalin.Javalin;

import java.nio.file.Path;

/**
 * Hanako 服务端引导：启动 Javalin（HTTP + WebSocket），装配 REST 路由与聊天 WebSocket。
 * <p>方案 B（轻量嵌入式 Web）：浏览器即 UI，静态前端资源打包进 jar 由 Javalin 直接托管，
 * 后端仅此一个主第三方依赖（Javalin / Jetty）。</p>
 */
public final class HanakoServer {

    private final HanakoEngine engine;
    private Javalin app;

    public HanakoServer(HanakoEngine engine) {
        this.engine = engine;
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/web";
                staticFiles.hostedPath = "/";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
        });

        ApiHandler api = new ApiHandler(engine);
        api.register(app);
        ChatWebSocket.register(app, engine);

        app.get("/api/health", ctx -> {
            java.util.Map<String, Object> body = java.util.Map.of(
                    "status", "ok",
                    "agent", engine.listAgents().stream().filter(a -> a.isDefault()).findFirst()
                            .map(a -> a.getName()).orElse("Hanako"),
                    "model", "未配置"
            );
            ctx.contentType("application/json").result(com.flora.codec.json.JsonBuilder.toJsonString(body));
        });

        app.start(port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    public static void main(String[] args) {
        Path home = Path.of(System.getProperty("user.home"), ".flora-hanako");
        HanakoEngine engine = new HanakoEngine(home);
        engine.applyProviders();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 4567;
        HanakoServer server = new HanakoServer(engine);
        server.start(port);
        System.out.println("[Hanako] 已启动，访问 http://localhost:" + port);
    }
}
