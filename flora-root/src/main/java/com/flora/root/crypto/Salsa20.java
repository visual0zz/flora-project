package com.flora.root.crypto;

/**
 * Salsa20 流密码（标准 eSTREAM 变体，KeePass 内层随机流 innerRandomStreamID=2 所用）。
 * <p>JDK 未提供 Salsa20，故自研；仅用于解密 KDBX 受保护字段，不依赖任何第三方库。</p>
 * <p>布局与旋转常量严格对齐 NaCl/KeePass 的 Salsa20 实现（ECRYPT 测试向量逐字节验证）：
 * 64 位块计数器置于 state[8..9]，8 字节 nonce 置于 state[6..7]，每轮 8 个 quarterround，
 * 旋转常量为 (7, 9, 13, 18)，共 20 轮（10 个双轮）。</p>
 */
public final class Salsa20 {

    private static final int[] CONSTANTS = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

    private final int[] state = new int[16];

    /** @param key 32 字节密钥（KeePass 内层随机流密钥 = SHA-256(种子)）
     *  @param nonce 8 字节 nonce（KeePass 默认 {@code e8 30 09 4b 97 20 5d 2a}） */
    public Salsa20(byte[] key, byte[] nonce) {
        state[0] = CONSTANTS[0];
        state[5] = CONSTANTS[1];
        state[10] = CONSTANTS[2];
        state[15] = CONSTANTS[3];
        for (int i = 0; i < 4; i++) {
            state[1 + i] = le32(key, i * 4);
        }
        for (int i = 0; i < 4; i++) {
            state[11 + i] = le32(key, 16 + i * 4);
        }
        state[6] = le32(nonce, 0);
        state[7] = le32(nonce, 4);
    }

    /** 从指定字节偏移生成 keystream（KeePass 内层流为顺序推进，不重置）。 */
    public byte[] keystream(int len, long byteOffset) {
        byte[] out = new byte[len];
        int produced = 0;
        long pos = byteOffset;
        while (produced < len) {
            int block = (int) (pos / 64);
            int off = (int) (pos % 64);
            byte[] blockKs = block(block);
            int take = Math.min(64 - off, len - produced);
            System.arraycopy(blockKs, off, out, produced, take);
            produced += take;
            pos += (64 - off);
        }
        return out;
    }

    private byte[] block(int blockIndex) {
        int[] w = state.clone();
        w[8] = blockIndex & 0xffffffff;            // 64 位块计数器低 32 位
        w[9] = (blockIndex >>> 32) & 0xffffffff;   // 64 位块计数器高 32 位
        for (int i = 0; i < 10; i++) {
            // column rounds
            rr(w, 4, 0, 12, 7); rr(w, 9, 5, 1, 7); rr(w, 14, 10, 6, 7); rr(w, 3, 15, 11, 7);
            rr(w, 8, 4, 0, 9); rr(w, 13, 9, 5, 9); rr(w, 2, 14, 10, 9); rr(w, 7, 3, 15, 9);
            rr(w, 12, 8, 4, 13); rr(w, 1, 13, 9, 13); rr(w, 6, 2, 14, 13); rr(w, 11, 7, 3, 13);
            rr(w, 0, 12, 8, 18); rr(w, 5, 1, 13, 18); rr(w, 10, 6, 2, 18); rr(w, 15, 11, 7, 18);
            // diagonal rounds
            rr(w, 1, 0, 3, 7); rr(w, 6, 5, 4, 7); rr(w, 11, 10, 9, 7); rr(w, 12, 15, 14, 7);
            rr(w, 2, 1, 0, 9); rr(w, 7, 6, 5, 9); rr(w, 8, 11, 10, 9); rr(w, 13, 12, 15, 9);
            rr(w, 3, 2, 1, 13); rr(w, 4, 7, 6, 13); rr(w, 9, 8, 11, 13); rr(w, 14, 13, 12, 13);
            rr(w, 0, 3, 2, 18); rr(w, 5, 4, 7, 18); rr(w, 10, 9, 8, 18); rr(w, 15, 14, 13, 18);
        }
        byte[] b = new byte[64];
        for (int i = 0; i < 16; i++) {
            int v = (w[i] + state[i]) & 0xffffffff;
            b[i * 4] = (byte) v;
            b[i * 4 + 1] = (byte) (v >>> 8);
            b[i * 4 + 2] = (byte) (v >>> 16);
            b[i * 4 + 3] = (byte) (v >>> 24);
        }
        return b;
    }

    /** x[a] ^= ROTL((x[b] + x[c]) & 0xffffffff, r) —— Salsa20 的 quarterround 原语。 */
    private static void rr(int[] x, int a, int b, int c, int r) {
        x[a] ^= rotl((x[b] + x[c]) & 0xffffffff, r);
    }

    private static int rotl(int v, int r) {
        return ((v << r) | (v >>> (32 - r))) & 0xffffffff;
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8
                | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }
}
