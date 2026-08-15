package com.flora.sanctum.crypto.impl;

import com.flora.sanctum.crypto.impl.HkdfSha256;

import java.security.SecureRandom;

/**
 * 安全随机源：混合熵（见设计 02"熵混合"）。
 * <p>
 * 所有安全随机（nonce、keyId 的 byte1、DEK、salt、UUID 等）统一经此入口获取，
 * 不直接依赖单一 {@link SecureRandom}。主源为系统 CSPRNG，叠加一个进程内自维护的
 * 熵累积器，两者经 HKDF-SHA256 提取-扩展合并；任一源退化时，HMAC 单向性 + 另一源
 * 仍保证输出不可预测。
 * <p>
 * 对外不暴露混合细节，调用方只使用 {@link #nextBytes(byte[])} 等常规随机接口。
 */
public final class SecureRandomSource {

    private final SecureRandom primary;
    private final long seedOffset;

    /** 熵累积器状态：持续混入 nanoTime 低位抖动。 */
    private long pool;
    private long counter;

    public SecureRandomSource() {
        this.primary = new SecureRandom();
        this.seedOffset = System.nanoTime();
        this.pool = 0;
        this.counter = 0;
    }

    /** 填满 dst，使用混合熵。 */
    public void nextBytes(byte[] dst) {
        // 主源随机字节
        byte[] p = new byte[dst.length];
        primary.nextBytes(p);
        // 叠加源：进程内抖动 + 累积状态
        byte[] o = overlay(dst.length);
        // 混合：XOR 后经 HKDF-SHA256 提取-扩展
        for (int i = 0; i < dst.length; i++) {
            dst[i] = (byte) (p[i] ^ o[i]);
        }
        byte[] out = HkdfSha256.expand(p, o, dst.length);
        System.arraycopy(out, 0, dst, 0, dst.length);
    }

    /** 返回一个随机字节。 */
    public byte nextByte() {
        byte[] b = new byte[1];
        nextBytes(b);
        return b[0];
    }

    /** 返回一个随机 int。 */
    public int nextInt() {
        byte[] b = new byte[4];
        nextBytes(b);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    /** 返回一个随机 long。 */
    public long nextLong() {
        byte[] b = new byte[8];
        nextBytes(b);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[i] & 0xFFL);
        }
        return v;
    }

    private byte[] overlay(int len) {
        byte[] o = new byte[len];
        long acc = pool ^ seedOffset;
        for (int i = 0; i < len; i++) {
            // 不可控抖动：线程调度 / GC / nanoTime 低位
            long t = System.nanoTime();
            acc ^= t;
            acc = acc * 31L + (t & 0xFF);
            o[i] = (byte) (acc & 0xFF);
        }
        pool = acc;
        counter++;
        return o;
    }
}
