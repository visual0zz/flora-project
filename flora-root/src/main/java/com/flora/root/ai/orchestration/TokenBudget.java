package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.tag.ThreadFragile;

import java.util.ArrayList;
import java.util.List;

/**
 * token 预算：估算一段上下文的 token 占用，并在超限时裁剪历史。
 * <p>估算器采用近似策略（文本按 4 字符≈1 token，图片/音频/文件按固定值），
 * 不追求精确计数——预算的目的是防止请求超窗，留出模型输出余量。</p>
 * <p>裁剪策略（{@link #trim}）：对调用方给定的消息序列<b>从后往前</b>保留，
 * 直到预算耗尽。投影器把注入内容（记忆/RAG）置于序列头部、历史置于尾部，
 * 因此注入内容优先保留，最旧的历史先被裁掉。</p>
 */
@ThreadFragile("无共享可变状态；若估算器注入的有状态实现需自行同步")
public final class TokenBudget {

    /** 近似估算：文本 4 字符≈1 token，多模态块按固定消耗。 */
    public static final int IMAGE_TOKENS = 1000;
    public static final int AUDIO_TOKENS = 1200;
    public static final int FILE_TOKENS = 1500;

    private final int maxTokens;

    public TokenBudget(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens 必须为正: " + maxTokens);
        }
        this.maxTokens = maxTokens;
    }

    /** 估算单条消息的 token 占用。 */
    public static int estimate(Message m) {
        int total = 0;
        if (m.content() != null) {
            for (ContentBlock b : m.content()) {
                total += estimateBlock(b);
            }
        }
        // 角色与结构开销
        return total + 4;
    }

    private static int estimateBlock(ContentBlock b) {
        return switch (b) {
            case ContentBlock.Text t -> t.text() == null ? 1 : (t.text().length() + 3) / 4;
            case ContentBlock.Image ignored -> IMAGE_TOKENS;
            case ContentBlock.Audio ignored -> AUDIO_TOKENS;
            case ContentBlock.File ignored -> FILE_TOKENS;
        };
    }

    /** 估算多条消息的总 token。 */
    public static int estimateAll(List<Message> messages) {
        int total = 0;
        for (Message m : messages) {
            total += estimate(m);
        }
        return total;
    }

    /**
     * 裁剪历史：从后往前保留直到预算耗尽。
     *
     * @return 保留的子列表（顺序不变）；全部放得下则原样返回
     */
    public List<Message> trim(List<Message> history) {
        if (estimateAll(history) <= maxTokens) {
            return history;
        }
        List<Message> kept = new ArrayList<>();
        int budget = maxTokens;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            int cost = estimate(m);
            if (cost > budget) {
                // 单条超预算：多模态大块场景下丢弃
                continue;
            }
            budget -= cost;
            kept.add(0, m);
        }
        return kept;
    }

    /** 最大 token 预算。 */
    public int maxTokens() {
        return maxTokens;
    }
}
