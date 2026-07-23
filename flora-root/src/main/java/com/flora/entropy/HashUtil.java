package com.flora.entropy;

import com.flora.entropy.hash.GoldenRatioMix;
import com.flora.entropy.hash.MurmurHash;
import com.flora.entropy.hash.StandardHash;
import com.flora.entropy.hash.SuitedFor;

import static com.flora.entropy.hash.UsageScenario.*;

/**
 * 哈希工具门面类，提供多种哈希算法的静态方法。
 * <p>
 * 本类不包含任何实现，所有方法委托给 {@link com.flora.entropy.hash} 包中的
 * 专门实现类：
 * </p>
 * <ul>
 *   <li>{@link GoldenRatioMix} — 黄金分割比快速混合（koloMix）</li>
 *   <li>{@link MurmurHash} — MurmurHash3 风格哈希（mmh3）</li>
 *   <li>{@link StandardHash} — 标准加密哈希（MD5、SHA 系列）</li>
 * </ul>
 * 每种方法通过 {@link SuitedFor} 注解标注适用场景。
 */
public final class HashUtil {
    private HashUtil() {}

    // ====== 快速非加密哈希（委托 GoldenRatioMix） ======

    @SuitedFor({FAST, ADDRESSING})
    public static long goldenHash(long x) {
        return GoldenRatioMix.goldenHash(x);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static int goldenHash(int x) {
        return GoldenRatioMix.goldenHash(x);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static short goldenHash(short x) {
        return GoldenRatioMix.goldenHash(x);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static byte goldenHash(byte x) {
        return GoldenRatioMix.goldenHash(x);
    }

    // ====== MurmurHash3 风格哈希（委托 MurmurHash） ======

    @SuitedFor({FAST, ADDRESSING})
    public static int mmh3(int x) {
        return MurmurHash.mmh3(x);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static long mmh3(long x) {
        return MurmurHash.mmh3(x);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static int mm3(String s) {
        return MurmurHash.mm3(s);
    }

    @SuitedFor({FAST, ADDRESSING})
    public static int mmh3(byte[] data) {
        return MurmurHash.mmh3(data);
    }

    // ====== 标准加密哈希（委托 StandardHash） ======

    @SuitedFor(COMPATIBLE)
    public static String md5(String s) {
        return StandardHash.md5(s);
    }

    @SuitedFor(COMPATIBLE)
    public static byte[] md5(byte[] data) {
        return StandardHash.md5(data);
    }

    @SuitedFor(COMPATIBLE)
    public static String sha1(String s) {
        return StandardHash.sha1(s);
    }

    @SuitedFor(COMPATIBLE)
    public static byte[] sha1(byte[] data) {
        return StandardHash.sha1(data);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha256(String s) {
        return StandardHash.sha256(s);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha256(byte[] data) {
        return StandardHash.sha256(data);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha512(String s) {
        return StandardHash.sha512(s);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha512(byte[] data) {
        return StandardHash.sha512(data);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static String sha3(String s) {
        return StandardHash.sha3(s);
    }

    @SuitedFor({ENCRYPTING, ADDRESSING})
    public static byte[] sha3(byte[] data) {
        return StandardHash.sha3(data);
    }
}
