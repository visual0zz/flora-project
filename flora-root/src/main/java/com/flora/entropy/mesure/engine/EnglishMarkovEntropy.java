package com.flora.entropy.mesure.engine;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.entropy.mesure.EntropyMetric;
import com.flora.entropy.mesure.EntropyMetricAlgorithmFactoryRegister;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一阶英文马尔可夫模型：用「前一个字节 → 下一个字节」的条件分布估算与英文的交叉熵。
 * <p>参数为写死的英文统计近似（英文字母频率、词首/词尾字母分布、常见 bigram 表），
 * 无需训练。对输入字节序列累计 {@code -log2 P(next | prev)} 并取每字节平均：
 * 像英文句子的串交叉熵明显偏低（英文一阶交叉熵约 4~5 bit），
 * 随机串 / base64 / hex 等几乎不满足英文转移 → 交叉熵接近满熵 8。</p>
 *
 * <p>该度量是<b>与英文的交叉熵</b>（英文相似度），与香农熵同为每字节熵（bit/字节），
 * 统一参与 {@code minDensity} 聚合。由于交叉熵不小于香农熵（信息论性质），
 * 在取最小值的聚合中通常不由它决定结果，但作为英文相似度视角可按需单独查询。</p>
 */
public final class EnglishMarkovEntropy implements EntropyMetric {

    private static final String NAME = "ENGLISH";
    private static final Set<String> SUPPORTED = Set.of(NAME);

    /** 英文字母频率（a-z，近似，构建时归一化）。 */
    private static final double[] LETTER_FREQ = {
            0.0817, 0.0149, 0.0278, 0.0425, 0.1270, 0.0223, 0.0202, 0.0609, 0.0697,
            0.0015, 0.0077, 0.0403, 0.0241, 0.0675, 0.0751, 0.0193, 0.0010, 0.0599,
            0.0633, 0.0906, 0.0276, 0.0098, 0.0236, 0.0015, 0.0197, 0.0007};

    /** 词首（空格后）字母分布，近似。 */
    private static final double[] WORD_START = {
            0.120, 0.058, 0.045, 0.040, 0.025, 0.030, 0.012, 0.032, 0.100,
            0.002, 0.005, 0.020, 0.050, 0.010, 0.070, 0.022, 0.001, 0.018,
            0.090, 0.145, 0.008, 0.004, 0.075, 0.002, 0.006, 0.001};

    /** 词尾（空格前）字母分布，近似。 */
    private static final double[] WORD_END = {
            0.008, 0.008, 0.006, 0.090, 0.190, 0.015, 0.030, 0.006, 0.005,
            0.001, 0.020, 0.040, 0.025, 0.080, 0.020, 0.010, 0.001, 0.060,
            0.120, 0.140, 0.004, 0.003, 0.005, 0.012, 0.050, 0.002};

    /** 常见英文 bigram（一阶转移中给增强权重）。 */
    private static final Set<String> HIGH_BIGRAMS = Arrays.stream((
            "th he in er an re on at en nd ti es or te of ed is it al ar st to nt "
                    + "ng se ha as ou io le ve co me de hi ri ro ic ne ev im ra ma ce "
                    + "ch ll be ea si om ac ad eg").split(" "))
            .collect(Collectors.toSet());

    private static final double MIN_PROB = 1e-6;
    private static final double BIGRAM_BOOST = 4.0;
    private static final double BASE_PROB = 1.0 / 1024.0;

    /** 一阶转移概率表 {@code P(next | prev)}，每行已归一化。 */
    private static final double[][] TRANS = buildTransitions();
    /** 首字符先验（句子/片段开头）。 */
    private static final double[] FIRST = buildFirst();

    @Override
    public String getAlgorithmName() {
        return NAME;
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
        double total = 0.0;
        int prev = -1;
        for (byte b : data) {
            int v = b & 0xFF;
            double p = prev < 0 ? FIRST[v] : TRANS[prev][v];
            if (p <= 0) {
                p = MIN_PROB;
            }
            total -= Math.log(p) / Math.log(2);
            prev = v;
        }
        return Math.min(total / data.length, 8.0);
    }

    /** 构建一阶转移表：字母行（bigram 增强 + 词尾空格）、空格/标点行（词首字母），逐行归一化。 */
    private static double[][] buildTransitions() {
        double[][] t = new double[256][256];
        for (double[] row : t) {
            Arrays.fill(row, BASE_PROB);
        }
        int space = ' ';
        // 字母行：next 按字母频率（高频 bigram 增强），next=空格 按词尾分布
        for (int c = 0; c < 26; c++) {
            int lower = 'a' + c;
            int upper = 'A' + c;
            for (int n = 0; n < 26; n++) {
                double p = LETTER_FREQ[n];
                if (isHighBigram(c, n)) {
                    p *= BIGRAM_BOOST;
                }
                for (int pv : new int[]{lower, upper}) {
                    t[pv]['a' + n] = p;
                    t[pv]['A' + n] = p;
                }
            }
            t[lower][space] = WORD_END[c];
            t[upper][space] = WORD_END[c];
        }
        // 空格行 / 标点行：next 按词首字母分布
        for (char p : " \n\t.,;:!?\"'".toCharArray()) {
            for (int n = 0; n < 26; n++) {
                t[p]['a' + n] = WORD_START[n];
                t[p]['A' + n] = WORD_START[n];
            }
        }
        // 逐行归一化
        for (int i = 0; i < 256; i++) {
            double sum = 0.0;
            for (double v : t[i]) {
                sum += v;
            }
            if (sum > 0) {
                for (int j = 0; j < 256; j++) {
                    t[i][j] /= sum;
                }
            }
        }
        return t;
    }

    /** 首字符先验：字母按词首分布，其余按均匀底线。 */
    private static double[] buildFirst() {
        double[] first = new double[256];
        Arrays.fill(first, BASE_PROB);
        for (int n = 0; n < 26; n++) {
            first['a' + n] = WORD_START[n];
            first['A' + n] = WORD_START[n];
        }
        return first;
    }

    /** 常见 bigram 命中判断（按小写字母下标）。 */
    private static boolean isHighBigram(int prev, int next) {
        return HIGH_BIGRAMS.contains("" + (char) ('a' + prev) + (char) ('a' + next));
    }

    @Override
    public AlgorithmFactory<? extends EntropyMetric> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<EntropyMetric> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return EntropyMetricAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return SUPPORTED;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public EntropyMetric construct(String algorithmName, AlgorithmComponent... components) {
            return new EnglishMarkovEntropy();
        }
    };
}
