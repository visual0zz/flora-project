package com.flora.hanako.server;

import com.flora.root.codec.json.JsonBuilder;
import com.flora.hanako.core.HanakoEngine;
import com.flora.hanako.core.HanakoEngine.EventSink;
import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonObject;
import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天 WebSocket：桥接前端 prompt → 引擎流式推理 → 增量事件广播。
 * <p>协议对齐 openhanako ws-protocol：Client→{type:"prompt"|"abort"}，
 * Server→{type:"text_delta"|"thinking_*"|"tool_start"|"tool_end"|"turn_end"|"error"|"status"}。</p>
 */
public final class ChatWebSocket {

    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

    private ChatWebSocket() {
    }

    public static void register(Javalin app, HanakoEngine engine) {
        app.ws("/ws/chat", ws -> {
            ws.onMessage(ctx -> {
                Map<String, Object> msg =
                        ctx.message() == null ? Map.of() : asMap(ctx.message());
                String type = String.valueOf(msg.getOrDefault("type", ""));
                switch (type) {
                    case "prompt" -> {
                        String sessionId = String.valueOf(msg.get("sessionId"));
                        String text = String.valueOf(msg.get("text"));
                        // 在独立线程跑推理，避免阻塞 WS 读写
                        POOL.submit(() -> {
                            EventSink sink = event -> safeSend(ctx, event);
                            engine.runTurn(sessionId, text, sink);
                        });
                    }
                    case "abort" -> {
                        // 当前流式推理在引擎内同步执行，abort 标记留作扩展
                        safeSend(ctx, Map.of("type", "status", "isStreaming", engine.isStreaming()));
                    }
                    default -> safeSend(ctx, Map.of("type", "error", "message", "未知消息类型: " + type));
                }
            });
            ws.onClose(ctx -> { /* 连接关闭，若正在推理可在此 abort */ });
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(String raw) {
        try {
            Object v = JsonParser.parse(raw);
            if (v instanceof JsonObject) {
                return ((JsonObject) v).toMap();
            }
        } catch (RuntimeException ignored) {
            // 非法 JSON
        }
        return Map.of();
    }

    private static void safeSend(io.javalin.websocket.WsContext ctx, Map<String, Object> event) {
        try {
            ctx.send(JsonBuilder.toJsonString(event));
        } catch (RuntimeException ignored) {
            // 连接已断开
        }
    }
}
