package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * base N 编码字符熵度量（统一实现 BASE16/64/64URL，最常见的密钥编码形态）。
 * <p>每种 base 编码只用固定的 {@code N} 个符号，每个字符至多携带 {@code log2(N)} bit 信息；
 * 本算法逐字节分类：
 * <ul>
 *   <li>落在该 base 字符集内的字节 → 按 {@code N} 符号的实际分布估算香农熵（上限 {@code log2(N)} bit/字符）；</li>
 *   <li>填充符（如 base64 的 {@code =}）→ 视为 0 信息；</li>
 *   <li>超出字符集的字节 → 视为满熵（8 bit/字节），保守假设其可能是任意密钥材料。</li>
 * </ul>
 * 返回每字节熵（bit/字节，范围 {@code [0,8]}），使对应 base 形态的数据按其真实信息容量
 * 参与密度归一化，避免长编码串因字符重复少而熵虚高。</p>
 *
 * <p>base16 大小写不敏感（hex 的 {@code a-f} 与 {@code A-F} 映射同一符号）；
 * base64/base64url 大小写敏感。</p>
 */
public final class BaseAlphabetEntropy implements EntropyMetric {

    /** 各 base 编码规格：字符集 + 是否大小写不敏感 + 填充符（{@code 0} 表示无填充）。 */
    private static final Map<String, Spec> SPECS = Map.of(
            "BASE16",
            new Spec("0123456789ABCDEF", true, '\0'),
            "BASE64",
            new Spec("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", false, '='),
            "BASE64URL",
            new Spec("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_", false, '='));

    private static final Set<String> SUPPORTED = SPECS.keySet();

    private final String name;
    private final int base;
    /** 字符索引表：{@code 0..base-1} 为符号索引，{@code -2} 为填充符，{@code -1} 为字符集外。 */
    private final int[] table = new int[256];

    private BaseAlphabetEntropy(String name, String alphabet, boolean caseInsensitive, char pad) {
        this.name = name;
        this.base = alphabet.length();
        Arrays.fill(table, -1);
        if (pad != 0) {
            table[pad & 0xFF] = -2;
        }
        for (int i = 0; i < alphabet.length(); i++) {
            char c = alphabet.charAt(i);
            table[c & 0xFF] = i;
            if (caseInsensitive) {
                if (c >= 'A' && c <= 'Z') {
                    table[(c + 32) & 0xFF] = i;   // 大写 → 小写
                } else if (c >= 'a' && c <= 'z') {
                    table[(c - 32) & 0xFF] = i;   // 小写 → 大写
                }
            }
        }
    }

    /** 按算法名创建对应 base 编码的实例。 */
    public static BaseAlphabetEntropy instance(String name) {
        Spec spec = SPECS.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("未定义的 base 编码算法: " + name);
        }
        return new BaseAlphabetEntropy(name, spec.alphabet(), spec.caseInsensitive(), spec.pad());
    }

    @Override
    public String getAlgorithmName() {
        return name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public double measure(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        int[] freq = new int[base];
        int symbolCount = 0;
        int otherCount = 0;
        for (byte b : data) {
            int idx = table[b & 0xFF];
            if (idx >= 0) {
                freq[idx]++;
                symbolCount++;
            } else if (idx != -2) {
                // 超出字符集 → 满熵
                otherCount++;
            }
            // idx == -2（填充符）：0 信息，不计入任何熵
        }
        double h = 0.0;
        if (symbolCount > 0) {
            for (int c : freq) {
                if (c == 0) {
                    continue;
                }
                double p = (double) c / symbolCount;
                h -= p * (Math.log(p) / Math.log(2));
            }
        }
        // 每字节熵 = (符号熵贡献 + 非字符集满熵) / 总长度
        return (h * symbolCount + 8.0 * otherCount) / data.length;
    }

    private record Spec(String alphabet, boolean caseInsensitive, char pad) {
    }
}
