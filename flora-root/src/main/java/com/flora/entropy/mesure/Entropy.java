package com.flora.entropy.mesure;

/**
 * 随机性与熵的评估工具门面类。
 * <p>
 * 提供两类互补度量，用于在不知道先验分布的前提下判断一段字符串是否"长得像随机密钥"：
 * <ul>
 *   <li><b>香农熵（Shannon entropy）</b>：衡量字符/字节分布的不确定度，单位为 bit/符号；
 *       越接近均匀分布，熵越高。</li>
 *   <li><b>归一化熵</b>：香农熵除以该串自身字母表宽度的上限，落在 {@code [0,1]}，
 *       使不同长度的串可直接比较"相对于自身字母表有多随机"。</li>
 *   <li><b>字符类别数</b>：串中出现的字符大类（小写字母 / 大写字母 / 数字 / 符号）种类数，
 *       作为"混合程度"的廉价代理指标——纯 hex 哈希只有 2 类，而 base64 风格密钥通常有 3 类以上。</li>
 *   <li><b>压缩复杂度比</b>：用 Deflate 压缩后的长度与原长的比值（Kolmogorov 复杂度的工程近似）；
 *       接近 1 表示不可压缩（高随机），接近 0 表示高度重复/结构化。</li>
 * </ul>
 * 本类为无状态纯函数工具，所有方法委托给 {@link EntropyProvider} 注册的算法实现。
 * </p>
 */
public final class Entropy {

    private Entropy() {
    }

    /**
     * 计算字符串的香农熵（bit/字符，按码点计）。
     * <p>空串返回 0；单字符串（无论重复与否）返回 0。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 香农熵，单位 bit/符号，始终非负
     */
    public static double shannon(String s) {
        return EntropyProvider.metric("SHANNON").measure(s);
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
     * 计算字符串的归一化香农熵，落在 {@code [0,1]}。
     * <p>等于 {@link #shannon(String)} 除以 {@code log2(实际出现的不同码点数)}。
     * 空串、单码点串返回 0；分布越均匀越接近 1。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时按空串处理
     * @return 归一化熵，范围 {@code [0,1]}
     */
    public static double normalized(String s) {
        return EntropyProvider.metric("NORMALIZED").measure(s);
    }

    /**
     * 统计字符串中出现的字符大类数量（小写 / 大写 / 数字 / 符号）。
     * <p>空白字符与控制字符不计入任何类别；返回值在 {@code [0,4]}。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时返回 0
     * @return 出现的字符大类种数，范围 {@code [0,4]}
     */
    public static int characterClasses(String s) {
        return (int) EntropyProvider.metric("CHARACTER_CLASSES").measure(s);
    }

    /**
     * 统计字符串中出现的字母/数字类别数（小写 / 大写 / 数字），范围 {@code [0,3]}。
     * <p>刻意忽略符号、分隔符（{@code - _ / + = .} 等）、空白与控制字符，
     * 因为这些在日期、路径、ID 等非密钥串里极常见，不应算作"多样性"。
     * 该方法用于区分"大小写混合且含数字"的随机密钥（如 base64 风格，通常 3 类）
     * 与"单大小写 + 数字"的结构化串（如 hex 哈希、UUID、时间戳，通常 2 类）。</p>
     *
     * @param s 待评估字符串，为 {@code null} 时返回 0
     * @return 出现的字母/数字类别种数，范围 {@code [0,3]}
     */
    public static int alnumClasses(String s) {
        return (int) EntropyProvider.metric("ALNUM_CLASSES").measure(s);
    }

    /**
     * 计算字符串的压缩复杂度比（Kolmogorov 复杂度的工程近似）。
     * <p>以 Deflate 最高压缩级别压缩 UTF-8 字节，返回压缩后长度与原长的比值。
     * 接近 {@code 1} 表示不可压缩（高随机）；接近 {@code 0} 表示高度重复或结构化文本。
     * 注意：对很短的串，压缩头开销会使比值偏高，解释结果时应结合长度。</p>
     *
     * @param s 待评估字符串，为 {@code null} 或空串返回 0
     * @return 压缩后长度 / 原长度，通常落在 {@code [0,~1.1]}
     */
    public static double complexityRatio(String s) {
        return EntropyProvider.metric("COMPLEXITY_RATIO").measure(s);
    }
}
