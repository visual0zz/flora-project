package com.flora.sanctum.core.store;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 一个数据块（见设计 04b"块自描述"）。
 * <p>
 * 块 = base64 串，解码后是标准信封字节（无额外异或混淆）。存储层记录块的物理位置
 * （文件 + 行号）与块级时间戳（落盘为 {@code timestamp:base64} 前缀）以支持定位与冲突仲裁。
 * {@code masked}/{@code unmasked} 为同一信封原始字节（两者等价）。
 * <p>
 * 对象 uuid 不写入信封头，由块文件路径承载并反推（见 {@link #uuid()}）。
 */
public final class Block {

    private final Path file;
    private final long line;
    private final long timestamp;
    private final String timestampText; // 落盘前缀时间戳原文（AAD 重建依赖原文，见设计 04）
    private final byte[] masked;
    private final byte[] unmasked;
    private final UUID uuid;

    public Block(Path file, long line, String timestampText, byte[] masked, byte[] unmasked, UUID uuid) {
        this.file = file;
        this.line = line;
        this.timestamp = Long.parseLong(timestampText);
        this.timestampText = timestampText;
        this.masked = masked;
        this.unmasked = unmasked;
        this.uuid = uuid;
    }

    public Path file() {
        return file;
    }

    public long line() {
        return line;
    }

    /** 块级时间戳数值（落盘 {@code timestamp:base64} 前缀解析），用于冲突仲裁与时钟锚点。 */
    public long timestamp() {
        return timestamp;
    }

    /** 落盘前缀时间戳原文（AAD/MAC 重建依赖原文字符串，与 {@link #timestamp()} 数值等价）。 */
    public String timestampText() {
        return timestampText;
    }

    public byte[] masked() {
        return masked.clone();
    }

    public byte[] unmasked() {
        return unmasked.clone();
    }

    /**
     * 对象 UUID。不存于信封头内，而由块文件路径反推
     * （见 {@link com.flora.sanctum.core.store.impl.MarkdownObjectStore#uuidOf}），
     * 因此块被移动到别的路径即得到不同 uuid，AAD 认证随之失败。
     */
    public UUID uuid() {
        return uuid;
    }

    /** 是否密文块（flags 偏移 MAGIC_LEN+1 = 9，0x01）。 */
    public boolean isCipher() {
        return unmasked.length >= com.flora.sanctum.core.crypto.impl.Envelope.MAGIC_LEN + 2
                && (unmasked[com.flora.sanctum.core.crypto.impl.Envelope.MAGIC_LEN + 1] & 0x01) != 0;
    }

    /** 是否明文块（flags 偏移 MAGIC_LEN+1 = 9，0x02）。 */
    public boolean isPlaintext() {
        return unmasked.length >= com.flora.sanctum.core.crypto.impl.Envelope.MAGIC_LEN + 2
                && (unmasked[com.flora.sanctum.core.crypto.impl.Envelope.MAGIC_LEN + 1] & 0x02) != 0;
    }

    @Override
    public String toString() {
        return "Block{" + file + ":" + line + ", uuid=" + uuid() + "}";
    }
}
