package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.store.BlockHeader;

/**
 * 版本化算法适配层（见设计 02"版本化算法适配层"）。
 * <p>
 * CodecRegistry 按版本分发：{@code forRead(version)} 读取旧格式（向后兼容），
 * {@code latest()} 写路径永远用最新版本。旧实现保留供读，不删除；
 * 惰性迁移：旧对象保持旧格式直到被再次写入。
 */
public final class CodecRegistry {

    /** 当前最新格式版本。 */
    public static final byte LATEST_VERSION = Envelope.VERSION_1;

    private CodecRegistry() {
    }

    /**
     * 按版本分发读路径 Codec。
     *
     * @throws IllegalArgumentException 版本不受支持
     */
    public static ReadCodec forRead(byte version) {
        if (version == Envelope.VERSION_1) {
            return (block) -> {
                byte[] deobf = BlockHeader.deobfuscate(block);
                if (!BlockHeader.isBlock(deobf)) {
                    throw new IllegalArgumentException("bad block");
                }
                return deobf;
            };
        }
        throw new IllegalArgumentException("unsupported version: " + version);
    }

    /** 写路径：最新版本。 */
    public static byte latestVersion() {
        return LATEST_VERSION;
    }

    /** 读路径接口：给定落盘块（含异或混淆），返回解异或后的信封字节。 */
    @FunctionalInterface
    public interface ReadCodec {
        byte[] read(byte[] obfuscatedBlock);
    }
}
