package com.flora.root.ai.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型 API 类型标识：决定使用哪家协议实现翻译请求。
 * <p>协议分类——{@code OPENAI_OFFICIAL}（OpenAI 官方）、{@code ANTHROPIC_OFFICIAL}
 * （Anthropic 官方）、{@code GEMINI_OFFICIAL}（Gemini 官方）、
 * {@code OPENAI_LIKE}（OpenAI 风格兼容接口）、{@code DEEPSEEK_OFFICIAL}
 * （DeepSeek 官方，OpenAI 兼容格式）。注册端点时 {@code apiKind} 指定其一，
 * 路由到对应的协议翻译实现。</p>
 * <p>标识以注册表形式持有：内置 5 种常量在类加载时注册；外部厂商可经
 * {@link #register(String)} 注册自定义标识，无需修改本类即可通过 SPI 接入新协议。
 * {@link #valueOf(String)} 解析注册表中的标识，未注册时抛 {@link IllegalArgumentException}。</p>
 */
public final class ApiSchema {

    private static final Map<String, ApiSchema> REGISTRY = new ConcurrentHashMap<>();

    private final String name;

    private ApiSchema(String name) {
        this.name = name;
    }

    public static final ApiSchema OPENAI_OFFICIAL = register("OPENAI_OFFICIAL");
    public static final ApiSchema ANTHROPIC_OFFICIAL = register("ANTHROPIC_OFFICIAL");
    public static final ApiSchema GEMINI_OFFICIAL = register("GEMINI_OFFICIAL");
    public static final ApiSchema OPENAI_LIKE = register("OPENAI_LIKE");
    public static final ApiSchema DEEPSEEK_OFFICIAL = register("DEEPSEEK_OFFICIAL");

    /** 注册（或取回已注册）一个协议标识；名称不能为空。 */
    public static ApiSchema register(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("apiSchema 名称不能为空");
        }
        return REGISTRY.computeIfAbsent(name, ApiSchema::new);
    }

    /** 解析已注册的协议标识；未注册时抛 {@link IllegalArgumentException}。 */
    public static ApiSchema valueOf(String name) {
        ApiSchema schema = REGISTRY.get(name);
        if (schema == null) {
            throw new IllegalArgumentException("未知 apiSchema: " + name);
        }
        return schema;
    }

    /** 标识名（与旧 enum {@code name()} 语义一致）。 */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
