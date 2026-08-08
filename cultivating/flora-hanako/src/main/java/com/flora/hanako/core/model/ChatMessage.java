package com.flora.hanako.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 单条聊天消息：角色 + 文本 + 工具调用/回执 + 时间戳。
 * <p>复刻 openhanako 会话流中的一条消息；role 与 {@code com.flora.ai.api.Message.Role} 对齐。</p>
 */
public final class ChatMessage {

    public enum Role {
        USER, ASSISTANT, TOOL, SYSTEM
    }

    private String id;
    private Role role;
    private String text;
    private List<Map<String, Object>> toolCalls;
    private List<Map<String, Object>> toolResults;
    private long timestamp;

    public ChatMessage() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    public ChatMessage(Role role, String text) {
        this();
        this.role = role;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Map<String, Object>> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<Map<String, Object>> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<Map<String, Object>> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<Map<String, Object>> toolResults) {
        this.toolResults = toolResults;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
