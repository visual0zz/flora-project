package com.flora.sanctum.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * TOTP（RFC 6238，见设计 02"TOTP"）。
 * <p>
 * TOTP 种子作为 {@code kind:"totp"} 的字段存储；本类从种子生成验证码，无需服务端。
 * HMAC-SHA1 走 JDK JCA（RFC 2104 标准，经 TotpTest 的 RFC 6238 向量验证）。
 */
public final class Totp {

    private Totp() {
    }

    /** 默认 30 秒步长，6 位码。 */
    public static final int DEFAULT_PERIOD_SECONDS = 30;
    public static final int DEFAULT_DIGITS = 6;

    /**
     * 基于种子生成当前 TOTP 验证码。
     *
     * @param secret    种子（base32 解码后的字节）
     * @param digits    码位数（6/8）
     * @param periodSec 步长秒
     */
    public static String generate(byte[] secret, int digits, int periodSec) {
        return generate(secret, System.currentTimeMillis() / 1000, digits, periodSec);
    }

    /** 基于种子在指定 Unix 时间生成验证码（用于测试/校验窗口）。 */
    public static String generate(byte[] secret, long unixSeconds, int digits, int periodSec) {
        long counter = unixSeconds / periodSec;
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmacSha1(secret, counterBytes);
        // 动态截断（RFC 4226）
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 failed", e);
        }
    }
}
