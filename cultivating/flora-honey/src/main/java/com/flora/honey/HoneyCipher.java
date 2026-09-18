package com.flora.honey;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

/**
 * 蜜糖加密核心：加密“LLM 预测 + 区间编码”的结果，且密文不带认证标签。
 *
 * <p><b>统一解密路径（无预言机）：</b>无论密钥正确与否，解密都执行
 * “AES-CTR 解密 → 用密钥播种的模型做区间解码 → 文本”这一条代码路径，
 * 不校验任何 tag、不抛异常。正确密钥解出真实明文；错误密钥解出随机字节，
 * 经同一模型解码后仍是合法 token，表现为一段看似正常的诱饵文本。
 * 因为没有任何可校验的认证信息，攻击者无法判断某次解密是否“成功”。</p>
 *
 * <p><b>模型由密钥播种：</b>加密与解密都用“输入密钥”派生种子初始化同一模型，
 * 因此正确密钥下模型分布与编码时一致 → 精确还原；错误密钥下分布不同 → 不同诱饵。</p>
 *
 * <p><b>安全说明（原型范围）：</b>AES-CTR 刻意不加 MAC，以换取“无预言机”的蜜糖性质；
 * 这以放弃完整性为代价（攻击者可能篡改密文，但只会让解密结果变成另一段诱饵）。
 * 真实部署需另行加固（密钥强度、侧信道、模型分布贴近真实明文分布等）。</p>
 */
public final class HoneyCipher {

    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITER = 200_000;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 16; // SunJCE 的 AES/CTR 要求 IV 长度等于分组长度

    private final TokenModel model;
    private final SecureRandom rng = new SecureRandom();

    public HoneyCipher(TokenModel model) {
        this.model = model;
    }

    /** 加密明文：区间编码（密钥播种的模型）→ AES-CTR（无 MAC）→ 密文 blob。 */
    public byte[] encrypt(char[] passphrase, String plaintext) {
        model.seed(decoySeed(passphrase));
        int[] symbols = toSymbols(plaintext);
        byte[] coded = RangeCoder.encode(symbols, model);
        byte[] payload = ByteBuffer.allocate(4 + coded.length).putInt(symbols.length).put(coded).array();

        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        rng.nextBytes(salt);
        rng.nextBytes(iv);
        SecretKey key = derive(passphrase, salt);
        byte[] ct = aesCtr(key, iv, payload, true);
        return ByteBuffer.allocate(salt.length + iv.length + ct.length)
                .put(salt).put(iv).put(ct).array();
    }

    /**
     * 解密：与加密完全相同的路径（AES-CTR 解密 + 同一密钥播种的模型做区间解码）。
     * 不抛异常、不校验 tag；正确密钥还原明文，错误密钥产出看似正常的诱饵。
     */
    public String decrypt(char[] passphrase, byte[] blob) {
        ByteBuffer buf = ByteBuffer.wrap(blob);
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        buf.get(salt);
        buf.get(iv);
        byte[] ct = new byte[buf.remaining()];
        buf.get(ct);

        SecretKey key = derive(passphrase, salt);
        byte[] payload;
        try {
            payload = aesCtr(key, iv, ct, false);
        } catch (RuntimeException e) {
            // 极端情况下底层解密异常也兜底为随机字节，保证解密路径永不抛错（不提供预言机）
            payload = new byte[Math.max(8, ct.length)];
            rng.nextBytes(payload);
        }

        int count;
        if (payload.length < 4) {
            count = 0;
        } else {
            int raw = ByteBuffer.wrap(payload).getInt(0);
            int byBytes = payload.length - 4;
            count = clamp(raw, byBytes);
        }
        byte[] coded = new byte[Math.max(0, payload.length - 4)];
        if (payload.length > 4) {
            System.arraycopy(payload, 4, coded, 0, coded.length);
        }

        model.seed(decoySeed(passphrase)); // 统一用输入密钥播种模型（用户指定）
        int[] symbols = RangeCoder.decode(coded, model, count);
        return fromSymbols(symbols);
    }

    /**
     * 由密文解码出的符号数：既允许正确密钥完整还原任意长明文，又限制错误密钥不会声明超量符号。
     *
     * <p>正确密钥下 {@code raw} 即真实符号数（{@code >=1}），原样保留 → 精确还原。
     * 错误密钥下 {@code raw} 是随机 4 字节，可能为 0 或负数；此时下界取 1，
     * 保证诱饵非空、看似正常文本（空串本身是可被区分的信号）。上界取 {@code byBytes*8+1}：
     * 算术编码每符号至少消耗 1 bit，故 {@code byBytes} 个字节最多承载 {@code 8*byBytes} 个符号，
     * 因此解码数量不会超过密文实际承载的内容，也不会额外放大内存分配。</p>
     */
    private static int clamp(int raw, int byBytes) {
        if (byBytes <= 0) {
            return 0; // 没有任何编码字节，无法解码出符号
        }
        long upper = (long) byBytes * 8 + 1; // 防 int 溢出
        if (upper > Integer.MAX_VALUE) {
            upper = Integer.MAX_VALUE;
        }
        int v = Math.max(1, raw);
        return v > upper ? (int) upper : v;
    }

    private int[] toSymbols(String text) {
        int n = text.length();
        int[] sym = new int[n];
        for (int i = 0; i < n; i++) {
            sym[i] = model.toId(text.charAt(i));
        }
        return sym;
    }

    private String fromSymbols(int[] symbols) {
        StringBuilder sb = new StringBuilder(symbols.length);
        for (int s : symbols) {
            sb.append(model.token(s));
        }
        return sb.toString();
    }

    private SecretKey derive(char[] passphrase, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITER, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] aesCtr(SecretKey key, byte[] iv, byte[] data, boolean encrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    key, new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 由密码确定性派生模型种子：相同密码永远得到相同种子（可否认性前提）。 */
    private static byte[] decoySeed(char[] passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("flora-honey:model-seed:v1:".getBytes(StandardCharsets.UTF_8));
            digest.update(new String(passphrase).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
