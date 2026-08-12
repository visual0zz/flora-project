package com.flora.root.ai.api;

/**
 * 客户端 IO 模式：每种模式对应一个可实例化的单能力 client 接口。
 * <p>与 {@link Capability} 区分：Capability 是 client 暴露的能力集合
 * （思考/JSON/多模态/工具，经 {@link ApiClient#capabilities()} 查询），
 * IOMode 是可构造的 client 模式（注册时按此构造一批独立 client 对象）。
 * 当前有对应 client 接口的模式为 {@link #CHAT}/{@link #STREAM}/{@link #JSON}；
 * {@link #EMBEDDING}/{@link #IMAGE} 为预留模式，尚无对应 client 接口，
 * 注册声明时需 provider 显式支持且上层自行消费。</p>
 */
public enum IOMode {
    /** 普通对话（一次性文本输出）。 */
    CHAT,
    /** 流式对话（增量文本输出，与普通 chat 是两种独立 client）。 */
    STREAM,
    /** JSON 结构化输出（与流式无关）。 */
    JSON,
    /** 向量嵌入（预留，暂无 client 接口）。 */
    EMBEDDING,
    /** 图像生成（预留，暂无 client 接口）。 */
    IMAGE
}
