package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.EndpointClients;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面：注册与使用分离。
 * <p><b>注册阶段</b>（加载时）：从配置读出所有端点注册（{@link #register}/{@link #registerAll}），
 * 并注册路由解析器（{@link #setRouter}）。注册时校验端点声明的 {@code capabilities}
 * 必须被对应 provider 支持，否则报错。</p>
 * <p><b>使用阶段</b>：获取端点后通过 {@link EndpointClients} 按能力取单能力 client：
 * {@link #getByContext}（Router 选端点）、{@link #getByName}（按注册名取特定）、
 * {@link #getDefault}（默认）。</p>
 *
 * <pre>{@code
 * // 加载阶段
 * AiApi.registerAll("[{\"apiKind\":\"OPENAI_OFFICIAL\",\"modelId\":\"gpt-5\",...}, ...]");
 * AiApi.setRouter((endpoints, ctx) -> ...);
 *
 * // 使用阶段
 * EndpointClients clients = AiApi.getByContext(TaskContext.of("kind", "reasoning"));
 * String answer = clients.chat().ask(ChatRequest.builder().message(...).build());
 * ChatClient specific = AiApi.getByName("my-gpt", ChatClient.class);
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    private static final Map<String, Endpoint> CLIENTS = new LinkedHashMap<>();
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
     * 注册单端点：解析 JSON 并加入目录。
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
        CLIENTS.put(endpoint.id(), endpoint);
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

    // ── 使用阶段 ──

    /**
     * 按任务上下文获取端点的 client 容器：Router 从注册端点中选择。
     * <p>未注册 Router、route 返回 null、或路由抛异常 → fallback 到默认端点。
     * 无默认端点则抛 {@link IllegalStateException}。</p>
     */
    public static EndpointClients getByContext(TaskContext context) {
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
        return buildClients(selected);
    }

    /** 按注册名获取端点的 client 容器；未注册抛 {@link IllegalArgumentException}。 */
    public static EndpointClients getByName(String id) {
        Endpoint endpoint = CLIENTS.get(id);
        if (endpoint == null) {
            throw new IllegalArgumentException("未注册端点: " + id);
        }
        return buildClients(endpoint);
    }

    /** 按注册名 + 能力类型获取单能力 client。 */
    public static <T> T getByName(String id, Class<T> capabilityType) {
        return getByName(id).as(capabilityType);
    }

    /** 获取默认端点的 client 容器；无默认端点抛 {@link IllegalStateException}。 */
    public static EndpointClients getDefault() {
        Endpoint endpoint = getDefaultEndpoint();
        if (endpoint == null) {
            throw new IllegalStateException("未注册默认端点");
        }
        return buildClients(endpoint);
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

    /** 按端点声明的能力构造一批单能力 client。 */
    private static EndpointClients buildClients(Endpoint endpoint) {
        AiProvider provider = providerFor(endpoint.apiKind());
        Map<Class<?>, Object> clients = new HashMap<>();
        for (Capability c : endpoint.capabilities()) {
            Object client = provider.createClient(endpoint, c);
            clients.put(interfaceOf(c), client);
        }
        return EndpointClients.of(clients);
    }

    /** 能力 → 对应 client 接口类型。 */
    private static Class<?> interfaceOf(Capability c) {
        return switch (c) {
            case CHAT -> com.flora.ai.api.ChatClient.class;
            case STREAM -> com.flora.ai.api.StreamingClient.class;
            case JSON -> com.flora.ai.api.JsonClient.class;
            case MULTIMODAL -> com.flora.ai.api.MultimodalClient.class;
            default -> throw new IllegalArgumentException("能力尚无对应 client 接口: " + c);
        };
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
