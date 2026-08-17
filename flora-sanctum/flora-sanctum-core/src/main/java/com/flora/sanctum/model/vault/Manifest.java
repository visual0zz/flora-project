package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import java.util.Base64;

/**
 * manifest 明文引导块（见设计 02"manifest"）。
 * <p>
 * 负载 JSON：{version, type:"manifest", parent:"manifest", cryptoVersion, kdf, salt, params{m,i,p},
 * updateTimestamp, mac}。明文 + MAC，MAC 覆盖信封头 + 负载全部内容。时间戳存于块前缀（见 04b），
 * 不依赖仓库锚点持久化。
 */
public final class Manifest {

    private final int version;
    private final String parent;
    private final String cryptoVersion;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final long updateTimestamp;
    private final byte[] mac;

    public Manifest(int version, String parent, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    long updateTimestamp, byte[] mac) {
        this.version = version;
        this.parent = parent;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.updateTimestamp = updateTimestamp;
        this.mac = mac;
    }

    public int version() {
        return version;
    }

    public String parent() {
        return parent;
    }

    public String cryptoVersion() {
        return cryptoVersion;
    }

    public String kdf() {
        return kdf;
    }

    public byte[] salt() {
        return salt.clone();
    }

    public int memoryKiB() {
        return memoryKiB;
    }

    public int iterations() {
        return iterations;
    }

    public int parallelism() {
        return parallelism;
    }

    public long updateTimestamp() {
        return updateTimestamp;
    }

    public byte[] mac() {
        return mac.clone();
    }

    /** manifest MAC 密钥派生：macKey = HKDF-SHA256(KEK, "sanctum-manifest-mac", 32B)（见 02）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return com.flora.sanctum.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    /**
     * 计算 manifest MAC 输入（覆盖信封头 uuid 与负载全部字段，见 02"MAC 全量认证"）。
     * 负载字段按固定顺序拼接，不含 mac 字段本身。
     */
    public byte[] canonical(java.util.UUID blockUuid) {
        StringBuilder sb = new StringBuilder();
        sb.append(blockUuid).append('|');
        sb.append(version).append('|');
        sb.append("manifest").append('|');
        sb.append(parent).append('|');
        sb.append(cryptoVersion).append('|');
        sb.append(kdf).append('|');
        sb.append(Base64.getEncoder().encodeToString(salt)).append('|');
        sb.append(memoryKiB).append(',').append(iterations).append(',').append(parallelism).append('|');
        sb.append(updateTimestamp).append('|');
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 计算并返回 manifest MAC（用 macKey）。 */
    public byte[] computeMac(byte[] macKey, java.util.UUID blockUuid) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(macKey, "HmacSHA256"));
            return mac.doFinal(canonical(blockUuid));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 从 JSON 解析 manifest。 */
    public static Manifest fromJson(byte[] payload) {
        com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (!"manifest".equals(n.getString("type"))) {
            throw new IllegalArgumentException("not a manifest");
        }
        com.flora.root.codec.json.model.JsonObject params = n.getObject("params");
        return new Manifest(
                n.getInt("version"),
                n.getString("parent"),
                n.getString("cryptoVersion"),
                n.getString("kdf"),
                Base64.getDecoder().decode(n.getString("salt")),
                params.getInt("m"),
                params.getInt("i"),
                params.getInt("p"),
                n.getLong("updateTimestamp") == null ? 1 : n.getLong("updateTimestamp"),
                Base64.getDecoder().decode(n.getString("mac"))
        );
    }
}
