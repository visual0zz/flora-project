package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import java.util.Base64;

/**
 * manifest 明文引导块（见设计 02"manifest"）。
 * <p>
 * 负载 JSON：{version, type:"manifest", cryptoVersion, kdf, salt, params{m,i,p},
 * rootGroupUuid, updateTimestamp}。块格式与密文对齐：{@code 信封头 + 负载 + MAC(尾附)}，
 * MAC = HMAC-SHA256(macKey, 完整信封头 ‖ 时间戳 ‖ 负载)（见 {@link com.flora.sanctum.model.impl.ManifestStore}）。
 * 时间戳存于块前缀，MAC 不存于 JSON 内部（与密文 tag 位置对应）。
 */
public final class Manifest {

    private final int version;
    private final String cryptoVersion;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final java.util.UUID rootGroupUuid;
    private final long updateTimestamp;

    public Manifest(int version, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    java.util.UUID rootGroupUuid, long updateTimestamp) {
        this.version = version;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.rootGroupUuid = rootGroupUuid;
        this.updateTimestamp = updateTimestamp;
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

    /** 根对象 uuid（manifest 记录，解锁 O(1) 定位）。 */
    public java.util.UUID rootGroupUuid() {
        return rootGroupUuid;
    }

    public long updateTimestamp() {
        return updateTimestamp;
    }

    /** manifest MAC 密钥派生：macKey = HKDF-SHA256(KEK, "sanctum-manifest-mac", 32B)（见 02）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return com.flora.sanctum.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    /** 从 JSON 负载解析 manifest（MAC 在块尾部，不在此负载内）。 */
    public static Manifest fromJson(byte[] payload) {
        com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (StoredNodeType.MANIFEST != StoredNodeType.fromTag(n.getString("type"))) {
            throw new IllegalArgumentException("not a manifest");
        }
        com.flora.root.codec.json.model.JsonObject params = n.getObject("params");
        String rootUuidStr = n.getString("rootGroupUuid");
        return new Manifest(
                n.getInt("version"),
                n.getString("cryptoVersion"),
                n.getString("kdf"),
                Base64.getDecoder().decode(n.getString("salt")),
                params.getInt("m"),
                params.getInt("i"),
                params.getInt("p"),
                rootUuidStr == null ? null : java.util.UUID.fromString(rootUuidStr),
                n.getLong("updateTimestamp") == null ? 1 : n.getLong("updateTimestamp")
        );
    }
}
