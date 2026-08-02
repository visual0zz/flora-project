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
 * <p>注入器通过本类追加系统提示（{@link #system}）与注入消息（{@link #inject}），
 * 投影器在全部注入完成后与历史上下文合并、裁剪，最终构建 {@link ChatRequest}。</p>
 */
public final class ProjectionBuilder {

    private final StringBuilder system = new StringBuilder();
    private final List<Message> injected = new ArrayList<>();

    /** 追加系统提示文本（多次追加合并）。 */
    public ProjectionBuilder system(String text) {
        if (text != null && !text.isBlank()) {
            if (!system.isEmpty()) {
                system.append("\n\n");
            }
            system.append(text);
        }
        return this;
    }

    /** 追加注入消息（记忆/RAG 结果等），置于历史之前。 */
    public ProjectionBuilder inject(Message message) {
        injected.add(message);
        return this;
    }

    /** 追加多条注入消息。 */
    public ProjectionBuilder injectAll(List<Message> messages) {
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
     * 构建请求：注入消息 + 历史消息，与工具声明、推理配置合并。
     *
     * @param history   上下文历史消息
     * @param tools     工具声明（可为空）
     * @param config    推理配置（可为 null，走默认）
     */
    ChatRequest build(List<Message> history, List<ToolSpec> tools, InferenceConfig config) {
        List<Message> all = new ArrayList<>(injected.size() + history.size());
        all.addAll(injected);
        all.addAll(history);
        ChatRequest.Builder b = ChatRequest.builder()
                .messages(all)
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
