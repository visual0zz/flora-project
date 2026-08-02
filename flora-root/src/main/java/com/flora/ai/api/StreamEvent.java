package com.flora.ai.api;

/**
 * 流式事件：多态，覆盖真实厂商流式协议的事件类型。
 * <p>调用方通过 {@code switch} 模式匹配处理各类型：
 * {@link Text}（文本增量）、{@link Thinking}（思考增量）、{@link ToolCallCompleted}
 * （工具调用完整攒齐）、{@link Error}（流式错误）、{@link Done}（流结束，含用量）。</p>
 */
public sealed interface StreamEvent {

    /** 文本增量。 */
    record Text(String delta) implements StreamEvent {
    }

    /** 思考内容增量。 */
    record Thinking(String delta) implements StreamEvent {
    }

    /**
     * 工具调用完整攒齐（非碎片推送）。
     * <p>各家客户端负责在协议层完成信号出现时发出：
     * Anthropic 在 {@code content_block_stop}、Gemini 在 {@code functionCall} part 到达时
     * 逐块发出；OpenAI 的 {@code tool_calls} 是分片传输且无 per-index 完成标记，故客户端
     * 按 {@code index} 聚合碎片，到 {@code finish_reason="tool_calls"}（或流结束）整批发出。
     * {@code call.arguments()} 为解析后的参数 Map，{@code rawArguments} 为原始 JSON 串。</p>
     */
    record ToolCallCompleted(ToolCall call, String rawArguments) implements StreamEvent {
    }

    /** 流式错误。 */
    record Error(String message) implements StreamEvent {
    }

    /** 流结束：含 finishReason 与 token 用量。 */
    record Done(String finishReason, TokenUsage usage) implements StreamEvent {
    }
}
