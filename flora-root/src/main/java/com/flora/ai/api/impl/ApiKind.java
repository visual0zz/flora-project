package com.flora.ai.api.impl;

/**
 * 模型 API 类型：协议分类。
 * <p>决定使用哪家协议实现翻译请求（OpenAI 官方 / Anthropic 官方 / Gemini 官方 /
 * OpenAI 风格兼容接口 / DeepSeek 官方）。{@code OPENAI_COMPATIBLE} 与
 * {@code DEEPSEEK_OFFICIAL} 复用 OpenAI 协议格式（各自独立 provider）。</p>
 */
public enum ApiKind {
    OPENAI_OFFICIAL,
    ANTHROPIC_OFFICIAL,
    GEMINI_OFFICIAL,
    OPENAI_COMPATIBLE,
    DEEPSEEK_OFFICIAL
}
