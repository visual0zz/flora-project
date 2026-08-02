package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.provider.AnthropicProvider;
import com.flora.ai.api.provider.DeepSeekProvider;
import com.flora.ai.api.provider.GeminiProvider;
import com.flora.ai.api.provider.OpenAiCompatibleProvider;
import com.flora.ai.api.provider.OpenAiProvider;
import com.flora.ai.api.spi.Router;
import com.flora.ai.api.spi.TaskContext;
import com.flora.ai.api.spi.AiProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面：模型目录 + 注册 + client 创建。不参与路由分发。
 * <p><b>职责</b>：provider 注册（内置代码注册，外部 SPI 附加）、client 注册（JSON）、
 * 列出模型目录、按明确指定的模型创建 client。</p>
 * <p><b>不职责</b>：自动路由选模型——由用户注册的 {@link Router} 负责；
 * 未注册 Router 时 {@link #routed(TaskContext)} 一律返回默认模型。</p>
 *
 * <pre>{@code
 * Endpoint m = AiApi.register("{\"apiKind\":\"OPENAI_OFFICIAL\",\"modelId\":\"gpt-5\",...}");
 * ChatClient c = AiApi.client(m);                      // 按注册端点创建
 * String answer = c.ask(ChatRequest.builder().message(...).build());
 *
 * AiApi.setRouter((models, ctx) -> ...);               // 自定义路由
 * ChatClient rc = AiApi.routed(TaskContext.of("kind", "reasoning"));
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    private static final Map<String, Endpoint> MODELS = new LinkedHashMap<>();
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

    // ── provider 注册 ──

    /** 注册 provider（内置代码注册 / 外部 SPI 附加）。 */
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

    // ── client 注册（JSON）──

    /**
     * 注册 client：解析 JSON 并加入目录。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code tags}/{@code spec}，其余字段进 {@code extra}。
     * 若 {@code default:true} 而目录中已有默认模型，则抛 {@link IllegalArgumentException}。
     * 重复注册相同 id 会覆盖。</p>
     */
    public static Endpoint register(String jsonConfig) {
        Endpoint model = Endpoint.fromJson(jsonConfig);
        return register(model);
    }

    /** 注册 client：直接传入模型对象。 */
    public static Endpoint register(Endpoint model) {
        if (model.isDefault()) {
            Endpoint existing = defaultModel();
            if (existing != null && !existing.id().equals(model.id())) {
                throw new IllegalArgumentException("默认模型已存在: " + existing.id()
                        + "，default 必须唯一");
            }
        }
        MODELS.put(model.id(), model);
        return model;
    }

    /** 注销 client。 */
    public static void unregister(String id) {
        MODELS.remove(id);
    }

    // ── 目录（不路由）──

    /** 列出所有注册端点。 */
    public static List<Endpoint> models() {
        return List.copyOf(MODELS.values());
    }

    /** 默认模型（isDefault=true）；无则 null。 */
    public static Endpoint defaultModel() {
        for (Endpoint m : MODELS.values()) {
            if (m.isDefault()) {
                return m;
            }
        }
        return null;
    }

    // ── 创建 client（按明确指定的模型/端点）──

    /** 按注册端点创建 client；无对应 provider 抛异常。 */
    public static ChatClient client(Endpoint model) {
        if (model == null) {
            throw new IllegalArgumentException("model 不能为空");
        }
        AiProvider provider = providerFor(model.apiKind());
        return provider.createClient(model);
    }

    /** 便捷：按 API 类型 + 端点创建 client（不入目录，不设 default）。 */
    public static ChatClient client(ApiKind kind, String baseUrl, String apiKey) {
        Endpoint m = Endpoint.of(kind, "model", baseUrl, apiKey);
        return providerFor(kind).createClient(m);
    }

    private static AiProvider providerFor(ApiKind kind) {
        for (AiProvider p : PROVIDERS) {
            if (p.apiKind() == kind) {
                return p;
            }
        }
        throw new IllegalArgumentException("没有 provider 支持 apiKind: " + kind);
    }

    // ── Router（路由由用户负责）──

    /** 注册路由解析器。 */
    public static void setRouter(Router router) {
        AiApi.router = router;
    }

    /** 当前路由解析器；未注册返回 null。 */
    public static Router router() {
        return router;
    }

    /**
     * 路由辅助：用 Router 从注册端点中选择并返回其 client。
     * <p>未注册 Router、或 route 返回 null、或路由抛异常 → fallback 到默认模型
     * （无默认模型则抛异常）。真正路由决策由用户 Router 完成。</p>
     */
    public static ChatClient routed(TaskContext context) {
        Endpoint selected = null;
        Router r = router;
        if (r != null) {
            try {
                selected = r.route(models(), context);
            } catch (RuntimeException ignored) {
                // 路由异常 → fallback 默认
            }
        }
        if (selected == null) {
            selected = defaultModel();
        }
        if (selected == null) {
            throw new IllegalStateException("无可用模型：未注册 Router 且没有默认模型");
        }
        return client(selected);
    }
}
