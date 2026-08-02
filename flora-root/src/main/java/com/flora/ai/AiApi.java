package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.provider.AnthropicProvider;
import com.flora.ai.api.provider.DeepSeekProvider;
import com.flora.ai.api.provider.GeminiProvider;
import com.flora.ai.api.provider.OpenAiCompatibleProvider;
import com.flora.ai.api.provider.OpenAiProvider;
import com.flora.ai.api.spi.AiProvider;
import com.flora.ai.api.spi.Router;
import com.flora.ai.api.spi.TaskContext;
import com.flora.codec.json.JsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面：注册与使用分离。
 * <p><b>注册阶段</b>（加载时）：从配置读出所有端点注册（{@link #register}/{@link #registerAll}），
 * 并注册路由解析器（{@link #setRouter}）。</p>
 * <p><b>使用阶段</b>：只通过三个获取接口获得 client：
 * {@link #getByContext}（Router 按任务上下文选）、{@link #getByName}（按注册名取特定）、
 * {@link #getDefault}（默认）。</p>
 *
 * <pre>{@code
 * // 加载阶段
 * AiApi.registerAll("[{\"apiKind\":\"OPENAI_OFFICIAL\",\"modelId\":\"gpt-5\",...}, ...]");
 * AiApi.setRouter((endpoints, ctx) -> ...);
 *
 * // 使用阶段
 * ChatClient c = AiApi.getByContext(TaskContext.of("kind", "reasoning"));
 * ChatClient specific = AiApi.getByName("my-gpt");
 * ChatClient def = AiApi.getDefault();
 * String answer = c.ask(ChatRequest.builder().message(...).build());
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    private static final Map<String, Endpoint> CLIENTS = new LinkedHashMap<>();
    private static Router router;

    static {
        // 内置 provider 直接代码注册（不走 SPI）
        registerProvider(new OpenAiProvider());
        registerProvider(new AnthropicProvider());
        registerProvider(new GeminiProvider());
        registerProvider(new OpenAiCompatibleProvider());
        registerProvider(new DeepSeekProvider());
        // 外部厂商经 SPI 附加注册
        for (AiProvider provider : ServiceLoader.load(AiProvider.class)) {
            registerProvider(provider);
        }
    }

    private AiApi() {
    }

    // ── provider 注册（低层接口）──

    /**
     * 注册 provider（内置代码注册 / 外部 SPI 附加）。
     * <p>低层接口：普通使用只需注册端点（{@link #register}），内置五家 provider
     * 已自动注册，外部厂商经 SPI 或本方法追加。</p>
     */
    public static void registerProvider(AiProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        PROVIDERS.removeIf(p -> p.apiKind() == provider.apiKind());
        PROVIDERS.add(provider);
    }

    /** 列出所有已注册的 provider。 */
    public static List<AiProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    // ── 注册阶段 ──

    /**
     * 注册单端点：解析 JSON 并加入目录。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code tags}/{@code spec}，其余字段进 {@code extra}
     * （供 Router 读取）。若 {@code default:true} 而目录中已有默认端点，则抛
     * {@link IllegalArgumentException}。重复注册相同 id 会覆盖。</p>
     */
    public static Endpoint register(String jsonConfig) {
        return register(Endpoint.fromJson(jsonConfig));
    }

    /**
     * 批量注册：解析配置 JSON 数组并逐个注册。
     * <p>任一项 {@code default:true} 与已有默认冲突时抛异常（default 必须唯一）。</p>
     */
    public static List<Endpoint> registerAll(String jsonArray) {
        List<?> list = com.flora.codec.json.JsonParser.parseArray(jsonArray);
        List<Endpoint> registered = new ArrayList<>();
        for (Object item : list) {
            registered.add(register(JsonBuilder.toJsonString(item)));
        }
        return registered;
    }

    /** 注册端点：直接传入对象。 */
    public static Endpoint register(Endpoint endpoint) {
        if (endpoint.isDefault()) {
            Endpoint existing = getDefaultEndpoint();
            if (existing != null && !existing.id().equals(endpoint.id())) {
                throw new IllegalArgumentException("默认端点已存在: " + existing.id()
                        + "，default 必须唯一");
            }
        }
        CLIENTS.put(endpoint.id(), endpoint);
        return endpoint;
    }

    /** 注销端点。 */
    public static void unregister(String id) {
        CLIENTS.remove(id);
    }

    /** 注册路由解析器。 */
    public static void setRouter(Router router) {
        AiApi.router = router;
    }

    /** 当前路由解析器；未注册返回 null。 */
    public static Router router() {
        return router;
    }

    // ── 使用阶段：三个获取接口 ──

    /**
     * 按任务上下文获取 client：Router 从注册端点中选择。
     * <p>未注册 Router、route 返回 null、或路由抛异常 → fallback 到默认端点。
     * 无默认端点则抛 {@link IllegalStateException}。</p>
     */
    public static ChatClient getByContext(TaskContext context) {
        Endpoint selected = null;
        Router r = router;
        if (r != null) {
            try {
                selected = r.route(endpoints(), context);
            } catch (RuntimeException ignored) {
                // 路由异常 → fallback 默认
            }
        }
        if (selected == null) {
            selected = getDefaultEndpoint();
        }
        if (selected == null) {
            throw new IllegalStateException("无可用端点：未注册 Router 且没有默认端点");
        }
        return createClient(selected);
    }

    /** 按注册名获取特定 client；未注册抛 {@link IllegalArgumentException}。 */
    public static ChatClient getByName(String id) {
        Endpoint endpoint = CLIENTS.get(id);
        if (endpoint == null) {
            throw new IllegalArgumentException("未注册端点: " + id);
        }
        return createClient(endpoint);
    }

    /** 获取默认 client；无默认端点抛 {@link IllegalStateException}。 */
    public static ChatClient getDefault() {
        Endpoint endpoint = getDefaultEndpoint();
        if (endpoint == null) {
            throw new IllegalStateException("未注册默认端点");
        }
        return createClient(endpoint);
    }

    // ── 管理 ──

    /** 列出所有注册端点。 */
    public static List<Endpoint> endpoints() {
        return List.copyOf(CLIENTS.values());
    }

    /** 默认端点（isDefault=true）；无则 null。 */
    private static Endpoint getDefaultEndpoint() {
        for (Endpoint e : CLIENTS.values()) {
            if (e.isDefault()) {
                return e;
            }
        }
        return null;
    }

    private static ChatClient createClient(Endpoint endpoint) {
        return providerFor(endpoint.apiKind()).createClient(endpoint);
    }

    private static AiProvider providerFor(ApiKind kind) {
        for (AiProvider p : PROVIDERS) {
            if (p.apiKind() == kind) {
                return p;
            }
        }
        throw new IllegalArgumentException("没有 provider 支持 apiKind: " + kind);
    }
}
