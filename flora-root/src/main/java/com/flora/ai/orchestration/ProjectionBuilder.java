package com.flora.ai.orchestration;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.InferenceConfig;
import com.flora.ai.api.Message;
import com.flora.ai.api.ToolSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 投影构建器：注入器与最终请求之间的缓冲。
 * <p>注入器通过本类追加系统提示（{@link #system}）与注入消息（{@link #inject}）。
 * 投影器在全部注入完成后把注入消息与历史上下文合并、裁剪，最终调用
 * {@link #build} 构建 {@link ChatRequest}。</p>
 * <p>写方法（{@link #system}/{@link #inject}/{@link #injectAll}）是线程安全的，
 * 供异步注入器并行写入；构建完成后由投影器在单线程读取（{@link #systemText}/
 * {@link #injected}）。</p>
 */
public final class ProjectionBuilder {

    private final StringBuilder system = new StringBuilder();
    private final List<Message> injected = new ArrayList<>();

    /** 追加系统提示文本（多次追加合并；线程安全）。 */
    public synchronized ProjectionBuilder system(String text) {
        if (text != null && !text.isBlank()) {
            if (!system.isEmpty()) {
                system.append("\n\n");
            }
            system.append(text);
        }
        return this;
    }

    /** 追加注入消息（记忆/RAG 结果等），置于历史之前（线程安全）。 */
    public synchronized ProjectionBuilder inject(Message message) {
        injected.add(message);
        return this;
    }

    /** 追加多条注入消息（线程安全）。 */
    public synchronized ProjectionBuilder injectAll(List<Message> messages) {
        injected.addAll(messages);
        return this;
    }

    /** 当前系统提示（未设置时为空串）。 */
    public String systemText() {
        return system.toString();
    }

    /** 当前注入的消息列表。 */
    public List<Message> injected() {
        return List.copyOf(injected);
    }

    /**
     * 构建请求：用给定的消息序列（已合并注入消息与裁剪后的历史）组装。
     *
     * @param messages 最终消息序列（注入消息 + 裁剪后历史，由调用方准备）
     * @param tools    工具声明（可为空，进 {@code ChatRequest.tools()})
     * @param config   推理配置（为 null 时 ChatRequest 使用默认配置）
     */
    ChatRequest build(List<Message> messages, List<ToolSpec> tools, InferenceConfig config) {
        ChatRequest.Builder b = ChatRequest.builder()
                .messages(messages)
                .tools(tools);
        if (!system.isEmpty()) {
            b.system(system.toString());
        }
        if (config != null) {
            b.config(config);
        }
        return b.build();
    }
}
