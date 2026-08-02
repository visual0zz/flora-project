package com.flora.ai.api;

/**
 * 流式事件：多态，覆盖真实厂商流式协议的事件类型。
 * <p>调用方通过 {@code switch} 模式匹配处理各类型：
 * {@link Text}（文本增量）、{@link Thinking}（思考增量）、{@link ToolCallDelta}
 * （工具调用增量，分片推送）、{@link Error}（流式错误）、{@link Done}（流结束，含用量）。</p>
 */
public sealed interface StreamEvent {

    /** 文本增量。 */
    record Text(String delta) implements StreamEvent {
    }

    /** 思考内容增量。 */
    record Thinking(String delta) implements StreamEvent {
    }

    /**
     * 工具调用增量（分片）：承载单个工具调用的部分字段。
     * <p>流式 tool_calls 分片推送（id/name/arguments 各为独立 delta）。
     * {@code call.arguments()} 为已解析的 Map（若增量是 JSON 片段，存原始串于
     * {@code rawArguments}），由调用方决定拼接时机与最终解析。</p>
     */
    record ToolCallDelta(ToolCall call, String rawArguments) implements StreamEvent {
    }

    /** 流式错误。 */
    record Error(String message) implements StreamEvent {
    }

    /** 流结束：含 finishReason 与 token 用量。 */
    record Done(String finishReason, TokenUsage usage) implements StreamEvent {
    }
}
