package com.flora.ai.api;

/**
 * Client 规格：选中的端点 + 要使用的能力。
 * <p>{@link com.flora.ai.api.spi.Router} 根据任务上下文（含能力信息）返回此规格，
 * 由 {@code AiApi} 据此构造对应的单能力 client。</p>
 */
public record ClientSpec(Endpoint endpoint, Capability capability) {

    public static ClientSpec of(Endpoint endpoint, Capability capability) {
        return new ClientSpec(endpoint, capability);
    }
}
