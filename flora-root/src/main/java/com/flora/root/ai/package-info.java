/**
 * AI 统一接入包。
 * <p>提供统一的大模型 API 抽象：{@code com.flora.ai.api} 为统一接口接入口
 * （能力接口与请求/响应数据模型），{@code com.flora.ai.api.spi} 为 SPI 扩展与路由
 * （新厂商通过实现 {@code AiProvider} 接入，路由由 {@code Router} 负责），
 * {@code com.flora.ai.api.provider} 为厂商适配器，{@code com.flora.ai.api.impl}
 * 为内置轻量 HTTP + SSE 流式基础设施（仅 JDK，零依赖）。
 * 门面 {@code AiApi} 提供模型注册、目录与 client 创建，不参与路由分发。</p>
 */
package com.flora.root.ai;
