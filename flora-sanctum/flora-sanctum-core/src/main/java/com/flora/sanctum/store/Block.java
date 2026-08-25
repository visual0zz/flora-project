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
    private final String timestampText; // 落盘前缀时间戳原文（AAD 重建依赖原文，见设计 04）
    private final byte[] obfuscated;
    private final byte[] deobfuscated;

    public Block(Path file, long line, String timestampText, byte[] obfuscated, byte[] deobfuscated) {
        this.file = file;
        this.line = line;
        this.timestamp = Long.parseLong(timestampText);
        this.timestampText = timestampText;
        this.obfuscated = obfuscated;
        this.deobfuscated = deobfuscated;
    }

    public Path file() {
        return file;
    }

    public long line() {
        return line;
    }

    /** 块级时间戳数值（落盘 {@code timestamp:base58} 前缀解析），用于冲突仲裁与时钟锚点。 */
    public long timestamp() {
        return timestamp;
    }

    /** 落盘前缀时间戳原文（AAD/MAC 重建依赖原文字符串，与 {@link #timestamp()} 数值等价）。 */
    public String timestampText() {
        return timestampText;
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
