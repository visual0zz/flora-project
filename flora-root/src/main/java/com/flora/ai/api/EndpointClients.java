package com.flora.ai.api;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个端点的 client 集合：持有该端点按能力构造的一批单能力 client。
 * <p>由 {@code AiApi} 在注册/路由后按 endpoint 声明的 capabilities 构造。
 * 通过类型化方法（{@link #chat()}/{@link #stream()}/{@link #json()}）或泛型
 * {@link #as(Class)} 取具体能力 client；缺失能力抛 {@link IllegalStateException}。</p>
 */
public final class EndpointClients {

    private final Map<Class<?>, Object> clients;

    private EndpointClients(Map<Class<?>, Object> clients) {
        this.clients = Map.copyOf(clients);
    }

    /** 构造容器（内部使用）。 */
    public static EndpointClients of(Map<Class<?>, Object> clients) {
        return new EndpointClients(clients);
    }

    /** 对话客户端。 */
    public ChatClient chat() {
        return as(ChatClient.class);
    }

    /** 流式对话客户端。 */
    public StreamingClient stream() {
        return as(StreamingClient.class);
    }

    /** JSON 结构化输出客户端。 */
    public JsonClient json() {
        return as(JsonClient.class);
    }

    /** 多模态客户端。 */
    public MultimodalClient multimodal() {
        return as(MultimodalClient.class);
    }

    /** 按能力接口类型取 client；缺失抛异常。 */
    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> type) {
        Object client = clients.get(type);
        if (client == null) {
            throw new IllegalStateException("该端点不支持能力: " + type.getSimpleName());
        }
        return (T) client;
    }

    /** 是否包含指定能力。 */
    public boolean supports(Class<?> type) {
        return clients.containsKey(type);
    }

    /** 所有已构造的 client（用于遍历/调试）。 */
    public Map<Class<?>, Object> all() {
        return new HashMap<>(clients);
    }
}
