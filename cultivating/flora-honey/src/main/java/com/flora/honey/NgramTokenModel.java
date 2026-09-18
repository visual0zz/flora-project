package com.flora.honey;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 阶-N n-gram 上下文模型（字符级），作为蜜糖加密中“LLM 预测”的自包含占位实现。
 *
 * <p>词表覆盖整个 BMP（id == Unicode 码元，0..65535），任意文本可无损编码。分布由
 * <b>密钥播种的回退先验</b>与<b>上下文条件估计</b>做插值（Dirichlet 平滑）：</p>
 *
 * <pre>
 *   freq(id) = base[id] + BOOST * baseTotal * count(ctx, id) / observed(ctx)
 * </pre>
 *
 * <ul>
 *   <li>{@code base[id]}：密钥播种的回退先验（空格/标点略加权，叠加由 {@code seed()} 从输入密钥
 *       确定性派生的 0..3 噪声），恒 &ge; 1。保证任意 id 频率都 &ge; 1，也保证不同密钥得到不同分布。</li>
 *   <li>{@code count(ctx, id)}：上下文 {@code ctx}（最近 {@code order} 个 token）后继 {@code id} 的计数；
 *       {@code observed(ctx)} 为其总计数。归一化后其总质量恰为 {@code BOOST * baseTotal}。</li>
 * </ul>
 *
 * <p>因此上下文与先验的相对权重只由 {@link #BOOST} 决定，且<b>总频率有界</b>
 * （约为 {@code baseTotal * (1 + BOOST)}），不随文本长度增长 → 不会导致算术编码的
 * {@code range*total} 溢出。</p>
 *
 * <p><b>对称推进：</b>编码器每编完一个 token、解码器每解出一个 token 都调用
 * {@link #observe(int)}：先清空当前上下文对 {@code delta} 的贡献，再累加到持久表
 * {@code ctxCounts}，然后滑入新上下文并按新上下文的归一化计数重建 {@code delta}。
 * 正确密钥下两端观察到的 token 序列相同 → 状态逐步一致 → 精确往返；错误密钥下窗口分叉，
 * 但仍是自洽诱饵，且永不抛异常。</p>
 *
 * <p>{@code order = 0} 退化为自适应 unigram（无上下文，仅全局计数），可作为压缩基线对照。</p>
 */
public final class NgramTokenModel implements TokenModel {

    private static final int VOCAB = 65536;
    /** 上下文估计相对于回退先验的权重（先验权重为 1）。 */
    private static final int BOOST = 15;
    /** 上下文表条目上限，超出则确定性清空，避免内存无界增长。 */
    private static final int MAX_CTX = 1 << 18;

    private final int order;
    private final long ctxMask;
    private final int[] base = new int[VOCAB];
    /** 当前上下文的归一化贡献，直接叠加到 base 上（绝大多数为 0）。 */
    private final int[] delta = new int[VOCAB];
    /** 上下文 key → (后继 token → 计数)，跨上下文持久保存。 */
    private final Map<Long, Map<Integer, Integer>> ctxCounts = new HashMap<>();

    private int baseTotal;
    private Map<Integer, Integer> curCounts; // == ctxCounts.get(ctxKey)，可为 null 表示空
    private long ctxKey;

    public NgramTokenModel() {
        this(3);
    }

    /**
     * @param order 上下文窗口长度（最近多少个 token 参与条件化），取值 0..3。
     *              order=0 表示无上下文（自适应 unigram）。
     */
    public NgramTokenModel(int order) {
        if (order < 0 || order > 3) {
            throw new IllegalArgumentException("order must be in [0, 3], got " + order);
        }
        this.order = order;
        this.ctxMask = order == 0 ? 0L : (1L << (16 * order)) - 1;
    }

    @Override
    public int vocabSize() {
        return VOCAB;
    }

    @Override
    public void seed(byte[] keySeed) {
        long h = 1469598103934665603L; // FNV-1a 偏移基数
        for (byte b : keySeed) {
            h = (h ^ (b & 0xFF)) * 1099511628211L;
        }
        Random rnd = new Random(h);
        int sum = 0;
        for (int i = 0; i < VOCAB; i++) {
            int b = (i == ' ' || i == '\n' || i == '\t') ? 16 : (isPunct(i) ? 8 : 1);
            base[i] = b + rnd.nextInt(4); // 叠加 0..3 的确定性噪声，保证 > 0
            delta[i] = 0;
            sum += base[i];
        }
        baseTotal = sum;
        ctxCounts.clear();
        curCounts = null;
        ctxKey = 0;
    }

    @Override
    public int freq(int id) {
        return base[id] + delta[id];
    }

    @Override
    public void observe(int id) {
        // 1) 清空当前上下文对 delta 的贡献（delta 只存当前上下文的贡献，置 0 即精确移除）
        if (curCounts != null) {
            for (Integer key : curCounts.keySet()) {
                delta[key] = 0;
            }
        }
        // 2) 有界化：上下文表超限则确定性清空（编/解码在正确密钥下同时触发）
        if (ctxCounts.size() >= MAX_CTX) {
            ctxCounts.clear();
            curCounts = null;
        }
        // 3) 累加当前上下文的持久计数
        curCounts = ctxCounts.computeIfAbsent(ctxKey, k -> new HashMap<>());
        curCounts.merge(id, 1, Integer::sum);
        // 4) 滑入新上下文
        ctxKey = order == 0 ? 0L : ((ctxKey << 16) | (id & 0xFFFF)) & ctxMask;
        // 5) 按新上下文的归一化计数重建 delta
        curCounts = ctxCounts.get(ctxKey);
        if (curCounts != null) {
            int observed = 0;
            for (int c : curCounts.values()) {
                observed += c;
            }
            for (Map.Entry<Integer, Integer> e : curCounts.entrySet()) {
                delta[e.getKey()] = (int) ((long) BOOST * baseTotal * e.getValue() / observed);
            }
        }
    }

    @Override
    public char token(int id) {
        return (char) id;
    }

    @Override
    public int toId(char c) {
        return c & 0xFFFF;
    }

    private static boolean isPunct(int i) {
        return i == '，' || i == '。' || i == '、' || i == '：' || i == '；'
                || i == '！' || i == '？' || i == '.' || i == ',' || i == ';'
                || i == ':' || i == '!' || i == '?';
    }
}
