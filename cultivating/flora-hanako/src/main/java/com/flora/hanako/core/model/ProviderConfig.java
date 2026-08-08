package com.flora.hanako.core.model;

/**
 * 模型提供商配置：对应 openhanako 设置中的 Provider（API key + base URL）。
 * <p>注册到 {@code com.flora.ai.api.AiApi} 后展开为若干能力端点（CHAT / STREAM / JSON）。</p>
 */
public final class ProviderConfig {

    private String id;
    private String name;
    private String apiKind;
    private String baseUrl;
    private String apiKey;
    private boolean enabled;

    public ProviderConfig() {
    }

    public ProviderConfig(String id, String name, String apiKind, String baseUrl, String apiKey) {
        this.id = id;
        this.name = name;
        this.apiKind = apiKind;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.enabled = true;
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

    public String getApiKind() {
        return apiKind;
    }

    public void setApiKind(String apiKind) {
        this.apiKind = apiKind;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
