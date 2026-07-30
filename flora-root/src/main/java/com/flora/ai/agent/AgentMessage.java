package com.flora.ai.agent;

/** Agent 间消息。 */
public record AgentMessage(AgentId from, AgentId to, String subject, String body, long timestamp) {

    public AgentMessage(AgentId from, AgentId to, String subject, String body) {
        this(from, to, subject, body, System.currentTimeMillis());
    }
}
