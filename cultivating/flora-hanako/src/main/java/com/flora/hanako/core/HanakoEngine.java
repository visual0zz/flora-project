package com.flora.hanako.core;

import com.flora.ai.AiApi;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolSpec;
import com.flora.ai.api.spi.TaskContext;
import com.flora.codec.json.JsonBuilder;
import com.flora.hanako.core.model.Agent;
import com.flora.hanako.core.model.ChatMessage;
import com.flora.hanako.core.model.Jian;
import com.flora.hanako.core.model.MemoryFact;
import com.flora.hanako.core.model.ModelConfig;
import com.flora.hanako.core.model.ProviderConfig;
import com.flora.hanako.core.model.Session;
import com.flora.hanako.storage.TaggedFactStore;
import com.flora.hanako.tools.ReadFileTool;
import com.flora.hanako.tools.TerminalTool;
import com.flora.hanako.tools.TodoTool;
import com.flora.hanako.tools.Tool;
import com.flora.hanako.tools.ToolRegistry;
import com.flora.hanako.tools.WebFetchTool;
import com.flora.hanako.tools.WriteFileTool;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hanako 引擎：编排 Agent / 会话 / 记忆 / 工具，驱动一次对话轮（含工具调用循环）。
 * <p>复刻 openhanako 的引擎层（core/engine.js + session-coordinator.js）：
 * 把用户消息、记忆检索结果、系统人格拼成对话，调用 {@code com.flora.ai.api} 流式推理，
 * 遇到工具调用则分发执行并回填，直到模型不再发起工具调用。</p>
 */
public final class HanakoEngine {

    /** 流式事件回调：服务端把事件广播到 WebSocket。 */
    public interface EventSink {
        void emit(Map<String, Object> event);
    }

    private final Path home;
    private final Path workDir;
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, ModelConfig> models = new ConcurrentHashMap<>();
    private final List<ProviderConfig> providers = new ArrayList<>();
    private final TaggedFactStore memory = new TaggedFactStore();
    private final Map<String, Jian> jians = new ConcurrentHashMap<>();
    private final Map<String, ToolRegistry> toolRegistryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean streaming = new AtomicBoolean(false);

    public HanakoEngine(Path home) {
        this.home = home;
        this.workDir = home.resolve("desk");
        java.nio.file.Path desk = this.workDir;
        try {
            java.nio.file.Files.createDirectories(desk);
        } catch (java.io.IOException ignored) {
            // 工作目录创建失败则沿用 home
        }
        seedDefaultAgent();
    }

    // ===================== 生命周期 =====================

    private void seedDefaultAgent() {
        Agent hanako = new Agent("hanako", "Hanako");
        hanako.setIdentity("你是一个温柔、可靠、有记忆的私人 AI 助理 Hanako。"
                + "你记住用户说过的话，主动关心，但不过度打扰。");
        hanako.setIshiki("以「像人」为目标：自然、克制、有分寸。需要时主动使用工具帮助用户完成真实任务。");
        hanako.setDefault(true);
        agents.put(hanako.getId(), hanako);
    }

    /** 注册 provider 到 AiApi，并刷新默认路由（按当前模型配置挑选端点）。 */
    public synchronized void applyProviders() {
        AiApi.registerAll(buildRegisterJson());
        AiApi.setRouter((endpoints, ctx) -> {
            ModelConfig.Role role = ctxRole(ctx);
            ModelConfig cfg = pickModel(role);
            if (cfg == null) {
                return null;
            }
            String want = cfg.getEndpointId();
            for (com.flora.ai.api.Endpoint e : endpoints) {
                if (e.id().equals(want) && e.capability() == com.flora.ai.api.IOMode.STREAM) {
                    return e;
                }
            }
            return null;
        });
    }

