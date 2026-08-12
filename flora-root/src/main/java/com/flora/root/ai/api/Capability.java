package com.flora.root.ai.api;

/**
 * 客户端能力：跨厂商一致的能力集合，由 client 在构造后暴露。
 * <p>调用方通过 {@link ApiClient#capabilities()} 查询某个 client 支持哪些能力，
 * 决定是否发送思考/JSON/多模态/工具等请求特征。能力由 client 自身声明，
 * 不依赖端点配置。新能力枚举值即新能力（向前兼容）。</p>
 */
public enum Capability {
    /** 支持推理思考。 */
    THINKING,
    /** 支持 JSON 模式/结构化输出。 */
    JSON_MODE,
    /** 支持多模态输入（图片/音频等）。 */
    MULTIMODAL,
    /** 支持流式输出。 */
    STREAMING,
    /** 支持工具/函数调用。 */
    TOOL_USE
}
