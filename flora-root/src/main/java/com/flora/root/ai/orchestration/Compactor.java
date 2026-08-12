package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.Message;

import java.util.List;

/**
 * 压缩器：把一批旧对话消息压缩为一条摘要消息。
 * <p>供 {@link Context#autoCompact} 使用，实现"上下文快满时自动压缩"：
 * 将最旧的若干条历史折叠为一条摘要（替换原文，腾出窗口空间）。
 * 实现可插拔——本地规则（快速、免费）或调用模型生成摘要（质量高）。</p>
 *
 * <pre>{@code
 * // 本地规则实现：拼接要点（快）
 * Compactor local = history -> Message.of(Message.Role.ASSISTANT,
 *     "【历史摘要】" + history.stream().map(Message::text).toList());
 *
 * // 模型实现：把旧历史喂给 ChatClient 要摘要
 * Compactor llm = history -> Message.of(Message.Role.ASSISTANT,
 *     summarizeClient.chat(ChatRequest.builder().messages(history).build()).text());
 * }</pre>
 */
@FunctionalInterface
public interface Compactor {

    /** 把一批旧消息压缩为一条摘要消息（通常是 ASSISTANT 或 SYSTEM 角色）。 */
    Message compact(List<Message> history);

    /**
     * 便捷构造：给定一个摘要生成函数，压缩结果包装为 ASSISTANT 摘要消息，
     * 文本前缀 {@code 【历史摘要】} 标记其来源。
     */
    static Compactor of(java.util.function.Function<List<Message>, String> summarizer) {
        return history -> Message.of(Message.Role.ASSISTANT,
                "【历史摘要】" + summarizer.apply(history));
    }
}
