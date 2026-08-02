/**
 * AI 统一接口接入口。
 * <p>能力接口（{@code ApiClient}/{@code ChatClient}/{@code StreamingClient}/
 * {@code JsonClient}）与请求/响应数据模型。实现类按需实现能力接口，
 * 调用方通过 {@code instanceof} 发现能力，并经 {@code ApiClient.capabilities()}
 * 查询具体能力集合。</p>
 */
package com.flora.ai.api;
