package com.flora.ai.chat;

/** LLM 供应商连接配置。纯数据，不负责加载。 */
public record LlmConfig(
    String provider,
    String apiUrl,
    String apiKey,
    String model
) {}
