package com.flora.sanctum.model;

import java.util.Base64;

/**
 * manifest 明文引导块（见设计 02"manifest"）。
 * <p>
 * 负载 JSON：{version, type:"manifest", cryptoVersion, kdf, salt, params{m,i,p},
 * warehouseTime, updateTimestamp, mac}。明文 + MAC，MAC 覆盖信封头 + 负载全部内容。
 */
public final class Manifest {

    private final int version;
    private final String cryptoVersion;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final long warehouseTime;
    private final long updateTimestamp;
    private final byte[] mac;

    public Manifest(int version, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    long warehouseTime, long updateTimestamp, byte[] mac) {
        this.version = version;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.warehouseTime = warehouseTime;
        this.updateTimestamp = updateTimestamp;
        this.mac = mac;
    }

    public int version() {
        return version;
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

    public long warehouseTime() {
        return warehouseTime;
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
        sb.append(cryptoVersion).append('|');
        sb.append(kdf).append('|');
        sb.append(Base64.getEncoder().encodeToString(salt)).append('|');
        sb.append(memoryKiB).append(',').append(iterations).append(',').append(parallelism).append('|');
        sb.append(warehouseTime).append('|');
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
        Json.Node n = Json.parse(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (!"manifest".equals(n.str("type"))) {
            throw new IllegalArgumentException("not a manifest");
        }
        Json.Node params = n.get("params");
        return new Manifest(
                n.get("version").asInt(),
                n.str("cryptoVersion"),
                n.str("kdf"),
                Base64.getDecoder().decode(n.str("salt")),
                params.get("m").asInt(),
                params.get("i").asInt(),
                params.get("p").asInt(),
                n.lng("warehouseTime") == null ? 1 : n.lng("warehouseTime"),
                n.lng("updateTimestamp") == null ? 1 : n.lng("updateTimestamp"),
                Base64.getDecoder().decode(n.str("mac"))
        );
    }
}
