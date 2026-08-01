package com.flora.ai.api;

import java.util.List;

/**
 * 对话请求：模型 + 消息 + 思考配置 + 采样配置。
 * <p>不可变对象，通过 {@link #builder()} 构建。</p>
 */
public record ChatRequest(ModelSpec model, List<Message> messages,
                          ThinkingConfig thinking, SamplingConfig sampling) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ModelSpec model;
        private List<Message> messages = List.of();
        private ThinkingConfig thinking = ThinkingConfig.auto();
        private SamplingConfig sampling = SamplingConfig.DEFAULT;

        public Builder model(ModelSpec model) {
            this.model = model;
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

        public Builder thinking(ThinkingConfig thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder sampling(SamplingConfig sampling) {
            this.sampling = sampling;
            return this;
        }

        public ChatRequest build() {
            if (model == null) {
                throw new IllegalStateException("ChatRequest 必须指定 model");
            }
            return new ChatRequest(model, messages, thinking, sampling);
        }
    }
}
