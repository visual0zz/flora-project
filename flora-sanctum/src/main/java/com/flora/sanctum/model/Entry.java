package com.flora.sanctum.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 单条密码凭据条目。
 */
public class Entry {

    private String id;
    private String title;
    private String username;
    private String password;
    private String url;
    private String category;
    private String note;
    private long createdAt;
    private long updatedAt;

    public Entry() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public Entry(String title, String username, String password, String url, String category, String note) {
        this();
        this.title = title;
        this.username = username;
        this.password = password;
        this.url = url;
        this.category = category;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** 标记为已更新，刷新时间戳。 */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entry entry)) return false;
        return Objects.equals(id, entry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // -- JSON 序列化辅助 --

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("username", username);
        map.put("password", password);
        map.put("url", url);
        map.put("category", category);
        map.put("note", note);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Entry fromMap(Map<String, Object> map) {
        Entry entry = new Entry();
        entry.id = (String) map.getOrDefault("id", UUID.randomUUID().toString());
        entry.title = (String) map.get("title");
        entry.username = (String) map.get("username");
        entry.password = (String) map.get("password");
        entry.url = (String) map.get("url");
        entry.category = (String) map.get("category");
        entry.note = (String) map.get("note");
        entry.createdAt = toLong(map.get("createdAt"));
        entry.updatedAt = toLong(map.get("updatedAt"));
        return entry;
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }
}
