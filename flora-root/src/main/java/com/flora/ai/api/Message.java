package com.flora.ai.api;

import java.util.List;

/**
 * 消息：角色 + 内容块列表 + 工具调用信息。
 * <p>角色 {@code TOOL} 的消息用 {@code toolCallId} 回执对应调用；角色 {@code ASSISTANT}
 * 的消息可含 {@code toolCalls}（模型发起调用）；{@code name} 标记调用名（TOOL 消息用）。</p>
 */
public record Message(Role role, List<ContentBlock> content,
                      List<ToolCall> toolCalls, String toolCallId, String name) {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /** 便捷构造：纯文本消息。 */
    public static Message of(Role role, String text) {
        return new Message(role, List.of(new ContentBlock.Text(text)), List.of(), null, null);
    }

    /** 便捷构造：单角色纯文本列表。 */
    public static List<Message> of(Role role, String... texts) {
        return java.util.Arrays.stream(texts).map(t -> of(role, t)).toList();
    }

    /** 构造 TOOL 角色回执消息。 */
    public static Message toolResult(String toolCallId, String result) {
        return new Message(Role.TOOL, List.of(new ContentBlock.Text(result)), List.of(),
                toolCallId, null);
    }

    /** 构造 ASSISTANT 角色消息：含工具调用（可选附带文本）。 */
    public static Message assistantWithCalls(List<ToolCall> calls, String text) {
        List<ContentBlock> content = text == null
                ? List.of() : List.of(new ContentBlock.Text(text));
        return new Message(Role.ASSISTANT, content, List.copyOf(calls), null, null);
    }
}
