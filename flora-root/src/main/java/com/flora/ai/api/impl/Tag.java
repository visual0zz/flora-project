package com.flora.ai.api.impl;

/**
 * 系统统一能力标签：跨厂商一致，供上层路由与能力判断。
 * <p>区别于 {@code RegisteredModel.spec()}（技术规格，定制化自由 key）——
 * 标签是统一的、可枚举的，spec 是定制化的。新能力枚举值即新能力（向前兼容）。</p>
 */
public enum Tag {
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
