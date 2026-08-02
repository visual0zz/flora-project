package com.flora.ai.api;

import java.util.Set;

/**
 * AI 客户端基接口：所有能力 client 的公共契约。
 * <p>承载能力查询（{@link #capabilities()}）——每个 client 在构造后声明自己支持的
 * 能力集合，调用方据此决定请求特征（是否携带思考/JSON/多模态/工具等）。
 * 能力由实现类自身声明，不依赖端点配置。</p>
 */
public interface ApiClient {

    /** 本 client 支持的能力集合。 */
    Set<Capability> capabilities();
}
