package com.flora.ai;

import com.flora.ai.api.*;
import com.flora.ai.api.provider.AnthropicOfficialProvider;
import com.flora.ai.api.provider.DeepSeekOfficialProvider;
import com.flora.ai.api.provider.GeminiOfficialProvider;
import com.flora.ai.api.provider.OpenAiLikeProvider;
import com.flora.ai.api.provider.OpenAiOfficialProvider;
import com.flora.ai.api.spi.AiProvider;
import com.flora.ai.api.spi.Router;
import com.flora.ai.api.spi.TaskContext;
import com.flora.codec.json.JsonBuilder;
import com.flora.tag.ModuleEntry;
import com.flora.tag.ThreadFragile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面：注册与使用分离。
 * <p><b>注册阶段</b>（加载时）：从配置读出所有端点注册（{@link #register}/{@link #registerAll}），
 * 每个端点的 capabilities 展开为多个 Endpoint（id = {@code 原id:capability}），
 * 每个 Endpoint 与一个 client 实例一对一预建。注册路由解析器（{@link #setRouter}）。</p>
 * <p><b>使用阶段</b>：获取单个能力 client——采用 chat/stream/json 的能力信息由
 * {@link TaskContext} 携带，{@link Router} 据此返回选中的 {@link Endpoint}（已含能力），
 * {@link #getByContext} 返回预建的对应 client；或通过 {@link #getByName}/{@link #getDefault}
 * 带 {@code Class} 参数明确指定能力。</p>
 *
 * <pre>{@code
 * // 加载阶段：capabilities 展开为 my-gpt:CHAT / my-gpt:STREAM / my-gpt:JSON
 * AiApi.registerAll("[{\"id\":\"my-gpt\",\"apiKind\":\"OPENAI_OFFICIAL\"," +
 *     "\"modelId\":\"gpt-5\",\"capabilities\":[\"CHAT\",\"STREAM\",\"JSON\"],...}]");
 * AiApi.setRouter((endpoints, ctx) -> ...);
 *
 * // 使用阶段
 * ChatClient chat = AiApi.getByContext(TaskContext.of("capability", "CHAT"));
 * StreamingClient stream = AiApi.getByName("my-gpt:STREAM", StreamingClient.class);
 * }</pre>
 */
@ModuleEntry
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    /** 端点目录（展开后 id → Endpoint）。 */
    private static final Map<String, Endpoint> ENDPOINTS = new LinkedHashMap<>();
    /** 注册时预建的 client 实例（展开后 id → client，一对一）。 */
    private static final Map<String, Object> CLIENTS = new LinkedHashMap<>();
    private static Router router;

    static {
        // 内置 provider 直接代码注册（不走 SPI）
        registerProvider(new OpenAiOfficialProvider());
        registerProvider(new AnthropicOfficialProvider());
        registerProvider(new GeminiOfficialProvider());
        registerProvider(new OpenAiLikeProvider());
        registerProvider(new DeepSeekOfficialProvider());
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
        PROVIDERS.removeIf(p -> p.apiSchema() == provider.apiSchema());
        PROVIDERS.add(provider);
    }

    /** 列出所有已注册的 provider。 */
    public static List<AiProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    // ── 注册阶段 ──

    /**
     * 注册单端点：解析 JSON，capabilities 展开为多个 Endpoint 并各自预建 client。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code tags}/{@code capabilities}/{@code spec}。
     * 展开后每个 Endpoint 的 {@code capability} 必须 ⊆ provider 支持能力，否则报错。
     * {@code default:true} 在同一 baseId 内唯一。</p>
     */
    @ThreadFragile("全局注册表无锁，运行时并发注册/注销与查询需外部同步")
    public static List<Endpoint> register(String jsonConfig) {
        List<Endpoint> expanded = Endpoint.fromJsonAll(jsonConfig);
        List<Endpoint> registered = new ArrayList<>();
        for (Endpoint e : expanded) {
            validateCapability(e);
            ENDPOINTS.put(e.id(), e);
            CLIENTS.put(e.id(), providerFor(e.apiKind()).createClient(e));
            registered.add(e);
        }
        return registered;
    }

    /** 批量注册：解析配置 JSON 数组并逐个注册。 */
    @ThreadFragile("全局注册表无锁，运行时并发注册/注销与查询需外部同步")
    public static List<Endpoint> registerAll(String jsonArray) {
        List<?> list = com.flora.codec.json.JsonParser.parseArray(jsonArray);
        List<Endpoint> registered = new ArrayList<>();
        for (Object item : list) {
            registered.addAll(register(JsonBuilder.toJsonString(item)));
        }
        return registered;
    }

    /** 校验端点能力被 provider 支持；不支持的报错（不静默忽略）。 */
    private static void validateCapability(Endpoint endpoint) {
        AiProvider provider = providerFor(endpoint.apiKind());
        if (!provider.supportedCapabilities().contains(endpoint.capability())) {
            throw new IllegalArgumentException(
                    "provider " + provider.name() + " 不支持能力 " + endpoint.capability()
                            + "（端点 " + endpoint.id() + "）");
        }
    }

    /** 注销端点（含其展开的所有能力条目）。 */
    @ThreadFragile("全局注册表无锁，运行时并发注册/注销与查询需外部同步")
    public static void unregister(String baseId) {
        List<String> toRemove = new ArrayList<>();
        for (String id : ENDPOINTS.keySet()) {
            if (id.startsWith(baseId + ":")) {
                toRemove.add(id);
            } else if (id.equals(baseId)) {
                toRemove.add(id);
            }
        }
        for (String id : toRemove) {
            ENDPOINTS.remove(id);
            CLIENTS.remove(id);
        }
    }

    /** 注册路由解析器。 */
    @ThreadFragile("router 非 volatile，并发 setRouter 与 getByContext 可能读到陈旧值")
    public static void setRouter(Router router) {
        AiApi.router = router;
    }

    /** 当前路由解析器；未注册返回 null。 */
    public static Router router() {
        return router;
    }

    // ── 使用阶段：返回单个 client ──

    /**
     * 按任务上下文获取单个能力 client。
     * <p>Router 根据 ctx（含能力信息）返回选中的 Endpoint；未注册 Router、
     * route 返回 null、或路由抛异常 → fallback 到默认端点（CHAT 能力优先）。
     * 返回类型由调用方左侧变量指定，若实际 client 与目标类型不符则 {@link ClassCastException}。</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> T getByContext(TaskContext context) {
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
            selected = defaultEndpoint();
        }
        if (selected == null) {
            throw new IllegalStateException("无可用端点：未注册 Router 且没有默认端点");
        }
        return (T) clientOf(selected);
    }

    /** 按注册名（展开后 id）+ 能力类型获取单能力 client。 */
    public static <T> T getByName(String id, Class<T> capabilityType) {
        Endpoint endpoint = ENDPOINTS.get(id);
        if (endpoint == null) {
            throw new IllegalArgumentException("未注册端点: " + id);
        }
        capabilityOf(capabilityType); // 校验类型与能力匹配
        return capabilityType.cast(clientOf(endpoint));
    }

    /** 默认端点 + 指定能力的 client；无默认端点抛 {@link IllegalStateException}。 */
    public static <T> T getDefault(Class<T> capabilityType) {
        IOMode capability = capabilityOf(capabilityType);
        Endpoint endpoint = defaultEndpoint();
        if (endpoint == null) {
            throw new IllegalStateException("未注册默认端点");
        }
        return capabilityType.cast(clientOf(endpoint));
    }

    // ── 管理 ──

    /** 列出所有注册端点（展开后）。 */
    public static List<Endpoint> endpoints() {
        return List.copyOf(ENDPOINTS.values());
    }

    /** 默认端点：优先 isDefault 且 CHAT 能力；否则任一 isDefault。 */
    private static Endpoint defaultEndpoint() {
        Endpoint fallback = null;
        for (Endpoint e : ENDPOINTS.values()) {
            if (e.isDefault()) {
                if (e.capability() == IOMode.CHAT) {
                    return e;
                }
                if (fallback == null) {
                    fallback = e;
                }
            }
        }
        return fallback;
    }

    /** 查预建 client。 */
    private static Object clientOf(Endpoint endpoint) {
        Object client = CLIENTS.get(endpoint.id());
        if (client == null) {
            throw new IllegalStateException("未注册端点: " + endpoint.id());
        }
        return client;
    }

    /** Class → IOMode 映射。 */
    private static IOMode capabilityOf(Class<?> type) {
        if (type == ChatClient.class) {
            return IOMode.CHAT;
        }
        if (type == StreamingClient.class) {
            return IOMode.STREAM;
        }
        if (type == JsonClient.class) {
            return IOMode.JSON;
        }
        throw new IllegalArgumentException("未知 client 能力类型: " + type.getName());
    }

    private static AiProvider providerFor(ApiSchema kind) {
        for (AiProvider p : PROVIDERS) {
            if (p.apiSchema() == kind) {
                return p;
            }
        }
        throw new IllegalArgumentException("没有 provider 支持 apiSchema: " + kind);
    }
}
