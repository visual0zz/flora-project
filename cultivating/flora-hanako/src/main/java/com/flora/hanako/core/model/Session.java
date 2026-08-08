package com.flora.hanako.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话：属于某个 Agent，承载该 Agent 与用户的一段对话。
 * <p>openhanako 中 Agent 即文件夹、会话即文件；此处用 {@code agentId} 关联，消息存内存并可持久化。</p>
 */
public final class Session {

    private String id;
    private String agentId;
    private String title;
    private List<ChatMessage> messages = new ArrayList<>();
    private long createdAt;
    private long updatedAt;

    public Session() {
        long now = Instant.now().toEpochMilli();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Session(String id, String agentId, String title) {
        this();
        this.id = id;
        this.agentId = agentId;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
