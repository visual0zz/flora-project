package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import java.util.Arrays;

/**
 * 库元数据（领域层，来自 manifest 明文引导块）。
 * <p>
 * 包含：格式版本、cryptoVersion、KDF 算法与参数、salt、仓库时间戳，
 * 以及 manifest 块的物理定位（文件+行号，供审计/恢复）。
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
    private final long updateTimestamp;
    private final java.nio.file.Path file;
    private final long line;

    public Metadata(int version, String cryptoVersion, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism,
                    long updateTimestamp) {
        this(version, cryptoVersion, kdf, salt, memoryKiB, iterations, parallelism,
                updateTimestamp, null, -1);
    }

    Metadata(int version, String cryptoVersion, String kdf, byte[] salt,
             int memoryKiB, int iterations, int parallelism,
             long updateTimestamp,
             java.nio.file.Path file, long line) {
        this.version = version;
        this.cryptoVersion = cryptoVersion;
        this.kdf = kdf;
        this.salt = salt == null ? null : salt.clone();
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.updateTimestamp = updateTimestamp;
        this.file = file;
        this.line = line;
    }

    /** 从存储层 Manifest 提取元数据。 */
    public static Metadata from(Manifest m) {
        return new Metadata(m.version(), m.cryptoVersion(), m.kdf(), m.salt(),
                m.memoryKiB(), m.iterations(), m.parallelism(),
                m.updateTimestamp());
    }

    /** 附加 manifest 块的物理定位（文件+行号），返回新实例。 */
    public Metadata withBlock(java.nio.file.Path file, long line) {
        return new Metadata(version, cryptoVersion, kdf, salt, memoryKiB, iterations, parallelism,
                updateTimestamp, file, line);
    }

    /** manifest 块所在文件（未定位返回 null）。 */
    public java.nio.file.Path file() {
        return file;
    }

    /** manifest 块所在行号（未定位返回 -1）。 */
    public long line() {
        return line;
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

    @Override
    public String toString() {
        return "Metadata{v" + version + ", " + cryptoVersion + ", kdf=" + kdf
                + ", m=" + memoryKiB + ", i=" + iterations + ", p=" + parallelism
                + ", updateTimestamp=" + updateTimestamp + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Metadata m)) {
            return false;
        }
        return version == m.version && memoryKiB == m.memoryKiB && iterations == m.iterations
                && parallelism == m.parallelism && updateTimestamp == m.updateTimestamp
                && cryptoVersion.equals(m.cryptoVersion) && kdf.equals(m.kdf)
                && Arrays.equals(salt, m.salt);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(salt) ^ version ^ memoryKiB ^ iterations ^ parallelism;
    }
}