    private String buildRegisterJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProviderConfig p : providers) {
            if (!p.isEnabled()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("apiKind", p.getApiKind());
            m.put("modelId", firstModelOf(p.getApiKind()));
            m.put("baseUrl", p.getBaseUrl());
            m.put("apiKey", p.getApiKey());
            m.put("default", true);
            m.put("capabilities", List.of("CHAT", "STREAM", "JSON"));
            list.add(m);
        }
        return JsonBuilder.toJsonString(list);
    }

    private String firstModelOf(String apiKind) {
        // 找一个已配置的角色模型名；否则用占位（用户需在设置里配置具体模型）
        for (ModelConfig cfg : models.values()) {
            if (cfg.getEndpointId() != null && cfg.getEndpointId().startsWith(apiKind + "@")) {
                return cfg.getDisplayName();
            }
        }
        return switch (apiKind) {
            case "OPENAI_OFFICIAL", "OPENAI_LIKE" -> "gpt-4o-mini";
            case "ANTHROPIC_OFFICIAL" -> "claude-sonnet-4-0";
            case "GEMINI_OFFICIAL" -> "gemini-1.5-flash";
            case "DEEPSEEK_OFFICIAL" -> "deepseek-chat";
            default -> "unknown";
        };
    }

    private ModelConfig.Role ctxRole(TaskContext ctx) {
        Object v = ctx.get("role");
        if (v instanceof ModelConfig.Role r) {
            return r;
        }
        return ModelConfig.Role.CHAT;
    }

    private ModelConfig pickModel(ModelConfig.Role role) {
        ModelConfig cfg = models.get(role.name());
        if (cfg == null) {
            cfg = models.get(ModelConfig.Role.CHAT.name());
        }
        return cfg;
    }

    // ===================== Agent =====================

    public List<Agent> listAgents() {
        return new ArrayList<>(agents.values());
    }

    public Agent getAgent(String id) {
        return agents.get(id);
    }

    public synchronized Agent saveAgent(Agent agent) {
        if (agent.getId() == null || agent.getId().isBlank()) {
            agent.setId(UUID.randomUUID().toString());
        }
        agents.put(agent.getId(), agent);
        return agent;
    }

    public synchronized void deleteAgent(String id) {
        agents.remove(id);
    }

    // ===================== Session =====================

    public List<Session> listSessions(String agentId) {
        List<Session> result = new ArrayList<>();
        for (Session s : sessions.values()) {
            if (agentId == null || agentId.equals(s.getAgentId())) {
                result.add(s);
            }
        }
        result.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return result;
    }

    public Session getSession(String id) {
        return sessions.get(id);
    }

    public synchronized Session newSession(String agentId) {
        String aid = agentId == null ? "hanako" : agentId;
        Session s = new Session(UUID.randomUUID().toString(), aid, "新对话");
        sessions.put(s.getId(), s);
        return s;
    }

    public synchronized void deleteSession(String id) {
        sessions.remove(id);
    }

    // ===================== Models / Providers =====================

    public List<ProviderConfig> listProviders() {
        return new ArrayList<>(providers);
    }

    public synchronized void saveProvider(ProviderConfig p) {
        providers.removeIf(x -> x.getId().equals(p.getId()));
        providers.add(p);
    }

    public Map<String, ModelConfig> listModels() {
        return new java.util.LinkedHashMap<>(models);
    }

    public synchronized void saveModel(ModelConfig m) {
        models.put(m.getRole().name(), m);
    }

    // ===================== Memory =====================

    public TaggedFactStore memory() {
        return memory;
    }

    public synchronized void remember(String text, List<String> tags) {
        memory.put(new MemoryFact(UUID.randomUUID().toString(), text, tags));
    }

    // ===================== Desk / Jian =====================

    public Path workDir() {
        return workDir;
    }

    public List<Jian> listJians(String agentId) {
        List<Jian> result = new ArrayList<>();
        for (Jian j : jians.values()) {
            if (agentId == null || agentId.equals(j.getAgentId())) {
                result.add(j);
            }
        }
        return result;
    }

    public synchronized Jian saveJian(Jian jian) {
        if (jian.getId() == null || jian.getId().isBlank()) {
            jian.setId(UUID.randomUUID().toString());
        }
        jians.put(jian.getId(), jian);
        return jian;
    }

    public synchronized void deleteJian(String id) {
        jians.remove(id);
    }

    // ===================== 工具 =====================

    private ToolRegistry toolsFor(String agentId) {
        return toolRegistryCache.computeIfAbsent(agentId, k -> {
            ToolRegistry reg = new ToolRegistry();
            reg.add(new ReadFileTool(workDir));
            reg.add(new WriteFileTool(workDir));
            reg.add(new TerminalTool(workDir, 60));
            reg.add(new WebFetchTool());
            reg.add(new TodoTool(memory));
            return reg;
        });
    }

    public List<ToolSpec> toolSpecs(String agentId) {
        return toolsFor(agentId).specs();
    }

    // ===================== 对话轮 =====================

    /** 是否正在流式推理。 */
    public boolean isStreaming() {
        return streaming.get();
    }

    /**
     * 执行一轮对话（含工具调用循环），通过 {@code sink} 增量广播事件。
     * 事件类型对齐 openhanako 的 ws-protocol：text_delta / thinking_delta / tool_start / tool_end / turn_end。
     */
    public synchronized void runTurn(String sessionId, String userText, EventSink sink) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            sink.emit(Map.of("type", "error", "message", "会话不存在: " + sessionId));
            return;
        }
        if (!streaming.compareAndSet(false, true)) {
            sink.emit(Map.of("type", "error", "message", "已有对话在进行中"));
            return;
        }
        try {
            ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, userText);
            userMsg.setId(UUID.randomUUID().toString());
            session.getMessages().add(userMsg);

            runTurnLoop(session, sink);

            if (session.getTitle() == null || session.getTitle().isBlank()
                    || "新对话".equals(session.getTitle())) {
                session.setTitle(titleFrom(userText));
            }
            session.setUpdatedAt(Instant.now().toEpochMilli());
            sink.emit(Map.of("type", "turn_end"));
        } finally {
            streaming.set(false);
        }
    }

    private void runTurnLoop(Session session, EventSink sink) {
        int maxRounds = 6;
        for (int round = 0; round < maxRounds; round++) {
            boolean tooled = streamOnce(session, sink);
            if (!tooled) {
                return;
            }
        }
        sink.emit(Map.of("type", "error", "message", "已达最大工具调用轮数，停止以避免死循环"));
    }

    /** 流式调用一次模型；若模型发起工具调用则返回 true（需继续下一轮）。 */
    private boolean streamOnce(Session session, EventSink sink) {
        Agent agent = agents.getOrDefault(session.getAgentId(), agents.get("hanako"));
        List<Message> messages = toApiMessages(session);
        List<ToolSpec> specs = toolSpecs(session.getAgentId());

        ChatRequest request = ChatRequest.builder()
                .system(buildSystemPrompt(agent))
                .messages(messages)
                .tools(specs)
                .build();

        StreamingClient client = AiApi.getByContext(TaskContext.of("capability", "STREAM"));
        StringBuilder assistantText = new StringBuilder();
        boolean thinking = false;
        StringBuilder thinkingBuf = new StringBuilder();
        List<ToolCall> pendingCalls = new ArrayList<>();
        Map<String, Map<String, Object>> callArgs = new LinkedHashMap<>();

        try (StreamIterator it = client.stream(request)) {
            while (it.hasNext()) {
                StreamEvent e = it.next();
                if (e instanceof StreamEvent.Text t) {
                    if (thinking) {
                        sink.emit(Map.of("type", "thinking_end"));
                        thinking = false;
                    }
                    assistantText.append(t.delta());
                    sink.emit(Map.of("type", "text_delta", "delta", t.delta()));
                } else if (e instanceof StreamEvent.Thinking th) {
                    if (!thinking) {
                        thinking = true;
                        sink.emit(Map.of("type", "thinking_start"));
                    }
                    thinkingBuf.append(th.delta());
                    sink.emit(Map.of("type", "thinking_delta", "delta", th.delta()));
                } else if (e instanceof StreamEvent.ToolCallCompleted tc) {
                    pendingCalls.add(tc.call());
                    callArgs.put(tc.call().id(), tc.call().arguments());
                } else if (e instanceof StreamEvent.Error err) {
                    sink.emit(Map.of("type", "error", "message", err.message()));
                }
            }
        } catch (RuntimeException ex) {
            sink.emit(Map.of("type", "error", "message", "推理失败: " + ex.getMessage()));
            return false;
        }

        // 记录 assistant 消息
        ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, assistantText.toString());
        assistantMsg.setId(UUID.randomUUID().toString());
        session.getMessages().add(assistantMsg);

        if (pendingCalls.isEmpty()) {
            return false;
        }

        // 执行工具调用并回填
        for (ToolCall call : pendingCalls) {
            Map<String, Object> args = callArgs.getOrDefault(call.id(), Map.of());
            sink.emit(Map.of("type", "tool_start", "name", call.name()));
            String result = toolsFor(session.getAgentId()).execute(call.name(), args);
            sink.emit(Map.of("type", "tool_end", "name", call.name(), "success",
                    !result.startsWith("错误"), "details", Map.of("result", result)));
            // 记录到 session 消息便于前端展示与回放
            ChatMessage toolMsg = new ChatMessage(ChatMessage.Role.TOOL, result);
            toolMsg.setId(UUID.randomUUID().toString());
            toolMsg.setToolResults(List.of(Map.of("name", call.name(), "result", result)));
            session.getMessages().add(toolMsg);
        }
        // 把 assistant 的工具调用意图也存为结构，便于回放
        assistantMsg.setToolCalls(pendingCalls.stream()
                .map(c -> Map.<String, Object>of("name", c.name(), "arguments", c.arguments()))
                .toList());
        return true;
    }

    private List<Message> toApiMessages(Session session) {
        List<Message> out = new ArrayList<>();
        for (ChatMessage cm : session.getMessages()) {
            switch (cm.getRole()) {
                case USER -> out.add(Message.of(Message.Role.USER, cm.getText()));
                case ASSISTANT -> {
                    if (cm.getToolCalls() != null && !cm.getToolCalls().isEmpty()) {
                        List<ToolCall> calls = new ArrayList<>();
                        for (Map<String, Object> tc : cm.getToolCalls()) {
                            String name = String.valueOf(tc.get("name"));
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = tc.get("arguments") instanceof Map
                                    ? (Map<String, Object>) tc.get("arguments") : Map.of();
                            calls.add(ToolCall.of(UUID.randomUUID().toString(), name, args));
                        }
                        out.add(Message.assistantWithCalls(calls, cm.getText()));
                    } else {
                        out.add(Message.of(Message.Role.ASSISTANT, cm.getText()));
                    }
                }
                case TOOL -> {
                    // 取工具结果文本回填
                    String result = cm.getText();
                    String callId = null;
                    if (cm.getToolResults() != null && !cm.getToolResults().isEmpty()) {
                        callId = String.valueOf(cm.getToolResults().get(0).get("name"));
                    }
                    out.add(Message.toolResult(callId == null ? "tool" : callId, result));
                }
                default -> { /* SYSTEM 不在消息流里 */ }
            }
        }
        return out;
    }

    private String buildSystemPrompt(Agent agent) {
        StringBuilder sb = new StringBuilder();
        if (agent.getIdentity() != null) {
            sb.append(agent.getIdentity()).append("\n\n");
        }
        if (agent.getIshiki() != null) {
            sb.append("[心识] ").append(agent.getIshiki()).append("\n\n");
        }
        // 记忆检索：抽取最近用户消息关键词做标签查询
        List<MemoryFact> related = memory.query(topTags());
        if (!related.isEmpty()) {
            sb.append("[记忆]\n");
            for (MemoryFact f : related.subList(0, Math.min(8, related.size()))) {
                sb.append("- ").append(f.getText()).append("\n");
            }
            sb.append("\n");
        }
        // 书桌笺
        List<Jian> open = listJians(agent.getId()).stream().filter(j -> !j.isDone()).toList();
        if (!open.isEmpty()) {
            sb.append("[书桌·待办笺]\n");
            for (Jian j : open) {
                sb.append("- ").append(j.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<String> topTags() {
        // 简单取全部标签（query 为空即返回全量按时间排序）
        return List.of();
    }

    private String titleFrom(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 24) {
            return t.isEmpty() ? "新对话" : t;
        }
        return t.substring(0, 24) + "…";
    }

    /** 当前数据根目录。 */
    public Path home() {
        return home;
    }
}
