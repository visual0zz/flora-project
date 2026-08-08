package com.flora.hanako.core.model;

/**
 * 模型配置：对话模型 / 小工具模型 / 大工具模型三种角色之一。
 * <p>openhanako 首次运行引导中选择三个模型：对话模型（主对话）、小工具模型（摘要等轻量任务）、
 * 大工具模型（记忆编译和深度分析）。{@code endpointId} 指向 AiApi 展开后的端点。</p>
 */
public final class ModelConfig {

    /** 模型角色：对话 / 轻量工具 / 重型工具。 */
    public enum Role {
        CHAT, UTILITY, HEAVY
    }

    private Role role;
    private String endpointId;
    private String displayName;

    public ModelConfig() {
    }

    public ModelConfig(Role role, String endpointId, String displayName) {
        this.role = role;
        this.endpointId = endpointId;
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
