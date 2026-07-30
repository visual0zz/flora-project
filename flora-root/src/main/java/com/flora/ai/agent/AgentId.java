package com.flora.ai.agent;

/** Agent 标识。 */
public record AgentId(String id, String name) {

    public static AgentId of(String name) {
        return new AgentId(name + "@" + Long.toHexString(System.nanoTime()), name);
    }
}
