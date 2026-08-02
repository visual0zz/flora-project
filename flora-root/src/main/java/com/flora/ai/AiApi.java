package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ClientSpec;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.MultimodalClient;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.provider.AnthropicOfficialProvider;
import com.flora.ai.api.provider.DeepSeekOfficialProvider;
import com.flora.ai.api.provider.GeminiOfficialProvider;
import com.flora.ai.api.provider.OpenAiLikeProvider;
import com.flora.ai.api.provider.OpenAiOfficialProvider;
import com.flora.ai.api.spi.AiProvider;
import com.flora.ai.api.spi.Router;
import com.flora.ai.api.spi.TaskContext;
import com.flora.codec.json.JsonBuilder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面：注册与使用分离。
 * <p><b>注册阶段</b>（加载时）：从配置读出所有端点注册（{@link #register}/{@link #registerAll}），
 * 并注册路由解析器（{@link #setRouter}）。注册时校验端点声明的 {@code capabilities}
 * 必须被对应 provider 支持，否则报错；并为每个能力预建一个 client 实例（每能力一对象）。</p>
 * <p><b>使用阶段</b>：获取单个能力 client——采用 chat/stream/json 的能力信息由
 * {@link TaskContext} 携带，{@link Router} 据此返回 {@link ClientSpec}（端点+能力），
 * {@link #getByContext} 返回预建的对应 client；或通过 {@link #getByName}/{@link #getDefault}
 * 带 {@code Class} 参数明确指定能力。</p>
 *
 * <pre>{@code
 * // 加载阶段
 * AiApi.registerAll("[{\"apiKind\":\"OPENAI_OFFICIAL\",\"modelId\":\"gpt-5\",...}, ...]");
 * AiApi.setRouter((endpoints, ctx) -> ...);
 *
 * // 使用阶段：能力由调用方左侧类型指定（Router 需返回对应能力的 ClientSpec）
 * ChatClient chat = AiApi.getByContext(TaskContext.of("capability", "CHAT"));
 * String answer = chat.ask(ChatRequest.builder().message(...).build());
 *
 * // 或明确指定
 * StreamingClient stream = AiApi.getByName("my-gpt", StreamingClient.class);
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    /** 端点目录（id → Endpoint）。 */
    private static final Map<String, Endpoint> ENDPOINTS = new LinkedHashMap<>();
    /** 注册时按能力预建的 client 实例（id → 能力 → client 对象）。 */
    private static final Map<String, Map<Capability, Object>> CLIENTS = new LinkedHashMap<>();
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
        PROVIDERS.removeIf(p -> p.apiKind() == provider.apiKind());
        PROVIDERS.add(provider);
    }

    /** 列出所有已注册的 provider。 */
    public static List<AiProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    // ── 注册阶段 ──

    /**
     * 注册单端点：解析 JSON 并加入目录，为每个能力预建一个 client 实例。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code tags}/{@code capabilities}/{@code spec}。
     * 注册时校验声明的 {@code capabilities} 必须 ⊆ provider 支持能力，否则报错。
     * {@code default:true} 必须唯一。</p>
     */
    public static Endpoint register(String jsonConfig) {
        return register(Endpoint.fromJson(jsonConfig));
    }

    /**
     * 批量注册：解析配置 JSON 数组并逐个注册。
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
        validateCapabilities(endpoint);
        ENDPOINTS.put(endpoint.id(), endpoint);
        // 为每个能力预建一个 client 实例（每能力一对象）
        Map<Capability, Object> perCap = new EnumMap<>(Capability.class);
        AiProvider provider = providerFor(endpoint.apiKind());
        for (Capability c : endpoint.capabilities()) {
            perCap.put(c, provider.createClient(endpoint, c));
        }
        CLIENTS.put(endpoint.id(), perCap);
        return endpoint;
    }

    /** 校验端点声明的能力被 provider 支持；不支持的报错（不静默忽略）。 */
    private static void validateCapabilities(Endpoint endpoint) {
        AiProvider provider = providerFor(endpoint.apiKind());
        for (Capability c : endpoint.capabilities()) {
            if (!provider.supportedCapabilities().contains(c)) {
                throw new IllegalArgumentException(
                        "provider " + provider.name() + " 不支持能力 " + c + "（端点 " + endpoint.id() + "）");
            }
        }
    }

    /** 注销端点。 */
    public static void unregister(String id) {
        ENDPOINTS.remove(id);
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

    // ── 使用阶段：返回单个 client ──

    /**
     * 按任务上下文获取单个能力 client。
     * <p>Router 根据 ctx（含能力信息）返回 {@link ClientSpec}；未注册 Router、
     * route 返回 null、或路由抛异常 → fallback 默认端点 + CHAT 能力。
     * 返回类型由调用方左侧变量指定（如 {@code ChatClient c = getByContext(ctx)}），
     * 若 Router 返回的能力与目标类型不符则 {@link ClassCastException}。</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> T getByContext(TaskContext context) {
        ClientSpec spec = null;
        Router r = router;
        if (r != null) {
            try {
                spec = r.route(endpoints(), context);
            } catch (RuntimeException ignored) {
                // 路由异常 → fallback 默认
            }
        }
        if (spec == null) {
            Endpoint def = getDefaultEndpoint();
            if (def == null) {
                throw new IllegalStateException("无可用端点：未注册 Router 且没有默认端点");
            }
            spec = ClientSpec.of(def, Capability.CHAT);
        }
        return (T) clientOf(spec);
    }

    /** 按注册名 + 能力类型获取单能力 client。 */
    public static <T> T getByName(String id, Class<T> capabilityType) {
        Map<Capability, Object> perCap = CLIENTS.get(id);
        if (perCap == null) {
            throw new IllegalArgumentException("未注册端点: " + id);
        }
        Capability capability = capabilityOf(capabilityType);
        return capabilityType.cast(clientOf(ClientSpec.of(ENDPOINTS.get(id), capability)));
    }

    /** 默认端点 + 指定能力的 client；无默认端点抛 {@link IllegalStateException}。 */
    public static <T> T getDefault(Class<T> capabilityType) {
        Endpoint endpoint = getDefaultEndpoint();
        if (endpoint == null) {
            throw new IllegalStateException("未注册默认端点");
        }
        Capability capability = capabilityOf(capabilityType);
        return capabilityType.cast(clientOf(ClientSpec.of(endpoint, capability)));
    }

    // ── 管理 ──

    /** 列出所有注册端点。 */
    public static List<Endpoint> endpoints() {
        return List.copyOf(ENDPOINTS.values());
    }

    /** 默认端点（isDefault=true）；无则 null。 */
    private static Endpoint getDefaultEndpoint() {
        for (Endpoint e : ENDPOINTS.values()) {
            if (e.isDefault()) {
                return e;
            }
        }
        return null;
    }

    /** 查预建 client；缺失（能力未声明）抛异常。 */
    private static Object clientOf(ClientSpec spec) {
        Map<Capability, Object> perCap = CLIENTS.get(spec.endpoint().id());
        if (perCap == null) {
            throw new IllegalArgumentException("未注册端点: " + spec.endpoint().id());
        }
        Object client = perCap.get(spec.capability());
        if (client == null) {
            throw new IllegalStateException("端点 " + spec.endpoint().id()
                    + " 未声明能力 " + spec.capability());
        }
        return client;
    }

    /** Class → Capability 映射。 */
    private static Capability capabilityOf(Class<?> type) {
        if (type == ChatClient.class) {
            return Capability.CHAT;
        }
        if (type == StreamingClient.class) {
            return Capability.STREAM;
        }
        if (type == JsonClient.class) {
            return Capability.JSON;
        }
        if (type == MultimodalClient.class) {
            return Capability.MULTIMODAL;
        }
        throw new IllegalArgumentException("未知 client 能力类型: " + type.getName());
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
