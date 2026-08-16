package com.flora.sanctum.model;

import java.util.Arrays;

/**
 * 库元数据（领域层，来自 manifest 明文引导块）。
 * <p>
 * 包含：格式版本、cryptoVersion、KDF 算法与参数、salt、仓库时间戳。
 * 区别于 {@link Manifest}（存储层块格式，含 MAC）。
 */
public final class Metadata {

    private final int version;
    private final String cryptoVersion;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final long warehouseTime;
    private final long updateTimestamp;

    public Metadata(int version, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    long warehouseTime, long updateTimestamp) {
        this.version = version;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt == null ? null : salt.clone();
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.warehouseTime = warehouseTime;
        this.updateTimestamp = updateTimestamp;
    }

    /** 从存储层 Manifest 提取元数据。 */
    public static Metadata from(Manifest m) {
        return new Metadata(m.version(), m.cryptoVersion(), m.kdf(), m.salt(),
                m.memoryKiB(), m.iterations(), m.parallelism(),
                m.warehouseTime(), m.updateTimestamp());
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

    @Override
    public String toString() {
        return "Metadata{v" + version + ", " + cryptoVersion + ", kdf=" + kdf
                + ", m=" + memoryKiB + ", i=" + iterations + ", p=" + parallelism
                + ", warehouseTime=" + warehouseTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Metadata m)) {
            return false;
        }
        return version == m.version && memoryKiB == m.memoryKiB && iterations == m.iterations
                && parallelism == m.parallelism && warehouseTime == m.warehouseTime
                && updateTimestamp == m.updateTimestamp
                && cryptoVersion.equals(m.cryptoVersion) && kdf.equals(m.kdf)
                && Arrays.equals(salt, m.salt);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(salt) ^ version ^ memoryKiB ^ iterations ^ parallelism;
    }
}
