package com.flora.ai.api;

import java.util.List;

/**
 * 对话请求：系统提示 + 消息 + 工具 + 推理配置。
 * <p>{@link #system()} 为顶层系统提示（与 Anthropic/Gemini 的原生顶层 system 对齐）；
 * 不绑定模型——具体模型由路由层从注册端点中选择，客户端持有自己的模型标识。
 * 不可变对象，通过 {@link #builder()} 构建。</p>
 */
public record ChatRequest(String system, List<Message> messages, List<ToolSpec> tools,
                          InferenceConfig config) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String system = null;
        private List<Message> messages = List.of();
        private List<ToolSpec> tools = List.of();
        private InferenceConfig config = InferenceConfig.DEFAULT;

        public Builder system(String system) {
            this.system = system;
            return this;
        }

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
            return new ChatRequest(system, messages, tools, config);
        }
    }
}
