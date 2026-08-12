package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.tag.ThreadFragile;
import com.flora.root.ai.api.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文：一个对话主体的完整状态（真相源）。
 * <p>持有有序 {@link Message} 列表（含用户/助手/工具结果/多模态输入），供投影器
 * {@link ChatProjector} 折叠为模型可见的请求。投影（{@code assemble}）是只读操作，
 * 不修改本状态；本类仅通过 {@link #append}/{@link #autoCompact} 显式变更。
 * {@code autoCompact} 把最旧历史折叠为摘要消息，是一种原地压缩，非投影副作用。</p>
 * <p>不可直接跨线程共享：同一 {@code Context} 建议单线程使用（多 agent 各自持有实例）。</p>
 */
@ThreadFragile("内部可变消息列表，非线程安全；同一实例建议单线程使用")
public final class Context {

    private final List<Message> messages = new ArrayList<>();

    /** 追加一条消息（用户/助手/工具结果均可）。 */
    public Context append(Message message) {
        messages.add(message);
        return this;
    }

    /** 追加用户纯文本消息。 */
    public Context appendUser(String text) {
        return append(Message.of(Message.Role.USER, text));
    }

    /** 追加用户多模态消息（text/image/audio/file 块）。 */
    public Context appendUser(List<ContentBlock> blocks) {
        return append(new Message(Message.Role.USER, List.copyOf(blocks),
                List.of(), null, null, false));
    }

    /** 追加助手纯文本消息。 */
    public Context appendAssistant(String text) {
        return append(Message.of(Message.Role.ASSISTANT, text));
    }

    /** 追加助手消息（含工具调用）。 */
    public Context appendAssistantWithCalls(List<ToolCall> calls, String text) {
        return append(Message.assistantWithCalls(calls, text));
    }

    /** 追加工具结果消息（一条）。 */
    public Context appendToolResult(String toolCallId, String result, boolean isError) {
        return append(Message.toolResult(toolCallId, result, isError));
    }

    /** 追加多条工具结果消息（并行调用回执）。 */
    public Context appendAll(List<Message> extra) {
        messages.addAll(extra);
        return this;
    }

    /**
     * 自动压缩：当历史 token 占用超过预算阈值时，把最旧的若干条压缩为一条摘要消息。
     * <p>实现"层叠式上下文"：保留最近 {@code retentionCount} 条完整历史，
     * 更旧的部分交给 {@link Compactor} 折叠成摘要并替换原文，腾出窗口空间。
     * 摘要消息仍是一条普通消息，后续可再次被压缩（摘要的摘要 → 远古记忆层）。
     * 历史未超阈值时不动作（返回 false）。</p>
     *
     * @param compactor       压缩器（本地规则或模型摘要）
     * @param budget          token 预算
     * @param retentionCount  保留的最近完整消息条数（不参与压缩）
     * @return 是否发生了压缩
     */
    public boolean autoCompact(Compactor compactor, TokenBudget budget, int retentionCount) {
        return autoCompact(compactor, budget, retentionCount, m -> true);
    }

    /**
     * 自动压缩（带类型过滤）：保留最近 {@code retentionCount} 条完整；
     * 更旧部分按 {@code includeInSummary} 筛选——命中者交给 {@link Compactor} 折叠进摘要，
     * 未命中者<b>直接抛弃</b>（不进摘要、也不保留）。
     * <p>典型用法：只压缩 USER/ASSISTANT 对话、抛弃 TOOL 工具回执（酒馆类场景），
     * 或只压缩文本块、抛弃历史图片以节省多模态预算。
     * <b>注意</b>：抛弃 TOOL 消息会使"工具调用仍在、结果缺失"的语义断裂，
     * 由场景层自行权衡；编排层只提供能力。</p>
     *
     * @param compactor        压缩器
     * @param budget           token 预算
     * @param retentionCount   保留的最近完整消息条数（不参与筛选）
     * @param includeInSummary 更旧部分中参与压缩的谓词；false 的消息被直接抛弃
     * @return 是否发生了压缩（只要发生了筛选/折叠即返回 true）
     */
    public boolean autoCompact(Compactor compactor, TokenBudget budget, int retentionCount,
                               java.util.function.Predicate<Message> includeInSummary) {
        int threshold = (int) (budget.maxTokens() * 0.8);
        if (TokenBudget.estimateAll(messages) <= threshold) {
            return false; // 未满，不压缩
        }
        if (retentionCount < 0) {
            throw new IllegalArgumentException("retentionCount 不能为负: " + retentionCount);
        }
        if (includeInSummary == null) {
            throw new IllegalArgumentException("includeInSummary 不能为 null");
        }
        int compactCount = messages.size() - Math.min(retentionCount, messages.size());
        if (compactCount <= 0) {
            return false; // 全部都是最近消息，无可压缩
        }
        List<Message> old = new ArrayList<>();
        for (int i = 0; i < compactCount; i++) {
            if (includeInSummary.test(messages.get(i))) {
                old.add(messages.get(i));
            }
        }
        List<Message> recent = new ArrayList<>(messages.subList(compactCount, messages.size()));
        messages.clear();
        if (!old.isEmpty()) {
            messages.add(compactor.compact(old));
        }
        messages.addAll(recent);
        return true;
    }

    /** 只读视图：全部历史消息（按时间序）。 */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** 历史消息数。 */
    public int size() {
        return messages.size();
    }

    /** 是否为空上下文。 */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
}
