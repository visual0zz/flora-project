package com.flora.hanako.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent：拥有独立记忆、人格（identity）与心识（ishiki）的私人助理单元。
 * <p>复刻 openhanako「Agent 就是文件夹」的隔离哲学：每个 Agent 有独立的
 * 人格模板、模型配置与定时任务。{@code modelId} 指向已注册的 provider 端点。</p>
 */
public final class Agent {

    private String id;
    private String name;
    private String identity;
    private String ishiki;
    private String modelId;
    private List<String> tags = new ArrayList<>();
    private boolean isDefault;

    public Agent() {
    }

    public Agent(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getIshiki() {
        return ishiki;
    }

    public void setIshiki(String ishiki) {
        this.ishiki = ishiki;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
