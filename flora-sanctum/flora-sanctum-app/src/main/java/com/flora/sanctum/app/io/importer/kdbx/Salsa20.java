package com.flora.sanctum.app.io.importer.kdbx;

/**
 * Salsa20 流密码（RFC 8439 变体的替代内核，KeePass 内层随机流使用 64 位块计数器 + 8 字节 nonce）。
 * <p>JDK 未提供 Salsa20，故自研；仅用于解密 KDBX 受保护字段，不依赖任何第三方库。</p>
 */
final class Salsa20 {

    private static final int[] CONSTANTS = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

    private final int[] state = new int[16];

    /** @param key 32 字节密钥（KeePass 内层随机流密钥）
     *  @param nonce 8 字节 nonce（KeePass 默认全零） */
    Salsa20(byte[] key, byte[] nonce) {
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
    byte[] keystream(int len, long byteOffset) {
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
        int[] s = state.clone();
        s[8] = blockIndex & 0xffffffff;            // 计数器低 32 位
        s[9] = (blockIndex >>> 32) & 0xffffffff;   // 计数器高 32 位
        int[] w = s.clone();
        for (int i = 0; i < 10; i++) {
            quarterRound(w, 0, 4, 8, 12);
            quarterRound(w, 1, 5, 9, 13);
            quarterRound(w, 2, 6, 10, 14);
            quarterRound(w, 3, 7, 11, 15);
            quarterRound(w, 0, 5, 10, 15);
            quarterRound(w, 1, 6, 11, 12);
            quarterRound(w, 2, 7, 8, 13);
            quarterRound(w, 3, 4, 9, 14);
        }
        int[] out = new int[16];
        for (int i = 0; i < 16; i++) {
            out[i] = w[i] + s[i];
        }
        byte[] b = new byte[64];
        for (int i = 0; i < 16; i++) {
            int v = out[i];
            b[i * 4] = (byte) v;
            b[i * 4 + 1] = (byte) (v >>> 8);
            b[i * 4 + 2] = (byte) (v >>> 16);
            b[i * 4 + 3] = (byte) (v >>> 24);
        }
        return b;
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] = rotAdd(x[a], x[b], 7);
        x[d] = rotXor(x[d], x[a], 9);
        x[c] = rotAdd(x[c], x[d], 13);
        x[b] = rotXor(x[b], x[c], 18);
    }

    private static int rotAdd(int a, int b, int r) {
        return (a + b) & 0xffffffff;
    }

    private static int rotXor(int a, int b, int r) {
        int v = a ^ b;
        return (v << r) | (v >>> (32 - r));
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8
                | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }
}
