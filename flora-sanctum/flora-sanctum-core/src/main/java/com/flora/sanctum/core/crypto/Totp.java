package com.flora.sanctum.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TOTP（RFC 6238，见设计 02"TOTP"）。
 * <p>
 * TOTP 字段的 value 以 {@code otpauth://} URI 或裸 base32 种子存储；本类据此生成验证码，无需服务端。
 * HMAC 走 JDK JCA（RFC 2104 标准，经 TotpTest 的 RFC 6238 向量验证）。
 */
public final class Totp {

    private Totp() {
    }

    /** 默认 30 秒步长，6 位码，SHA1 算法。 */
    public static final int DEFAULT_PERIOD_SECONDS = 30;
    public static final int DEFAULT_DIGITS = 6;
    public static final String DEFAULT_ALGORITHM = "SHA1";

    /** HMAC 算法白名单（RFC 6238 允许 SHA1/SHA256/SHA512）。 */
    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("SHA1", "SHA256", "SHA512");

    /**
     * 基于裸 base32 种子生成当前 TOTP 验证码（向后兼容：字段 value 为裸种子时）。
     */
    public static String generate(byte[] secret, int digits, int periodSec) {
        return generate(secret, System.currentTimeMillis() / 1000, digits, periodSec, DEFAULT_ALGORITHM);
    }

    /** 基于裸种子在指定 Unix 时间生成验证码（用于测试/校验窗口）。 */
    public static String generate(byte[] secret, long unixSeconds, int digits, int periodSec) {
        return generate(secret, unixSeconds, digits, periodSec, DEFAULT_ALGORITHM);
    }

    /**
     * 从 {@code otpauth://} URI 生成当前 TOTP 验证码。
     * 解析其中的 secret/digits/period/algorithm 参数；仅支持 totp 类型（与 kind:"totp" 一致）。
     */
    public static String generateFromUri(String uri) {
        return generateFromUri(uri, System.currentTimeMillis() / 1000);
    }

    /** 从 URI 在指定 Unix 时间生成（用于测试/校验窗口）。 */
    public static String generateFromUri(String uri, long unixSeconds) {
        ParsedOtp parsed = parseOtpAuth(uri);
        return generate(parsed.secret, unixSeconds, parsed.digits, parsed.period, parsed.algorithm);
    }

    private static String generate(byte[] secret, long unixSeconds, int digits, int periodSec, String algorithm) {
        if (digits != 6 && digits != 8) {
            throw new IllegalArgumentException("digits must be 6 or 8: " + digits);
        }
        if (periodSec <= 0) {
            throw new IllegalArgumentException("period must be positive: " + periodSec);
        }
        long counter = unixSeconds / periodSec;
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmac(secret, counterBytes, algorithm);
        // 动态截断（RFC 4226）
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    private static byte[] hmac(byte[] key, byte[] data, String algorithm) {
        String jca = "Hmac" + algorithm;
        try {
            Mac mac = Mac.getInstance(jca);
            mac.init(new SecretKeySpec(key, jca));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Hmac " + algorithm + " failed", e);
        }
    }

    /** 解析 otpauth:// URI，提取种子/位数/步长/算法（仅 totp 类型）。 */
    private static ParsedOtp parseOtpAuth(String uri) {
        URI u;
        try {
            u = URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid otpauth uri: " + uri, e);
        }
        if (!"otpauth".equalsIgnoreCase(u.getScheme())) {
            throw new IllegalArgumentException("not an otpauth uri: " + uri);
        }
        String otpType = u.getHost();
        if (otpType == null) {
            throw new IllegalArgumentException("missing otp type in uri: " + uri);
        }
        if (!"totp".equalsIgnoreCase(otpType)) {
            // Sanctum 的 TOTP 字段仅对应 totp 类型；hotp 等不支持
            throw new IllegalArgumentException("unsupported otp type: " + otpType + " (only totp is supported)");
        }
        Map<String, String> q = queryParams(u.getRawQuery());
        String secretStr = q.get("secret");
        if (secretStr == null || secretStr.isEmpty()) {
            throw new IllegalArgumentException("missing secret in otpauth uri");
        }
        byte[] secret = com.flora.root.codec.Base32.decode(secretStr);
        int digits = parseDigits(q.get("digits"));
        int period = parsePeriod(q.get("period"));
        String algorithm = parseAlgorithm(q.get("algorithm"));
        return new ParsedOtp(secret, digits, period, algorithm);
    }

    private static Map<String, String> queryParams(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return m;
        }
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                m.put(pair, "");
            } else {
                m.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return m;
    }

    private static int parseDigits(String s) {
        if (s == null || s.isEmpty()) {
            return DEFAULT_DIGITS;
        }
        try {
            int d = Integer.parseInt(s);
            if (d != 6 && d != 8) {
                throw new IllegalArgumentException("digits must be 6 or 8: " + s);
            }
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid digits: " + s, e);
        }
    }

    private static int parsePeriod(String s) {
        if (s == null || s.isEmpty()) {
            return DEFAULT_PERIOD_SECONDS;
        }
        try {
            int p = Integer.parseInt(s);
            if (p <= 0) {
                throw new IllegalArgumentException("period must be positive: " + s);
            }
            return p;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid period: " + s, e);
        }
    }

    private static String parseAlgorithm(String s) {
        if (s == null || s.isEmpty()) {
            return DEFAULT_ALGORITHM;
        }
        String up = s.toUpperCase(Locale.ROOT);
        if (!ALLOWED_ALGORITHMS.contains(up)) {
            throw new IllegalArgumentException("unsupported algorithm: " + s);
        }
        return up;
    }

    private record ParsedOtp(byte[] secret, int digits, int period, String algorithm) {
    }
}
