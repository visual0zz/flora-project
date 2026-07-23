package com.flora.entropy.hash;

import java.nio.charset.StandardCharsets;

import static com.flora.entropy.hash.UsageScenario.ADDRESSING;
import static com.flora.entropy.hash.UsageScenario.FAST;

public final class MurmurHash {
    /**
     * 对 int 值执行 mmh3 混合（MurmurHash3 风格的 finalizer）。
     *
     * @param x 输入值
     * @return 混合后的 int 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static int mmh3(int x) {
        x ^= x >>> 16;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x ^= x >>> 16;
        return x;
    }

    /**
     * 对 long 值执行 mmh3 混合。
     *
     * @param x 输入值
     * @return 混合后的 long 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static long mmh3(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    /**
     * 对字符串计算 mmh3 哈希（UTF-8 编码后计算）。
     *
     * @param s 输入字符串
     * @return 32 位哈希值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static int mm3(String s) {
        return mmh3(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 对字节数组计算 MurmurHash3 32 位哈希。
     *
     * @param data 输入字节数组
     * @return 32 位哈希值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static int mmh3(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }

        int length = data.length;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;

        int h1 = 0;


        int i = 0;
        while (i + 3 < length) {
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;

            i += 4;
        }


        int k1 = 0;
        switch (length & 3) {
            case 3:
                k1 ^= (data[i + 2] & 0xff) << 16;

            case 2:
                k1 ^= (data[i + 1] & 0xff) << 8;

            case 1:
                k1 ^= (data[i] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }


        h1 ^= length;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }
}
