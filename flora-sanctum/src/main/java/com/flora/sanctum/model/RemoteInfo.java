package com.flora.sanctum.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远程仓库信息（明文存储于 meta.json 的非机密元数据）。
 * <p>
 * SSH 私钥、token 等敏感凭据以加密 Entry 形式存储，不在此处。
 */
public class RemoteInfo {

    private String url;
    private String protocol; // "HTTPS" | "SSH"
    private String username;

    public RemoteInfo() {
    }

    public RemoteInfo(String url, String protocol, String username) {
        this.url = url;
        this.protocol = protocol;
        this.username = username;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    // -- JSON 序列化辅助 --

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", url);
        map.put("protocol", protocol);
        map.put("username", username);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RemoteInfo fromMap(Map<String, Object> map) {
        RemoteInfo info = new RemoteInfo();
        info.url = (String) map.getOrDefault("url", "");
        info.protocol = (String) map.getOrDefault("protocol", "HTTPS");
        info.username = (String) map.get("username");
        return info;
    }
}
