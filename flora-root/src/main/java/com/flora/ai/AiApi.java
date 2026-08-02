package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.RegisteredModel;
import com.flora.ai.provider.anthropic.AnthropicProvider;
import com.flora.ai.provider.deepseek.DeepSeekProvider;
import com.flora.ai.provider.gemini.GeminiProvider;
import com.flora.ai.provider.openai.OpenAiCompatibleProvider;
import com.flora.ai.provider.openai.OpenAiProvider;
import com.flora.ai.route.Router;
import com.flora.ai.route.TaskContext;
import com.flora.ai.spi.AiProvider;

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
 * RegisteredModel m = AiApi.register("{\"apiKind\":\"OPENAI_OFFICIAL\",\"modelId\":\"gpt-5\",...}");
 * ChatClient c = AiApi.client(m);                      // 按注册模型创建
 * String answer = c.ask(ChatRequest.builder().message(...).build());
 *
 * AiApi.setRouter((models, ctx) -> ...);               // 自定义路由
 * ChatClient rc = AiApi.routed(TaskContext.of("kind", "reasoning"));
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = new ArrayList<>();
    private static final Map<String, RegisteredModel> MODELS = new LinkedHashMap<>();
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
    public static RegisteredModel register(String jsonConfig) {
        RegisteredModel model = RegisteredModel.fromJson(jsonConfig);
        return register(model);
    }

    /** 注册 client：直接传入模型对象。 */
    public static RegisteredModel register(RegisteredModel model) {
        if (model.isDefault()) {
            RegisteredModel existing = defaultModel();
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

    /** 列出所有注册模型。 */
    public static List<RegisteredModel> models() {
        return List.copyOf(MODELS.values());
    }

    /** 默认模型（isDefault=true）；无则 null。 */
    public static RegisteredModel defaultModel() {
        for (RegisteredModel m : MODELS.values()) {
            if (m.isDefault()) {
                return m;
            }
        }
        return null;
    }

    // ── 创建 client（按明确指定的模型/端点）──

    /** 按注册模型创建 client；无对应 provider 抛异常。 */
    public static ChatClient client(RegisteredModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model 不能为空");
        }
        AiProvider provider = providerFor(model.apiKind());
        return provider.createClient(model);
    }

    /** 便捷：按 API 类型 + 端点创建 client（不入目录，不设 default）。 */
    public static ChatClient client(ApiKind kind, String baseUrl, String apiKey) {
        RegisteredModel m = RegisteredModel.of(kind, "model", baseUrl, apiKey);
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
     * 路由辅助：用 Router 从注册模型中选择并返回其 client。
     * <p>未注册 Router、或 route 返回 null、或路由抛异常 → fallback 到默认模型
     * （无默认模型则抛异常）。真正路由决策由用户 Router 完成。</p>
     */
    public static ChatClient routed(TaskContext context) {
        RegisteredModel selected = null;
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
