package com.flora.root.entropy.mesure;

import java.nio.charset.StandardCharsets;

/**
 * 随机性与熵评估的<b>字符串便捷门面</b>。
 * <p>
 * 本类是面向「字符串输入」的高层便捷入口：所有方法先把字符串按 UTF-8 编码为字节，
 * 再委托给底层注册表与归一化层 {@link EntropyEstimator}。当调用方需要<b>字节级输入</b>、
 * 算法<b>注册 / 扩展</b>，或<b>直接按算法名查询原始度量</b>时，应改用 {@link EntropyEstimator}。
 * 两层职责清晰划分：本类负责「字符串 → 字节」的适配与常用语义封装，
 * {@link EntropyEstimator} 负责算法注册、密度归一化与聚合。</p>
 * <p>提供两类互补度量，用于在未知先验分布时判断一段文本是否「长得像随机密钥」：</p>
 * <ul>
 *   <li><b>香农熵（原始幅度，{@link #shannon}）</b>：每字节不确定度，bit/字节，范围 {@code [0,8]}。</li>
 *   <li><b>归一化密度（{@link #shannonDensity}、{@link #density}）</b>：熵总量除以按字节长度推导的上限
 *       （{@link EntropyEstimator#maxPerByte}），落在 {@code [0,1]}，与长度解耦，
 *       便于跨长度比较「相对自身字母表有多随机」。</li>
 *   <li><b>压缩复杂度比（{@link #complexityRatio}）</b>：Deflate 压缩后长度 / 原长
 *       （Kolmogorov 复杂度的工程近似），{@code [0,1]}；接近 1 不可压缩（高随机），
 *       接近 0 高度重复。<b>注意它是「可压缩度」语义，并非归一化熵密度</b>。</li>
 *   <li><b>聚合密度（{@link #minDensity}）</b>：取所有（或指定）已注册算法密度的<b>最小值</b>，
 *       即「最保守算法」的综合随机性评分。</li>
 * </ul>
 * <p>基础算法（SHANNON、COMPLEXITY_RATIO、BASE16/64/64URL、ENGLISH）由
 * {@link EntropyEstimator} 注册；其中 BASE / ENGLISH 等视角可通过
 * {@link #density(String, String)} 按算法名直接取密度，不必绕到 {@link EntropyEstimator#metric}。</p>
 */
public final class Entropy {

    private Entropy() {
    }

    /**
     * 计算字符串的香农熵（bit/字节，按 UTF-8 编码的字节计）。
     * <p>空串返回 0；单字节重复串返回 0。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 香农熵，单位 bit/字节，始终非负
     */
    public static double shannon(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyEstimator.metric("SHANNON").measure(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的香农熵（bit/字节）。
     * <p>空数组返回 0。字节级输入也可直接使用 {@link EntropyEstimator#metric}。</p>
     *
     * @param data 待评估字节，为 {@code null} 时按空数组处理
     * @return 香农熵，单位 bit/字节，始终非负
     */
    public static double shannon(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        return EntropyEstimator.metric("SHANNON").measure(data);
    }

    /**
     * 计算字符串的<b>归一化香农熵密度</b>（{@code [0,1]}）。
     * <p>等于 {@link #shannon(String)} 除以按字节长度推导的熵上限（{@code log2(min(N,256))}）。
     * 空串、单字节重复串返回 0；分布越均匀越接近 1。原名 {@code normalized} 未标明所针对的
     * 算法，故更名显式标注其针对 SHANNON 算法。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 归一化香农熵密度，范围 {@code [0,1]}
     */
    public static double shannonDensity(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyEstimator.density("SHANNON", s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字符串在指定算法上的<b>归一化熵密度</b>（{@code [0,1]}）。
     * <p>统一入口，便于直接取 BASE16/64/64URL、ENGLISH 等所有已注册算法的密度，
     * 不必绕到 {@link EntropyEstimator#metric}。语义同 {@link EntropyEstimator#density}。</p>
     *
     * @param name 算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}、{@code "BASE64"}、{@code "ENGLISH"}）
     * @param s    待评估字符串，为 {@code null} 或空串返回 0
     * @return 该算法的归一化熵密度，范围 {@code [0,1]}
     * @throws IllegalArgumentException 若算法名未注册
     */
    public static double density(String name, String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyEstimator.density(name, s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字符串的压缩复杂度比（压缩后长度 / 原长度，范围 {@code [0,1]}）。
     * <p>接近 {@code 1} 表示不可压缩（高随机）；接近 {@code 0} 表示高度重复或结构化文本。
     * 注意：对很短的串，压缩头开销会使比值偏高，解释结果时应结合长度。
     * 本方法返回的是「可压缩度」语义，与 {@link #density} 的归一化熵密度不同。</p>
     *
     * @param s 待评估字符串，为 {@code null} 或空串返回 0
     * @return 压缩后长度 / 原长度，通常落在 {@code [0,1]}
     */
    public static double complexityRatio(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return EntropyEstimator.compressionRatio(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字符串在所有（或指定）已注册熵算法上的<b>随机性密度最小值</b>（{@code [0,1]}）。
     * <p>字符串按 UTF-8 编码为字节后参与评估。取最小值即「最保守算法」的判定：
     * 仅当所有参与算法都认为该数据高度随机时结果才高，任何算法认为「不像随机」
     * 都会拉低总分——适合用于综合评估一段文本是否形似随机密钥。</p>
     *
     * @param s          待评估字符串，为 {@code null} 时按空串处理
     * @param algorithms 参与聚合的算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}）；
     *                   为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     */
    public static double minDensity(String s, String... algorithms) {
        return EntropyEstimator.minDensity(s, algorithms);
    }

    /**
     * 计算字节数组在所有（或指定）已注册熵算法上的<b>随机性密度最小值</b>（{@code [0,1]}），
     * 语义同 {@link #minDensity(String, String...)}。字节级输入请直接使用
     * {@link EntropyEstimator#minDensity(byte[], String...)}。
     *
     * @param data       待评估字节数组，为 {@code null} 时按空数组处理
     * @param algorithms 参与聚合的算法名；为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     */
    public static double minDensity(byte[] data, String... algorithms) {
        return EntropyEstimator.minDensity(data, algorithms);
    }
}
