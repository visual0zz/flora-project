package com.flora.sanctum.store;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 一个数据块（见设计 04b"块自描述"）。
 * <p>
 * 块 = base58 串，解码 + 解异或后是标准信封字节。存储层记录块的物理位置
 * （文件 + 行号）以支持原位替换。
 */
public final class Block {

    private final Path file;
    private final long line;
    private final String base58;
    private final byte[] obfuscated;
    private final byte[] deobfuscated;

    public Block(Path file, long line, String base58, byte[] obfuscated, byte[] deobfuscated) {
        this.file = file;
        this.line = line;
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

    /** 是否密文块（flags 0x01）。 */
    public boolean isCipher() {
        return deobfuscated.length >= 6 && (deobfuscated[5] & 0x01) != 0;
    }

    /** 是否明文块（flags 0x02）。 */
    public boolean isPlaintext() {
        return deobfuscated.length >= 6 && (deobfuscated[5] & 0x02) != 0;
    }

    @Override
    public String toString() {
        return "Block{" + file + ":" + line + ", uuid=" + uuid() + "}";
    }
}
