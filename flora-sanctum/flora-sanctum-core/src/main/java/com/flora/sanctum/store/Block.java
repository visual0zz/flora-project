package com.flora.sanctum.store;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 一个数据块（见设计 04b"块自描述"）。
 * <p>
 * 块 = base58 串，解码 + 解异或后是标准信封字节。存储层记录块的物理位置
 * （文件 + 行号）与块级时间戳（落盘为 {@code timestamp:base58} 前缀）以支持定位与冲突仲裁。
 */
public final class Block {

    private final Path file;
    private final long line;
    private final long timestamp;
    private final String base58;
    private final byte[] obfuscated;
    private final byte[] deobfuscated;

    public Block(Path file, long line, long timestamp, String base58, byte[] obfuscated, byte[] deobfuscated) {
        this.file = file;
        this.line = line;
        this.timestamp = timestamp;
        this.base58 = base58;
        this.obfuscated = obfuscated;
        this.deobfuscated = deobfuscated;
    }

    public Path file() {
        return file;
    }

    public long line() {
        return line;
    }

    /** 块级时间戳（落盘 {@code timestamp:base58} 前缀），用于冲突仲裁与时钟锚点。 */
    public long timestamp() {
        return timestamp;
    }

    public String base58() {
        return base58;
    }

    public byte[] obfuscated() {
        return obfuscated.clone();
    }

    public byte[] deobfuscated() {
        return deobfuscated.clone();
    }

    /** 对象 UUID（块内自述）。 */
    public UUID uuid() {
        return BlockHeader.uuid(deobfuscated);
    }

    /** keyId（仅密文块）。 */
    public byte[] keyId() {
        return BlockHeader.keyId(deobfuscated);
    }

    /** 是否密文块（flags 偏移 MAGIC_LEN+1 = 9，0x01）。 */
    public boolean isCipher() {
        return deobfuscated.length >= com.flora.sanctum.crypto.impl.Envelope.MAGIC_LEN + 2
                && (deobfuscated[com.flora.sanctum.crypto.impl.Envelope.MAGIC_LEN + 1] & 0x01) != 0;
    }

    /** 是否明文块（flags 偏移 MAGIC_LEN+1 = 9，0x02）。 */
    public boolean isPlaintext() {
        return deobfuscated.length >= com.flora.sanctum.crypto.impl.Envelope.MAGIC_LEN + 2
                && (deobfuscated[com.flora.sanctum.crypto.impl.Envelope.MAGIC_LEN + 1] & 0x02) != 0;
    }

    @Override
    public String toString() {
        return "Block{" + file + ":" + line + ", uuid=" + uuid() + "}";
    }
}
