package com.flora.entropy.mesure;

import java.nio.charset.StandardCharsets;

/**
 * 随机性与熵的评估工具门面类。
 * <p>
 * 提供两类互补度量，用于在不知道先验分布的前提下判断一段字符串是否"长得像随机密钥"：
 * <ul>
 *   <li><b>香农熵（Shannon entropy）</b>：衡量字符/字节分布的不确定度，单位为 bit/符号；
 *       越接近均匀分布，熵越高。</li>
 *   <li><b>归一化熵（密度）</b>：香农熵除以按输入字节长度推导的熵上限（见
 *       {@link EntropyProvider#maxPerByte}），落在 {@code [0,1]}，与长度解耦，
 *       使不同长度的串可直接比较"相对于自身字母表有多随机"。</li>
 *   <li><b>压缩复杂度比</b>：用 Deflate 压缩后的长度与原长的比值（Kolmogorov 复杂度的
 *       工程近似）；接近 {@code 1} 表示不可压缩（高随机），接近 {@code 0} 表示高度重复。</li>
 *   <li><b>聚合密度</b>：{@link #minDensity} 取所有已注册算法密度的最小值，
 *       即"最保守算法"的综合随机性评分。</li>
 * </ul>
 * 算法层统一以 {@code byte[]} 输入、输出熵总量，密度归一化与聚合由 {@link EntropyProvider}
 * 完成；字符串输入在门面层按 UTF-8 编码后参与评估。
 * </p>
 */
public final class Entropy {

    private Entropy() {
    }

    /**
     * 计算字符串的香农熵（bit/字节，按 UTF-8 编码的字节计）。
     * <p>空串返回 0；单字节重复串（无论重复与否）返回 0。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 香农熵，单位 bit/字节，始终非负
     */
    public static double shannon(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyProvider.metric("SHANNON").measure(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的香农熵（bit/字节）。
     * <p>空数组返回 0。</p>
     *
     * @param data 待评估字节，为 {@code null} 时按空数组处理
     * @return 香农熵，单位 bit/字节，始终非负
     */
    public static double shannon(byte[] data) {
        return EntropyProvider.metric("SHANNON").measure(data);
    }

    /**
     * 计算字符串的归一化香农熵（密度），落在 {@code [0,1]}。
     * <p>等于 {@link #shannon(String)} 除以按字节长度推导的熵上限（{@code log2(min(N,256))}）。
     * 空串、单字节重复串返回 0；分布越均匀越接近 1。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 归一化熵，范围 {@code [0,1]}
     */
    public static double normalized(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyProvider.density("SHANNON", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字符串的压缩复杂度比（压缩后长度 / 原长度）。
     * <p>接近 {@code 1} 表示不可压缩（高随机）；接近 {@code 0} 表示高度重复或结构化文本。
     * 注意：对很短的串，压缩头开销会使比值偏高，解释结果时应结合长度。</p>
     *
     * @param s 待评估字符串，为 {@code null} 或空串返回 0
     * @return 压缩后长度 / 原长度，通常落在 {@code [0,1]}
     */
    public static double complexityRatio(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        // 算法输出每字节熵近似（min(ratio,1)*8），此处还原为比值
        return EntropyProvider.metric("COMPLEXITY_RATIO")
                .measure(s.getBytes(StandardCharsets.UTF_8)) / 8.0;
    }

    /**
     * 计算字符串在所有（或指定）已注册熵算法上的<b>随机性密度最小值</b>（{@code [0,1]}）。
     * <p>聚合各算法的密度评分（归一化香农熵、压缩不可压缩度等），
     * 取最小值即"最保守算法"的判定：仅当所有参与算法都认为该串高度随机时结果才高，
     * 任何算法认为"不像随机"都会拉低总分——适合用于综合评估一段文本是否形似随机密钥。</p>
     *
     * @param s          待评估字符串，为 {@code null} 时按空串处理
     * @param algorithms 参与聚合的算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}）；
     *                   为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     */
    public static double minDensity(String s, String... algorithms) {
        return EntropyProvider.minDensity(s, algorithms);
    }

    /**
     * 计算字节数组在所有（或指定）已注册熵算法上的<b>随机性密度最小值</b>（{@code [0,1]}），
     * 语义同 {@link #minDensity(String, String...)}。
     *
     * @param data       待评估字节数组，为 {@code null} 时按空数组处理
     * @param algorithms 参与聚合的算法名；为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     */
    public static double minDensity(byte[] data, String... algorithms) {
        return EntropyProvider.minDensity(data, algorithms);
    }
}
