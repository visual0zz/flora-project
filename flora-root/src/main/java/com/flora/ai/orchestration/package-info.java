/**
 * AI 编排层：对话上下文管理 + 请求投影 + 工具接入。
 * <p>面向多种应用场景（单 agent 任务 / 长故事 / 多 agent 游戏）提供统一骨架：
 * {@link com.flora.ai.orchestration.Context} 持有对话真相，
 * {@link com.flora.ai.orchestration.ChatProjector} 通过注入器链把上下文投影为
 * {@link com.flora.ai.api.ChatRequest}（含预算裁剪与多模态），
 * {@link com.flora.ai.orchestration.ToolRegistry} 统一工具声明与执行，
 * {@link com.flora.ai.orchestration.Session} 提供单步 turn 原语供场景层组合循环。</p>
 */
package com.flora.ai.orchestration;
