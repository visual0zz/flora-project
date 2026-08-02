package com.flora.ai.api;

import java.util.List;

/**
 * 对话请求：消息 + 工具 + 推理配置。
 * <p>不绑定模型——具体模型由路由层从注册端点中选择，客户端持有自己的模型标识。
 * 不可变对象，通过 {@link #builder()} 构建。</p>
 */
public record ChatRequest(List<Message> messages, List<ToolSpec> tools,
                          InferenceConfig config) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Message> messages = List.of();
        private List<ToolSpec> tools = List.of();
        private InferenceConfig config = InferenceConfig.DEFAULT;

        public Builder messages(List<Message> messages) {
            this.messages = List.copyOf(messages);
            return this;
        }

        public Builder message(Message message) {
            this.messages = List.of(message);
            return this;
        }

        public Builder tools(List<ToolSpec> tools) {
            this.tools = List.copyOf(tools);
            return this;
        }

        public Builder tool(ToolSpec tool) {
            this.tools = List.of(tool);
            return this;
        }

        public Builder config(InferenceConfig config) {
            this.config = config;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(messages, tools, config);
        }
    }
}
