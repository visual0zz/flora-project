package com.flora.ai.api;

import java.util.List;

/**
 * 消息：角色 + 内容块列表 + 工具调用信息。
 * <p>系统提示不在此处表示，而是 {@link com.flora.ai.api.ChatRequest#system()} 顶层字段
 * （与 Anthropic/Gemini 的原生顶层 system 对齐，OpenAI 在协议层再合成 system 消息）。
 * 角色 {@code TOOL} 的消息用 {@code toolCallId} 回执对应调用；角色 {@code ASSISTANT}
 * 的消息可含 {@code toolCalls}（模型发起调用）；{@code name} 标记调用名（TOOL 消息用）。</p>
 */
public record Message(Role role, List<ContentBlock> content,
                      List<ToolCall> toolCalls, String toolCallId, String name,
                      boolean error) {

    /** 兼容构造：默认非错误。 */
    public Message(Role role, List<ContentBlock> content,
                   List<ToolCall> toolCalls, String toolCallId, String name) {
        this(role, content, toolCalls, toolCallId, name, false);
    }

    public enum Role {
        USER, ASSISTANT, TOOL
    }

    /** 便捷：拼接全部文本块的内容（忽略图片/音频/文件块）。 */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : content) {
            if (b instanceof ContentBlock.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    /** 便捷构造：纯文本消息。 */
    public static Message of(Role role, String text) {
        return new Message(role, List.of(new ContentBlock.Text(text)), List.of(), null, null, false);
    }

    /** 便捷构造：单角色纯文本列表。 */
    public static List<Message> of(Role role, String... texts) {
        return java.util.Arrays.stream(texts).map(t -> of(role, t)).toList();
    }

    /** 构造 TOOL 角色回执消息（文本）。 */
    public static Message toolResult(String toolCallId, String result) {
        return toolResult(toolCallId, List.of(new ContentBlock.Text(result)), false);
    }

    /** 构造 TOOL 角色回执消息（多模态：text/image 块列表）。 */
    public static Message toolResult(String toolCallId, List<ContentBlock> content) {
        return toolResult(toolCallId, content, false);
    }

    /** 构造 TOOL 角色回执消息（文本，标记执行失败）。 */
    public static Message toolResult(String toolCallId, String result, boolean isError) {
        return toolResult(toolCallId, List.of(new ContentBlock.Text(result)), isError);
    }

    /** 构造 TOOL 角色回执消息（多模态内容块，标记执行失败）。 */
    public static Message toolResult(String toolCallId, List<ContentBlock> content, boolean isError) {
        return new Message(Role.TOOL, List.copyOf(content), List.of(), toolCallId, null, isError);
    }

    /** 构造 ASSISTANT 角色消息：含工具调用（可选附带文本）。 */
    public static Message assistantWithCalls(List<ToolCall> calls, String text) {
        List<ContentBlock> content = text == null
                ? List.of() : List.of(new ContentBlock.Text(text));
        return new Message(Role.ASSISTANT, content, List.copyOf(calls), null, null, false);
    }
}
