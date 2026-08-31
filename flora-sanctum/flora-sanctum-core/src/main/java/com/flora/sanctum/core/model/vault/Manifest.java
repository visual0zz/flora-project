package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import java.util.Base64;

/**
 * manifest 明文引导块（见设计 02"manifest"）。
 * <p>
 * 负载 JSON：{version, type:"manifest", cryptoVersion, kdf, salt, params{memoryKiB,iterations,parallelism},
 * updateTimestamp}。块格式与密文对齐：{@code 信封头 + 负载 + MAC(尾附)}，
 * MAC = HMAC-SHA256(macKey, {@code uuid ‖ 时间戳 ‖ 信封头 ‖ 负载})
 * （见 {@link com.flora.sanctum.core.model.impl.ManifestStore}）。
 * 时间戳存于块前缀，MAC 不存于 JSON 内部（与密文 tag 位置对应）。
 * <p>
 * 根对象 uuid 不由 manifest 记录，而由 KEK 单向推导
 * （见 {@link com.flora.sanctum.core.crypto.RootUuid#derive}）：同一主密码即重算出同一根对象路径，
 * 换主密码后根对象的分片位置随之改变。
 */
public final class Manifest {

    /**
     * 固定保留 uuid：manifest 引导块永远使用此 uuid，使其分片路径确定不变
     * （Markdown 存储下为 {@code <库根>/00/00000000000000000000000000000000.md}），
     * 无需扫描即可定位。全 0 为系统保留块，普通数据节点用随机 uuid，不会与之冲突。
     */
    public static final java.util.UUID MANIFEST_UUID = new java.util.UUID(0L, 0L);

    private final int version;
    private final String cryptoVersion;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final long updateTimestamp;

    public Manifest(int version, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    long updateTimestamp) {
        this.version = version;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
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

    public long updateTimestamp() {
        return updateTimestamp;
    }

    /** manifest MAC 密钥派生：macKey = HKDF-SHA256(KEK, "sanctum-manifest-mac", 32B)（见 02）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return com.flora.sanctum.core.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    /** 从 JSON 负载解析 manifest（MAC 在块尾部，不在此负载内）。 */
    public static Manifest fromJson(byte[] payload) {
        com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (StoredNodeType.MANIFEST != StoredNodeType.fromTag(n.getString("type"))) {
            throw new IllegalArgumentException("not a manifest");
        }
        com.flora.root.codec.json.model.JsonObject params = n.getObject("params");
        return new Manifest(
                n.getInt("version"),
                n.getString("cryptoVersion"),
                n.getString("kdf"),
                Base64.getDecoder().decode(n.getString("salt")),
                params.getInt("memoryKiB"),
                params.getInt("iterations"),
                params.getInt("parallelism"),
                n.getLong("updateTimestamp") == null ? 1 : n.getLong("updateTimestamp")
        );
    }
}
