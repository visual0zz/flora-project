package com.flora.sanctum.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 保险库元数据（明文存储）。
 * <p>
 * 包含格式版本、KDF 参数、盐值、远程仓库信息等非机密数据。
 * 通过 HMAC 签名（meta.json.sig）防篡改。
 */
public class VaultMeta {

    public static final int FORMAT_VERSION = 1;
    public static final String DEFAULT_KDF = "PBKDF2-HMAC-SHA256";
    public static final int DEFAULT_ITERATIONS = 600_000;

    private int formatVersion;
    private String kdf;
    private int iterations;
    private String salt; // base64 encoded
    private List<RemoteInfo> remotes;
    private long createdAt;
    private long updatedAt;

    public VaultMeta() {
        this.formatVersion = FORMAT_VERSION;
        this.kdf = DEFAULT_KDF;
        this.iterations = DEFAULT_ITERATIONS;
        this.remotes = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public int getFormatVersion() { return formatVersion; }
    public void setFormatVersion(int formatVersion) { this.formatVersion = formatVersion; }

    public String getKdf() { return kdf; }
    public void setKdf(String kdf) { this.kdf = kdf; }

    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public List<RemoteInfo> getRemotes() { return remotes; }
    public void setRemotes(List<RemoteInfo> remotes) { this.remotes = remotes; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    // -- JSON 序列化辅助 --

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("formatVersion", formatVersion);
        map.put("kdf", kdf);
        map.put("iterations", iterations);
        map.put("salt", salt);
        List<Map<String, Object>> remoteMaps = new ArrayList<>();
        for (RemoteInfo r : remotes) {
            remoteMaps.add(r.toMap());
        }
        map.put("remotes", remoteMaps);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static VaultMeta fromMap(Map<String, Object> map) {
        VaultMeta meta = new VaultMeta();
        meta.formatVersion = toInt(map.getOrDefault("formatVersion", FORMAT_VERSION));
        meta.kdf = (String) map.getOrDefault("kdf", DEFAULT_KDF);
        meta.iterations = toInt(map.getOrDefault("iterations", DEFAULT_ITERATIONS));
        meta.salt = (String) map.get("salt");
        meta.createdAt = toLong(map.get("createdAt"));
        meta.updatedAt = toLong(map.get("updatedAt"));

        List<Map<String, Object>> remoteMaps = (List<Map<String, Object>>) map.get("remotes");
        if (remoteMaps != null) {
            List<RemoteInfo> remotes = new ArrayList<>();
            for (Map<String, Object> rm : remoteMaps) {
                remotes.add(RemoteInfo.fromMap(rm));
            }
            meta.remotes = remotes;
        }
        return meta;
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return FORMAT_VERSION;
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }
}
