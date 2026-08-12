package com.flora.root.ai.api;

/**
 * 模型 API 类型：决定使用哪家协议实现翻译请求。
 * <p>协议分类——{@code OPENAI_OFFICIAL}（OpenAI 官方）、{@code ANTHROPIC_OFFICIAL}
 * （Anthropic 官方）、{@code GEMINI_OFFICIAL}（Gemini 官方）、
 * {@code OPENAI_LIKE}（OpenAI 风格兼容接口）、{@code DEEPSEEK_OFFICIAL}
 * （DeepSeek 官方，OpenAI 兼容格式）。注册端点时 {@code apiKind} 指定其一，
 * 路由到对应的协议翻译实现。</p>
 */
public enum ApiSchema {
    OPENAI_OFFICIAL,
    ANTHROPIC_OFFICIAL,
    GEMINI_OFFICIAL,
    OPENAI_LIKE,
    DEEPSEEK_OFFICIAL
}
