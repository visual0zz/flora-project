package com.flora.entropy.mesure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Entropy} 的度量契约测试：香农熵、归一化香农熵密度、按算法名取密度、
 * 压缩复杂度比、聚合 minDensity。
 */
class EntropyTest {

    @Test
    void shannonOfEmptyAndUniform() {
        assertEquals(0.0, Entropy.shannon(""), 1e-9);
        assertEquals(0.0, Entropy.shannon("aaaa"), 1e-9);
        // 16 个互异小写字母：均匀分布，香农熵 = log2(16) = 4 bit/字符
        assertEquals(4.0, Entropy.shannon("abcdefghijklmnop"), 1e-9);
    }

    @Test
    void shannonByteUniform() {
        byte[] uniform = new byte[256];
        for (int i = 0; i < 256; i++) {
            uniform[i] = (byte) i;
        }
        // 256 个互异字节：熵 = log2(256) = 8 bit/字节
        assertEquals(8.0, Entropy.shannon(uniform), 1e-9);
        assertEquals(0.0, Entropy.shannon(new byte[0]), 1e-9);
    }

    @Test
    void shannonDensityRangesAndUniform() {
        assertEquals(0.0, Entropy.shannonDensity(""), 1e-9);
        assertEquals(0.0, Entropy.shannonDensity("aaaa"), 1e-9);
        // 互异字母：归一化后应为 1.0（相对自身字母表完全随机）
        assertEquals(1.0, Entropy.shannonDensity("abcdefghijklmnop"), 1e-9);
        // 随机且混合的串归一化熵接近上限，且不超过 1
        double r = Entropy.shannonDensity("aB3kF9xQ2mNpLr7tVcWzQeXyZ");
        assertTrue(r > 0.8 && r <= 1.0, "随机串归一化熵应接近 1: " + r);
    }

    @Test
    void densityByAlgorithmNameReachesAllRegistered() {
        // density(name, s) 是统一入口：SHANNON 视图应与 shannonDensity 一致
        String s = "abcdefghijklmnop";
        assertEquals(Entropy.shannonDensity(s), Entropy.density("SHANNON", s), 1e-9);
        // BASE64 视图：64 个互异 base64 符号 → 每字符满熵 → 密度 1.0
        String b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        assertEquals(1.0, Entropy.density("BASE64", b64), 1e-9);
        // ENGLISH 视图：density(name, s) 统一入口可达，返回落在 [0,1]。
        // 注：短串的 density 会因 maxPerByte(n)=log2(n) 趋于饱和而钳到 1.0，
        // ENGLISH 对短串的区分能力体现在原始交叉熵（见 englishMarkovCrossEntropyDistinguishesTextFromRandom）。
        double englishDensity = Entropy.density("ENGLISH", "the quick brown fox jumps over the lazy dog");
        assertTrue(englishDensity >= 0.0 && englishDensity <= 1.0, "ENGLISH 密度应在 [0,1]: " + englishDensity);
        // 未注册算法应抛异常
        assertThrows(IllegalArgumentException.class, () -> Entropy.density("BASE32", s));
    }

    @Test
    void complexityRatioDistinguishesRandomFromRepetitive() {
        // 高随机串基本不可压缩，比值接近 1
        double random = Entropy.complexityRatio("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789");
        assertTrue(random > 0.8, "随机串压缩比应接近 1: " + random);
        // 高度重复的串可压缩，比值明显偏低
        double repetitive = Entropy.complexityRatio("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(repetitive < 0.5, "重复串压缩比应偏低: " + repetitive);
    }

    @Test
    void maxPerByteIsDerivedFromByteLength() {
        // 熵上限按输入字节长度推导：log2(min(N,256))
        assertEquals(0.0, EntropyEstimator.maxPerByte(1), 1e-9);
        assertEquals(4.0, EntropyEstimator.maxPerByte(16), 1e-9);
        assertEquals(8.0, EntropyEstimator.maxPerByte(256), 1e-9);
        assertEquals(8.0, EntropyEstimator.maxPerByte(1024), 1e-9);
    }

    @Test
    void densityIsMeasureDividedByByteLengthUpperBound() {
        // SHANNON 密度 = 每字节香农熵 / log2(min(N,256))
        byte[] uniform16 = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8);
        assertEquals(1.0, EntropyEstimator.density("SHANNON", uniform16), 1e-9);
        assertEquals(0.0, EntropyEstimator.density("SHANNON", "aaaa".getBytes(StandardCharsets.UTF_8)), 1e-9);
        assertEquals(0.0, EntropyEstimator.density("SHANNON", new byte[0]), 1e-9);
        // 随机混合串密度接近 1 且不超过 1
        double r = EntropyEstimator.density("SHANNON",
                "aB3kF9xQ2mNpLr7tVcWzQeXyZ".getBytes(StandardCharsets.UTF_8));
        assertTrue(r > 0.8 && r <= 1.0, "随机串密度应接近 1: " + r);
    }

