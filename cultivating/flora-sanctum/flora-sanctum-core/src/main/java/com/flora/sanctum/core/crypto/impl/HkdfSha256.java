package com.flora.sanctum.core.crypto.impl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * HKDF-SHA256（RFC 5869）提取-扩展。
 * <p>
 * 用于熵混合（见 02"熵混合"）、encKey 派生、manifest MAC 密钥派生等。
 */
public final class HkdfSha256 {

    private HkdfSha256() {
    }

    /**
     * 提取：PRK = HMAC-SHA256(salt, ikm)。
     */
    public static byte[] extract(byte[] ikm, byte[] salt) {
        byte[] s = (salt == null || salt.length == 0) ? new byte[32] : salt;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(s, "HmacSHA256"));
            return mac.doFinal(ikm);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF-Extract failed", e);
        }
    }

    /**
     * 扩展：从 PRK 扩展出 len 字节，可选 info 上下文。
     */
    public static byte[] expand(byte[] prk, byte[] info, int len) {
        if (len < 0 || len > 255 * 32) {
            throw new IllegalArgumentException("invalid output length: " + len);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] t = new byte[0];
            byte[] result = new byte[len];
            int pos = 0;
            int counter = 1;
            while (pos < len) {
                mac.reset();
                if (t.length > 0) {
                    mac.update(t);
                }
                if (info != null && info.length > 0) {
                    mac.update(info);
                }
                mac.update((byte) counter);
                t = mac.doFinal();
                int take = Math.min(t.length, len - pos);
                System.arraycopy(t, 0, result, pos, take);
                pos += take;
                counter++;
            }
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF-Expand failed", e);
        }
    }

    /** 便捷：提取+扩展一步。 */
    public static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int len) {
        return expand(extract(ikm, salt), info, len);
    }

    /** 便捷：基于字符串 info 的推导。 */
    public static byte[] derive(byte[] ikm, byte[] salt, String info, int len) {
        return derive(ikm, salt, info.getBytes(StandardCharsets.UTF_8), len);
    }
}
