/**
 * AI 统一接口接入口。
 * <p>能力接口（{@code ChatClient}/{@code StreamingClient}/{@code JsonClient}/
 * {@code MultimodalClient}/{@code ToolClient}）与请求/响应数据模型。实现类按需
 * 实现能力接口，调用方通过 {@code instanceof} 发现能力。</p>
 */
package com.flora.ai.api;
