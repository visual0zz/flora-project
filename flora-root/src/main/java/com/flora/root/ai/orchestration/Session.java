package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.ChatClient;
import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ChatResponse;
import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.tag.ThreadFragile;

import java.util.List;

/**
 * 会话：编排层面向场景层的单步原语。
 * <p>一次 {@link #turn} 完成：追加输入 → 自动压缩（可选）→ 投影为请求 →
 * 调用模型 → 把响应写回 {@link Context}。不内置循环——工具执行、退出条件、
 * 多 agent 调度均由场景层组合本类驱动（见 {@link #executeTools}）。</p>
 */
@ThreadFragile("持有并修改可变 Context，非线程安全；同一 Session 建议单线程使用")
public final class Session {

    /** 默认保留的最近完整消息条数（自动压缩时不受影响）。 */
    public static final int DEFAULT_RETENTION = 20;

    private final Context context;
    private final ChatProjector projector;
    private final ChatClient client;
    private final ToolRegistry tools;
    private final Compactor compactor;
    private final int retentionCount;

    /** 无自动压缩的会话。 */
    public Session(Context context, ChatProjector projector, ChatClient client, ToolRegistry tools) {
        this(context, projector, client, tools, null, DEFAULT_RETENTION);
    }

    /** 带自动压缩的会话：每次 turn 前若预算超阈值则压缩最旧历史。 */
    public Session(Context context, ChatProjector projector, ChatClient client,
                   ToolRegistry tools, Compactor compactor, int retentionCount) {
        this.context = context;
        this.projector = projector;
        this.client = client;
        this.tools = tools;
        this.compactor = compactor;
        this.retentionCount = retentionCount;
    }

    /** 当前上下文（引用，便于场景层检查历史）。 */
    public Context context() {
        return context;
    }

    /** 追加用户纯文本输入并完成一轮对话。 */
    public ChatResponse turn(String userText) {
        return turn(List.of(new ContentBlock.Text(userText)));
    }

    /** 追加用户多模态输入（text/image/audio/file 块）并完成一轮对话。 */
    public ChatResponse turn(List<ContentBlock> input) {
        context.append(new Message(Message.Role.USER, List.copyOf(input),
                List.of(), null, null, false));
        // 投影前自动压缩：历史超预算阈值则折叠最旧部分，防止请求超窗
        if (compactor != null) {
            context.autoCompact(compactor, projector.budget(), retentionCount);
        }
        ChatRequest request = projector.assemble(context);
        ChatResponse response = client.chat(request);
        recordAssistant(response);
        return response;
    }

    /**
     * 执行响应中的工具调用并把结果回传上下文。
     * <p>供场景层工具循环使用：模型返回 toolCalls → 本方法执行全部工具 →
     * 回执消息写入上下文 → 场景层可再次 {@link #turn} 让模型继续。</p>
     */
    public boolean executeTools(ChatResponse response) {
        if (tools == null || !response.hasToolCalls()) {
            return false;
        }
        context.appendAll(tools.executeAll(response.toolCalls()));
        return true;
    }

    /** 把模型响应记录为助手消息（含工具调用信息，供回传/续聊）。 */
    private void recordAssistant(ChatResponse response) {
        if (response.hasToolCalls()) {
            context.append(Message.assistantWithCalls(response.toolCalls(), response.text()));
        } else if (response.text() != null && !response.text().isBlank()) {
            context.appendAssistant(response.text());
        }
    }
}
