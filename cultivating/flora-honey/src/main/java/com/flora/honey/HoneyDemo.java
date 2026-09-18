package com.flora.honey;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 蜜糖加密演示入口。
 *
 * <p>展示核心性质：用正确密码解密得到真实明文；用任意错误密码解密不会报错，
 * 而是得到一段看起来正常的诱饵文本，且不同错误密码得到不同诱饵、同一错误密码始终一致。
 * 关键在于：正确与错误密钥走的是<b>同一条</b>解密路径（AES-CTR 解密 +
 * 密钥播种的模型做区间解码），且密文<b>不带 tag</b>，因此不提供任何可校验正确性的预言机。</p>
 *
 * <p>末尾演示上下文窗口带来的压缩收益：同一段重复文本，在阶-3 模型下编码体积小于阶-0 模型。</p>
 */
public final class HoneyDemo {

    public static void main(String[] args) {
        TokenModel model = new NgramTokenModel(); // 阶-3 上下文模型
        HoneyCipher cipher = new HoneyCipher(model);

        String secret = "真实的秘密：服务器 10.0.0.7，root 密码为 T%9xPq!2mK，周五前交付";
        char[] correct = "correct-horse-battery-staple".toCharArray();

        byte[] blob = cipher.encrypt(correct, secret);
        System.out.println("=== 加密完成，blob(base64) ===");
        System.out.println(Base64.getEncoder().encodeToString(blob));
        System.out.println();

        // 1) 正确密码 -> 真实明文（同一解密路径，只是模型被正确密钥播种）
        String ok = cipher.decrypt(correct, blob);
        System.out.println("[正确密码] 解密结果：");
        System.out.println(ok);
        System.out.println("是否等于原文：" + secret.equals(ok));
        System.out.println();

        // 2) 错误密码 -> 诱饵（不报错，看起来正常；模型被错误密钥播种 + 输入流为随机字节）
        char[] wrong1 = "wrong-password-123".toCharArray();
        String decoy1 = cipher.decrypt(wrong1, blob);
        System.out.println("[错误密码 wrong-password-123] 解密结果（诱饵）：");
        System.out.println(decoy1);
        System.out.println("是否泄露真实明文：" + secret.equals(decoy1));
        System.out.println();

        // 3) 另一个错误密码 -> 不同诱饵
        char[] wrong2 = "hunter2".toCharArray();
        String decoy2 = cipher.decrypt(wrong2, blob);
        System.out.println("[错误密码 hunter2] 解密结果（诱饵）：");
        System.out.println(decoy2);
        System.out.println("与上一诱饵是否相同（应不同）：" + !decoy1.equals(decoy2));
        System.out.println();

        // 4) 同一错误密码两次 -> 必须一致（可否认性前提）
        String decoy1Again = cipher.decrypt(wrong1, blob);
        System.out.println("[错误密码 wrong-password-123 再次解密]：");
        System.out.println(decoy1Again);
        System.out.println("与首次是否一致（应一致）：" + decoy1.equals(decoy1Again));
        System.out.println();

        // 5) 上下文窗口的压缩收益：同一段重复文本，阶-3 编码体积小于阶-0
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("the quick brown fox jumps over the lazy dog. 敏捷的棕色狐狸跳过懒狗。");
        }
        String repetitive = sb.toString();
        int order0 = codedSize(repetitive, 0);
        int order3 = codedSize(repetitive, 3);
        System.out.println("=== 上下文窗口的压缩收益（同一段 " + repetitive.length() + " 字符重复文本）===");
        System.out.println("阶-0（无上下文）编码字节：" + order0);
        System.out.println("阶-3（上下文窗口）编码字节：" + order3);
        System.out.println("阶-3 更小：" + (order3 < order0));

        blacken(correct);
        blacken(wrong1);
        blacken(wrong2);
    }

    /** 用指定阶数的模型对文本做区间编码，返回编码字节数（同包可直接调用包级私有 RangeCoder）。 */
    private static int codedSize(String text, int order) {
        TokenModel m = new NgramTokenModel(order);
        m.seed("compression-demo".getBytes(StandardCharsets.UTF_8));
        int[] sym = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            sym[i] = m.toId(text.charAt(i));
        }
        return RangeCoder.encode(sym, m).length;
    }

    private static void blacken(char[] chars) {
        for (int i = 0; i < chars.length; i++) {
            chars[i] = '\0';
        }
    }
}
