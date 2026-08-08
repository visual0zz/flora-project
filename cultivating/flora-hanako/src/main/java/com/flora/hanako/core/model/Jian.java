package com.flora.hanako.core.model;

import java.time.Instant;

/**
 * 笺（Jian）：书桌上的便签，Agent 会主动读取并执行。
 * <p>复刻 openhanako 书桌的「笺」概念——用户与 Agent 之间异步协作的轻量指令/笔记。</p>
 */
public final class Jian {

    private String id;
    private String agentId;
    private String content;
    private boolean done;
    private long createdAt;
    private long updatedAt;

    public Jian() {
        long now = Instant.now().toEpochMilli();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Jian(String id, String agentId, String content) {
        this();
        this.id = id;
        this.agentId = agentId;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
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
