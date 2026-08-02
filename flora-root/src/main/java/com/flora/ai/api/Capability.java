package com.flora.ai.api;

/**
 * 客户端能力：每种能力对应一个可实例化的单能力 client 接口。
 * <p>与 {@link Tag} 区分：Tag 是端点能力标签（供路由/判断），Capability 是
 * 可构造的 client 能力（注册时按此构造一批独立 client 对象）。
 * 当前有对应 client 接口的能力为 {@link #CHAT}/{@link #STREAM}/{@link #JSON}/
 * {@link #MULTIMODAL}；{@link #EMBEDDING}/{@link #IMAGE} 为预留能力，
 * 尚无对应 client 接口，注册声明时需 provider 显式支持且上层自行消费。</p>
 */
public enum Capability {
    /** 普通对话（一次性文本输出）。 */
    CHAT,
    /** 流式对话（增量文本输出，与普通 chat 是两种独立 client）。 */
    STREAM,
    /** JSON 结构化输出（与流式无关）。 */
    JSON,
    /** 多模态输入（图片/音频等，与流式无关）。 */
    MULTIMODAL,
    /** 向量嵌入（预留，暂无 client 接口）。 */
    EMBEDDING,
    /** 图像生成（预留，暂无 client 接口）。 */
    IMAGE
}