    @Test
    void minDensityAggregatesConservatively() {
        // 随机混合串：各算法都高，min 保持高
        double randomMin = Entropy.minDensity("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789");
        assertTrue(randomMin > 0.6, "随机串 minDensity 应偏高: " + randomMin);
        // 重复串：香农密度极低，min 被拉低
        double repetitiveMin = Entropy.minDensity("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(repetitiveMin < 0.2, "重复串 minDensity 应偏低: " + repetitiveMin);
        assertEquals(0.0, Entropy.minDensity(""), 1e-9);
    }

    @Test
    void minDensitySupportsAlgorithmSubset() {
        // 指定算法子集时只在该子集内取最小值
        assertEquals(1.0, Entropy.minDensity("abcdefghijklmnopqrstuvwxyz", "SHANNON"), 1e-9);
        assertTrue(Entropy.minDensity("abcdefghijklmnopqrstuvwxyz", "COMPLEXITY_RATIO") > 0.5,
                "字母表压缩密度应较高");
        // 指定未注册算法应抛异常
        assertThrows(IllegalArgumentException.class,
                () -> Entropy.minDensity("abc", "NOT_REGISTERED"));
    }

    @Test
    void minDensityByteArray() {
        byte[] random = new byte[256];
        for (int i = 0; i < 256; i++) {
            random[i] = (byte) i;
        }
        assertTrue(Entropy.minDensity(random) > 0.8, "随机字节 minDensity 应偏高");
        assertTrue(Entropy.minDensity(new byte[100]) < 0.1, "重复字节 minDensity 应偏低");
        assertEquals(0.0, Entropy.minDensity(new byte[0]), 1e-9);
    }

    @Test
    void base64EntropyMeasuresByBase64Alphabet() {
        // 64 个 base64 符号各一次：每字符熵 = log2(64) = 6
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        assertEquals(6.0, EntropyEstimator.metric("BASE64")
                .measure(alphabet.getBytes(StandardCharsets.UTF_8)), 1e-9);
        // 单符号重复 → 0
        assertEquals(0.0, EntropyEstimator.metric("BASE64")
                .measure("aaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8)), 1e-9);
        // 超出 base64 字符集的字节 → 满熵 8 bit/字节
        byte[] raw = new byte[16];
        Arrays.fill(raw, (byte) 0xFF);
        assertEquals(8.0, EntropyEstimator.metric("BASE64").measure(raw), 1e-9);
        // 填充符 '=' 不携带信息：16 个互异符号 + 2 个 '=' → 每字节熵 = (log2(16)*16)/18
        double withPad = EntropyEstimator.metric("BASE64")
                .measure("abcdefghijklmnop==".getBytes(StandardCharsets.UTF_8));
        assertEquals((4.0 * 16) / 18, withPad, 1e-9);
    }

    @Test
    void baseAlphabetEntropyVariants() {
        // BASE16（hex）：16 符号均匀 → 每字符 4 bit；大小写不敏感
        assertEquals(4.0, EntropyEstimator.metric("BASE16")
                .measure("0123456789ABCDEF".getBytes(StandardCharsets.UTF_8)), 1e-9);
        assertEquals(4.0, EntropyEstimator.metric("BASE16")
                .measure("0123456789abcdef".getBytes(StandardCharsets.UTF_8)), 1e-9);
        // BASE64URL 用 -_ 替代 +/：64 符号均匀 → 6 bit
        assertEquals(6.0, EntropyEstimator.metric("BASE64URL").measure(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                        .getBytes(StandardCharsets.UTF_8)), 1e-9);
        // 单个非字符集字节 → 满熵 8
        assertEquals(8.0, EntropyEstimator.metric("BASE16").measure(new byte[]{'!'}), 1e-9);
        // 单符号重复 → 0
        assertEquals(0.0, EntropyEstimator.metric("BASE64")
                .measure("aaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8)), 1e-9);
        // 已精简掉的 base 编码（如 BASE32）不再注册，应抛异常
        assertThrows(IllegalArgumentException.class, () -> EntropyEstimator.metric("BASE32"));
    }

    @Test
    void englishMarkovCrossEntropyDistinguishesTextFromRandom() {
        // 一阶英文马尔可夫：像英文句子的串交叉熵明显偏低
        double text = EntropyEstimator.metric("ENGLISH")
                .measure("the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertTrue(text < 6.5, "英文句子交叉熵应偏低: " + text);
        double text2 = EntropyEstimator.metric("ENGLISH")
                .measure("hello world this is a test".getBytes(StandardCharsets.UTF_8));
        assertTrue(text2 < 6.0, "英文句子交叉熵应偏低: " + text2);
        // 随机串（不满足英文转移）→ 接近满熵
        double random = EntropyEstimator.metric("ENGLISH")
                .measure("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789".getBytes(StandardCharsets.UTF_8));
        assertTrue(random > 7.0, "随机串交叉熵应接近满熵: " + random);
        assertEquals(0.0, EntropyEstimator.metric("ENGLISH").measure(new byte[0]), 1e-9);
    }

    @Test
    void defaultAggregationUsesCoreAlgorithms() {
        // 默认聚合只含核心算法 SHANNON + COMPLEXITY_RATIO，base/英文视角按需显式指定
        double textMin = Entropy.minDensity("the quick brown fox jumps over the lazy dog");
        assertTrue(textMin > 0.3 && textMin <= 1.0, "英文句子聚合密度应在合理范围: " + textMin);
        double randomMin = Entropy.minDensity("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789");
        assertTrue(randomMin > textMin, "随机串聚合密度应高于英文句子: " + randomMin);
        // 显式指定 base / 英文视角可用
        assertTrue(Entropy.minDensity("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789", "BASE64") > 0);
        assertTrue(Entropy.minDensity("the quick brown fox", "ENGLISH") >= 0);
    }
}
