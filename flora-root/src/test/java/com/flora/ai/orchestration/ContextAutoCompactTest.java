package com.flora.ai.orchestration;

import com.flora.ai.api.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Context#autoCompact} 与 {@link Compactor} 测试：层叠式压缩、保留最近、阈值不触发。
 */
class ContextAutoCompactTest {

    /** 简易压缩器：把历史拼接为一条摘要消息。 */
    private static final Compactor SUMMARIZER = Compactor.of(history -> {
        StringBuilder sb = new StringBuilder();
        for (Message m : history) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(m.text());
        }
        return sb.toString();
    });

    @Test
    void compactsOldestWhenOverThreshold() {
        Context ctx = new Context();
        // 每条消息约 10+ token（"msg-NN with padding text here ..."）
        for (int i = 0; i < 30; i++) {
            ctx.appendUser("msg-" + i + " with padding text here to occupy tokens");
        }
        assertTrue(ctx.autoCompact(SUMMARIZER, new TokenBudget(100), 5),
                "超阈值应触发压缩");
        // 压缩后：1 条摘要 + 最近 5 条
        assertEquals(6, ctx.size());
        List<Message> msgs = ctx.messages();
        assertEquals(Message.Role.ASSISTANT, msgs.get(0).role(), "首条应为摘要消息");
        assertTrue(msgs.get(0).text().contains("msg-0"), "摘要应包含最旧消息");
        assertTrue(msgs.get(msgs.size() - 1).text().contains("msg-29"), "最近消息应完整保留");
    }

    @Test
    void noCompactionWhenUnderThreshold() {
        Context ctx = new Context().appendUser("hi").appendAssistant("hello");
        assertFalse(ctx.autoCompact(SUMMARIZER, new TokenBudget(100000), 5),
                "未超阈值不应压缩");
        assertEquals(2, ctx.size());
    }

    @Test
    void compactedSummaryCanBeCompactedAgain() {
        Context ctx = new Context();
        for (int i = 0; i < 40; i++) {
            ctx.appendUser("msg-" + i + " with padding text here to occupy tokens");
        }
        TokenBudget budget = new TokenBudget(60);
        assertTrue(ctx.autoCompact(SUMMARIZER, budget, 3));
        assertEquals(4, ctx.size());
        // 再次压缩：摘要消息也被折叠进更粗的摘要（层叠）
        assertTrue(ctx.autoCompact(SUMMARIZER, budget, 2));
        assertEquals(3, ctx.size());
        assertTrue(ctx.messages().get(0).text().contains("摘要") || ctx.messages().get(0).text().contains("msg-0"));
    }

    @Test
    void negativeRetentionThrows() {
        Context ctx = new Context().appendUser("hi");
        for (int i = 0; i < 30; i++) {
            ctx.appendUser("msg-" + i + " with padding text here to occupy tokens");
        }
        assertThrows(IllegalArgumentException.class,
                () -> ctx.autoCompact(SUMMARIZER, new TokenBudget(100), -1));
    }

    @Test
    void compactorOfBuildsSummaryMessage() {
        Message summary = SUMMARIZER.compact(List.of(
                Message.of(Message.Role.USER, "a"), Message.of(Message.Role.USER, "b")));
        assertEquals(Message.Role.ASSISTANT, summary.role());
        assertTrue(summary.text().contains("a"));
        assertTrue(summary.text().contains("b"));
    }

    @Test
    void filterDropsNonMatchingOldMessages() {
        Context ctx = new Context();
        // 14 对交错（USER + TOOL），随后 2 条 USER 作为最近保留区
        for (int i = 0; i < 14; i++) {
            ctx.appendUser("q-" + i + " with padding text here to occupy tokens");
            ctx.appendToolResult("t" + i, "result-" + i, false);
        }
        ctx.appendUser("q-14 with padding text here to occupy tokens");
        ctx.appendUser("q-15 with padding text here to occupy tokens");
        // 只压缩 USER，TOOL 回执直接抛弃
        assertTrue(ctx.autoCompact(SUMMARIZER, new TokenBudget(80), 2,
                m -> m.role() == Message.Role.USER));
        List<Message> msgs = ctx.messages();
        // 1 摘要 + 最近保留（最后 2 条 USER）
        assertEquals(3, msgs.size());
        assertEquals(Message.Role.ASSISTANT, msgs.get(0).role());
        assertFalse(msgs.get(0).text().contains("result-"), "摘要不应含被抛弃的 TOOL 内容");
        assertTrue(msgs.get(msgs.size() - 1).text().contains("q-15"), "最近消息应完整保留");
        // 全部消息中不应再有 TOOL 角色
        assertTrue(msgs.stream().noneMatch(m -> m.role() == Message.Role.TOOL),
                "被过滤的 TOOL 消息应被抛弃");
    }

    @Test
    void filterDroppingAllOldStillKeepsRecent() {
        Context ctx = new Context();
        for (int i = 0; i < 10; i++) {
            ctx.appendUser("q-" + i + " with padding text here to occupy tokens");
        }
        // 谓词恒 false：更旧部分全部抛弃、不产生摘要
        assertTrue(ctx.autoCompact(SUMMARIZER, new TokenBudget(60), 2, m -> false));
        assertEquals(2, ctx.size(), "旧消息全抛弃后只剩最近 2 条");
        assertEquals(Message.Role.USER, ctx.messages().get(0).role());
    }

    @Test
    void nullFilterThrows() {
        Context ctx = new Context();
        for (int i = 0; i < 10; i++) {
            ctx.appendUser("q-" + i + " with padding text here to occupy tokens");
        }
        assertThrows(IllegalArgumentException.class,
                () -> ctx.autoCompact(SUMMARIZER, new TokenBudget(60), 2, null));
    }
}
