package com.flora.ai.api;

import java.util.Map;

/**
 * JSON 模式能力（可选）：请求结构化 JSON 输出。
 * <p>支持 JSON mode / structured output 的客户端实现此接口。
 * 返回已解析的 JSON 对象（Map/List 嵌套），由实现方负责与厂商的
 * {@code response_format} / JSON schema 协议对齐。</p>
 */
public interface JsonClient {

    /** 请求 JSON 结构化输出，返回解析后的 JSON 对象。 */
    Map<String, Object> chatJson(ChatRequest request);
}
