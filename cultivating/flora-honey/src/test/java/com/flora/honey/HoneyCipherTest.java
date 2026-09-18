package com.flora.honey;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蜜糖加密核心行为测试：正确密钥精确还原，错误密钥产出确定性诱饵且不抛异常、无预言机；
 * 并验证阶-N 上下文模型确实带来压缩收益。
 */
class HoneyCipherTest {

    private static TokenModel model() {
        return new NgramTokenModel(); // 阶-3 上下文模型
    }

    @Test
    void correctKeyRecoversPlaintextExactly() {
        HoneyCipher cipher = new HoneyCipher(model());
        String secret = "服务器 10.0.0.7 root:T%9xPq!2mK 周五前交付";
        char[] pass = "correct-horse".toCharArray();

        byte[] blob = cipher.encrypt(pass, secret);
        assertNotNull(blob);

        assertEquals(secret, cipher.decrypt(pass, blob), "正确密钥必须精确还原真实明文");
    }

    @Test
    void longerTextRoundTripsExactly() {
        HoneyCipher cipher = new HoneyCipher(model());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("普通中文与 English 混合文本，第 ").append(i).append(" 段。");
        }
        String secret = sb.toString();
        byte[] blob = cipher.encrypt("key".toCharArray(), secret);
        assertEquals(secret, cipher.decrypt("key".toCharArray(), blob));
    }

    @Test
    void contextImprovesCompressionOnRepetitiveText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("the quick brown fox jumps over the lazy dog. 敏捷的棕色狐狸跳过懒狗。");
        }
        String text = sb.toString();

        int order0 = codedSize(text, 0);
        int order3 = codedSize(text, 3);

        assertTrue(order3 < order0,
                "阶-3 上下文模型的编码体积应小于阶-0，实际 order0=" + order0 + " order3=" + order3);
    }

    @Test
    void higherOrderRoundTripsExactly() {
        // 上下文窗口越长越可能出现边界情况，逐一验证可精确往返
        String text = "abcabcabcabc 重复模式 repeated patterns 0123456789 中文上下文窗口测试";
        for (int order = 0; order <= 3; order++) {
            TokenModel m = new NgramTokenModel(order);
            m.seed("order-seed".getBytes(StandardCharsets.UTF_8));
            int[] sym = new int[text.length()];
            for (int i = 0; i < text.length(); i++) {
                sym[i] = m.toId(text.charAt(i));
            }
            byte[] coded = RangeCoder.encode(sym, m);
            m.seed("order-seed".getBytes(StandardCharsets.UTF_8)); // 重置模型后再解码（模拟两端独立）
            int[] back = RangeCoder.decode(coded, m, sym.length);
            StringBuilder out = new StringBuilder();
            for (int s : back) {
                out.append(m.token(s));
            }
            assertEquals(text, out.toString(), "order=" + order + " 应精确往返");
        }
    }

    @Test
    void wrongKeyYieldsTextWithoutThrowing() {
        HoneyCipher cipher = new HoneyCipher(model());
        String secret = "服务器 10.0.0.7 root:T%9xPq!2mK";
        byte[] blob = cipher.encrypt("right-key".toCharArray(), secret);

        String decoy = cipher.decrypt("totally-wrong".toCharArray(), blob); // 不应抛异常

        assertNotNull(decoy);
        assertFalse(decoy.isEmpty(), "错误密钥也应产出非空诱饵（空串是可被区分的信号）");
        assertFalse(decoy.equals(secret), "诱饵不应泄露真实明文");
    }

    @Test
    void sameWrongKeyIsDeterministic() {
        HoneyCipher cipher = new HoneyCipher(model());
        byte[] blob = cipher.encrypt("right-key".toCharArray(), "任意内容用于演示");

        String first = cipher.decrypt("same-wrong".toCharArray(), blob);
        String second = cipher.decrypt("same-wrong".toCharArray(), blob);

        assertEquals(first, second, "同一错误密钥必须产出完全相同的诱饵（可否认性前提）");
    }

    @Test
    void allKeysTakeUniformPathAndYieldText() {
        // 关键性质：无论密钥对错，decrypt 都不校验 tag、不抛异常，给出文本
        HoneyCipher cipher = new HoneyCipher(model());
        byte[] blob = cipher.encrypt("right-key".toCharArray(), "敏感内容");
        String real = cipher.decrypt("right-key".toCharArray(), blob);
        String decoy = cipher.decrypt("totally-wrong".toCharArray(), blob);
        assertFalse(real.isEmpty());
        assertFalse(decoy.isEmpty());
    }

    private static int codedSize(String text, int order) {
        TokenModel m = new NgramTokenModel(order);
        m.seed("compression-demo".getBytes(StandardCharsets.UTF_8));
        int[] sym = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            sym[i] = m.toId(text.charAt(i));
        }
        return RangeCoder.encode(sym, m).length;
    }
}
