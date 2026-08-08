package com.flora.hanako.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆事实：文本 + 标签 + 时间，入库后即一条可检索记忆。
 * <p>复刻 openhanako {@code lib/memory/fact-store.js} 的「标签化事实存储」；
 * 检索按多标签命中数降序排序（见 {@code TaggedFactStore}）。</p>
 */
public final class MemoryFact {

    private String id;
    private String text;
    private List<String> tags = new ArrayList<>();
    private long time;

    public MemoryFact() {
        this.time = Instant.now().toEpochMilli();
    }

    public MemoryFact(String id, String text, List<String> tags) {
        this();
        this.id = id;
        this.text = text;
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
