package com.flora.honey;

import java.io.ByteArrayOutputStream;

/**
 * 算术编码器（Witten–Neal–Cleary, CACM 1987 经典实现，32 位寄存器）。
 *
 * <p>把 token 序列按模型给出的频率分布压成字节流，且保证：用同一模型、同一密钥播种、
 * 同一上下文推进规则编码与解码可精确往返。编码器每编完一个 token、解码器每解出一个 token
 * 都调用 {@link TokenModel#observe(int)} 推进上下文，随后<b>按当前模型重建累积表</b>——
 * 因为模型是上下文自适应的，分布会逐符号变化。</p>
 *
 * <p>解码端对越界/截断的随机字节做了健壮处理（{@link BitReader} 读到末尾补 0，
 * {@link #symbolFor} 对越界值做夹取），<b>永不抛异常</b>，因此错误密钥解出随机字节时
 * 也能稳定输出合法 token 序列。</p>
 */
final class RangeCoder {

    private static final int BITS = 32;
    private static final long MAX = (1L << BITS) - 1;       // 0xFFFFFFFF
    private static final long HALF = 1L << (BITS - 1);      // 0x80000000
    private static final long QUARTER = HALF / 2;           // 0x40000000
    private static final long THREE_QUARTER = 3 * QUARTER;  // 0xC0000000

    static byte[] encode(int[] symbols, TokenModel model) {
        int n = model.vocabSize();
        int[] cum = new int[n + 1]; // 复用同一个暂存数组，逐符号按当前模型重建
        BitWriter out = new BitWriter();
        long low = 0;
        long high = MAX;
        int pending = 0;

        for (int s : symbols) {
            rebuildCum(model, cum);
            int total = cum[n];
            long range = high - low + 1;
            high = low + (range * cum[s + 1]) / total - 1;
            low = low + (range * cum[s]) / total;
            while (true) {
                if (high < HALF) {
                    out.writeBit(0, pending);
                    pending = 0;
                } else if (low >= HALF) {
                    out.writeBit(1, pending);
                    pending = 0;
                    low -= HALF;
                    high -= HALF;
                } else if (low >= QUARTER && high < THREE_QUARTER) {
                    pending++;
                    low -= QUARTER;
                    high -= QUARTER;
                } else {
                    break;
                }
                low = (low << 1) & MAX;
                high = ((high << 1) | 1) & MAX;
            }
            model.observe(s);
        }
        // 收尾：输出 pending 位 + 一个区分位
        pending++;
        if (low < QUARTER) {
            out.writeBit(0, pending);
        } else {
            out.writeBit(1, pending);
        }
        out.flush();
        return out.toByteArray();
    }

    static int[] decode(byte[] data, TokenModel model, int count) {
        int n = model.vocabSize();
        int[] cum = new int[n + 1];
        BitReader in = new BitReader(data);
        long value = 0;
        for (int i = 0; i < BITS; i++) {
            value = (value << 1) | in.readBit();
        }
        long low = 0;
        long high = MAX;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            rebuildCum(model, cum);
            int total = cum[n];
            long range = high - low + 1;
            long cumVal = ((value - low + 1) * total - 1) / range;
            int s = symbolFor(cum, cumVal);
            out[i] = s;
            high = low + (range * cum[s + 1]) / total - 1;
            low = low + (range * cum[s]) / total;
            while (true) {
                if (high < HALF) {
                    // 高位为 0，无需调整
                } else if (low >= HALF) {
                    value -= HALF;
                    low -= HALF;
                    high -= HALF;
                } else if (low >= QUARTER && high < THREE_QUARTER) {
                    value -= QUARTER;
                    low -= QUARTER;
                    high -= QUARTER;
                } else {
                    break;
                }
                low = (low << 1) & MAX;
                high = ((high << 1) | 1) & MAX;
                value = ((value << 1) | in.readBit()) & MAX;
            }
            model.observe(s);
        }
        return out;
    }

    /** 按模型当前分布重建累积频率表（cum[i] = 前 i 个 token 的频率和）。 */
    private static void rebuildCum(TokenModel model, int[] cum) {
        int acc = 0;
        for (int i = 0; i < cum.length - 1; i++) {
            acc += Math.max(1, model.freq(i));
            cum[i + 1] = acc;
        }
    }

    /** 二分查找累积值所属的 token；越界值夹取到 [0, n-1]，保证永不抛异常。 */
    private static int symbolFor(int[] cum, long value) {
        int lo = 0;
        int hi = cum.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] > value) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        int s = lo - 1;
        if (s < 0) {
            return 0;
        }
        return s >= cum.length - 1 ? cum.length - 2 : s;
    }

    /** MSB-first 位写入器；writeBit(bit, pending) 写 1 个 bit 后追加 pending 个反相 bit。 */
    private static final class BitWriter {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private int acc = 0;
        private int nbits = 0;

        void writeBit(int bit, int pending) {
            push(bit);
            for (int i = 0; i < pending; i++) {
                push(1 - bit);
            }
        }

        private void push(int bit) {
            acc = (acc << 1) | (bit & 1);
            nbits++;
            if (nbits == 8) {
                bos.write(acc & 0xFF);
                acc = 0;
                nbits = 0;
            }
        }

        void flush() {
            if (nbits > 0) {
                acc <<= (8 - nbits);
                bos.write(acc & 0xFF);
                acc = 0;
                nbits = 0;
            }
        }

        byte[] toByteArray() {
            return bos.toByteArray();
        }
    }

    /** MSB-first 位读取器；流末尾补 0（保证错误密钥的随机字节也能稳定解码）。 */
    private static final class BitReader {
        private final byte[] data;
        private int pos = 0;
        private int acc = 0;
        private int nbits = 0;

        BitReader(byte[] data) {
            this.data = data;
        }

        int readBit() {
            if (nbits == 0) {
                if (pos < data.length) {
                    acc = data[pos++] & 0xFF;
                } else {
                    acc = 0; // 末尾补 0
                }
                nbits = 8;
            }
            nbits--;
            return (acc >> nbits) & 1;
        }
    }
}
