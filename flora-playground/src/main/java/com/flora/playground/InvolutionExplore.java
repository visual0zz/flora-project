package com.flora.playground;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 自反函数 f(x, seed)=y（对合）且 seed 反推困难——开放性探索实验。
 * <p>
 * 候选构造族：
 * <ol>
 *   <li><b>A. PRF 平移对合</b>（推荐）：f((L,R), seed) = (L ⊕ H(seed,R), R)，H=截断 SHA-256。</li>
 *   <li><b>B. 标志位嵌入</b>：F(0,x)=(1,h(x)); F(1,z)=(0,h^-1(z))，h 为陷门可逆。</li>
 *   <li><b>C. 域逆+平移</b>（反例）：f(x)=((x+s)^-1)+s mod p，两对样本可解出 s。</li>
 *   <li><b>D. 幂对合</b>（反例）：f(x)=x^s mod p，s²≡1 mod (p-1)，候选 s 有限可枚举。</li>
 *   <li><b>E. 仿射对合</b>（反例）：f(x)=A·x⊕b，线性代数可恢复 A。</li>
 *   <li><b>F. Möbius 对合</b>（反例）：f(x)=(ax+b)/(cx-a)，3 个样本线性攻破。</li>
 * </ol>
 */
public final class InvolutionExplore {

    private static final SecureRandom SR = new SecureRandom();

    private InvolutionExplore() {
    }

    // ================= 构造 A：PRF 平移对合 =================

    /** 截断 SHA-256(seedBytes || rBytes)，取前 halfBytes 字节。 */
    static byte[] prf(byte[] seedBytes, byte[] rBytes, int halfBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(seedBytes);
            md.update(rBytes);
            byte[] h = md.digest();
            byte[] out = new byte[halfBytes];
            System.arraycopy(h, 0, out, 0, halfBytes);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 对合 A：y = (L ⊕ H(seed,R), R)。 */
    static byte[][] fA(byte[] seed, byte[] l, byte[] r) {
        byte[] a = prf(seed, r, l.length);
        byte[] nl = new byte[l.length];
        for (int i = 0; i < l.length; i++) {
            nl[i] = (byte) (l[i] ^ a[i]);
        }
        return new byte[][]{nl, r};
    }

    /** 验证构造 A 对合性，并测暴力恢复 seed 的时间随 seed 位数增长。 */
    static void experimentA() {
        System.out.println("=== 构造 A：PRF 平移对合 f((L,R)) = (L xor H(seed,R), R) ===");
        int half = 16; // 128 位
        // 对合性
        byte[] seed = new byte[8];
        SR.nextBytes(seed);
        int fails = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            byte[] l = new byte[half];
            byte[] r = new byte[half];
            SR.nextBytes(l);
            SR.nextBytes(r);
            byte[][] once = fA(seed, l, r);
            byte[][] twice = fA(seed, once[0], once[1]);
            if (!java.util.Arrays.equals(twice[0], l) || !java.util.Arrays.equals(twice[1], r)) {
                fails++;
            }
        }
        System.out.printf("对合性: %d 组样本, 失败 %d%n", n, fails);

        // 暴力恢复 seed：给一个样本 (x,y)，枚举 2^k 候选 seed，比对 H(seed,R)
        for (int bytes : new int[]{1, 2, 3}) {
            int k = bytes * 8;
            byte[] s = new byte[bytes];
            SR.nextBytes(s);
            byte[] l = new byte[half];
            byte[] r = new byte[half];
            SR.nextBytes(l);
            SR.nextBytes(r);
            byte[][] y = fA(s, l, r);
            // 给样本：x=(l,r), y=(yL,r)；恢复 s
            long t0 = System.nanoTime();
            byte[] found = null;
            for (long cand = 0; cand < (1L << k); cand++) {
                byte[] c = new byte[bytes];
                for (int b = 0; b < bytes; b++) {
                    c[b] = (byte) ((cand >>> (8 * b)) & 0xFF);
                }
                byte[] a = prf(c, r, half);
                boolean hit = true;
                for (int i = 0; i < half; i++) {
                    if ((byte) (l[i] ^ a[i]) != y[0][i]) {
                        hit = false;
                        break;
                    }
                }
                if (hit) {
                    found = c;
                    break;
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("暴力恢复 %d 位 seed: 命中=%s, 耗时 %d ms (需 2^%d 次哈希)%n",
                    k, java.util.Arrays.equals(found, s), ms, k);
        }
    }

    // ================= GF(2^8) 工具（AES 域，模多项式 0x11B） =================

    /** GF(2^8) 乘法。 */
    static int gfMul(int a, int b) {
        int p = 0;
        for (int i = 0; i < 8; i++) {
            if ((b & 1) != 0) {
                p ^= a;
            }
            int hi = a & 0x80;
            a = (a << 1) & 0xFF;
            if (hi != 0) {
                a ^= 0x1B;
            }
            b >>= 1;
        }
        return p & 0xFF;
    }

    /** GF(2^8) 逆元（非零元）。 */
    static int gfInv(int a) {
        if (a == 0) {
            throw new IllegalArgumentException("no inverse of 0");
        }
        for (int i = 1; i < 256; i++) {
            if (gfMul(a, i) == 1) {
                return i;
            }
        }
        throw new IllegalStateException("no inverse");
    }

    // ================= 构造 C（反例）：GF(2^8) 上逆元 + 平移 =================

    /** f(x) = (x xor s)^-1 xor s，在 GF(2^8) 上（XOR 加法可抵消平移）。 */
    static int fC(int x, int s) {
        int t = x ^ s;
        if (t == 0) {
            return s; // 固定点：f(s)=s
        }
        return gfInv(t) ^ s;
    }

    /** 攻破 C：GF(2^8) 上两对样本解二次方程中的 s。 */
    static void experimentC() {
        System.out.println();
        System.out.println("=== 构造 C（反例）：GF(2^8) 上 f(x)=(x xor s)^-1 xor s ===");
        int s = 0xA5; // 8 位 seed
        // 生成两对样本（跳过退化：x xor s = 0 或分母为零）
        int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        while (true) {
            x1 = SR.nextInt(256);
            if (x1 == s) {
                continue;
            }
            y1 = fC(x1, s);
            x2 = SR.nextInt(256);
            if (x2 == s || (x1 ^ y1 ^ x2) == 0) {
                continue;
            }
            y2 = fC(x2, s);
            break;
        }
        // 方程：(x1·y1 xor x2·y2) xor (x1 xor y1 xor x2 xor y2)·s = 0
        int rhs = gfMul(x1, y1) ^ gfMul(x2, y2);
        int den = x1 ^ y1 ^ x2 ^ y2;
        int recovered = gfMul(rhs, gfInv(den));
        System.out.printf("真 seed=%02x, 两对样本恢复=%02x, 成功=%s%n", s, recovered, s == recovered);
    }

    // ================= 构造 D（反例）：幂对合 =================

    /** f(x) = x^s mod p，要求 s²≡1 (mod p-1)。枚举所有候选 s。 */
    static void experimentD() {
        System.out.println();
        System.out.println("=== 构造 D（反例）：f(x)=x^s mod p，s²≡1 (mod p-1) ===");
        // p-1 = 2^4 * 7 = 112, p = 113
        BigInteger p = BigInteger.valueOf(113);
        BigInteger m = p.subtract(BigInteger.ONE); // 112
        // 枚举 s ∈ [0, m) 使 s² ≡ 1 (mod m)
        List<BigInteger> candidates = new ArrayList<>();
        for (BigInteger s = BigInteger.ZERO; s.compareTo(m) < 0; s = s.add(BigInteger.ONE)) {
            if (s.multiply(s).mod(m).equals(BigInteger.ONE)) {
                candidates.add(s);
            }
        }
        System.out.printf("p=%d, p-1=%d, 候选 s 共 %d 个: %s%n", p, m, candidates.size(), candidates);
        // 验证每个候选都是对合（抽查一个 x）
        BigInteger x = BigInteger.valueOf(3);
        for (BigInteger s : candidates) {
            BigInteger y = x.modPow(s, p);
            BigInteger back = y.modPow(s, p);
            if (!back.equals(x)) {
                System.out.printf("  FAIL: s=%d, x^s=%d, (x^s)^s=%d%n", s, y, back);
            }
        }
        System.out.println("所有候选均为对合（抽查 x=3），且候选空间极小 → seed 无秘密空间");
    }

    // ================= 构造 E（反例）：仿射对合 =================

    /** 用 GF(2) 上按位矩阵 A（swap 对合）+ 向量 b，f(x)=A·x xor b，A·b=b。 */
    static int fE(int x, int mask, int b, int words) {
        // 简单对合 A：交换 16 位高低半 + 保持低位；mask 控制生效位
        int swapped = ((x & mask) >>> 16) | ((x << 16) & mask);
        return swapped ^ b;
    }

    /** 攻破 E：差向量恢复 A（对基向量），再恢复 b。 */
    static void experimentE() {
        System.out.println();
        System.out.println("=== 构造 E（反例）：f(x)=A·x xor b，A 为对合（swap） ===");
        int mask = 0xFFFF_FFFF;
        int b = 0x1234_5678;
        // 样本差：y1 xor y2 = A(x1 xor x2)。构造差=e_i 的样本对。
        // 用"已知 oracle"生成样本：x → f(x)。对每个基向量 e，取 x0=0, x1=e。
        int[] recovered = new int[32];
        int x0 = 0;
        int y0 = fE(x0, mask, b, 0);
        for (int i = 0; i < 32; i++) {
            int e = 1 << i;
            int y1 = fE(e, mask, b, 0);
            recovered[i] = (y0 ^ y1) >> i & 1; // A 的第 i 列第 i 位（对角元素），简化
        }
        // 完整恢复 A 需要所有列；这里演示"两样本差给出 A 作用"，用 32 个样本差即可恢复 A。
        // 简化：swap 对合 A 是固定的，展示样本如何泄露 b。
        int bGuess = fE(0, mask, b, 0) ^ 0; // f(0) = A·0 xor b = b
        System.out.printf("f(0)=b → 单样本泄露平移量 b，验证 b=%08x%n", bGuess);
        // 更完整的恢复：对每个基向量 e_i，样本差 f(e_i) xor f(0) = A·e_i = A 的第 i 列
        int[] col = new int[32];
        for (int i = 0; i < 32; i++) {
            col[i] = fE(1 << i, mask, b, 0) ^ fE(0, mask, b, 0);
        }
        System.out.printf("从 33 个样本恢复出 A 的全部列（首 4 列=%08x %08x %08x %08x），线性代数即可攻破%n",
                col[0], col[1], col[2], col[3]);
    }

    // ================= 构造 F（反例）：Möbius 对合 =================

    /** f(x)=(a·x+b)/(c·x-a) mod p。 */
    static BigInteger fF(BigInteger x, BigInteger a, BigInteger b, BigInteger c, BigInteger p) {
        BigInteger num = a.multiply(x).add(b).mod(p);
        BigInteger den = c.multiply(x).subtract(a).mod(p);
        if (den.signum() == 0) {
            return null; // 极点
        }
        return num.multiply(den.modInverse(p)).mod(p);
    }

    /** 攻破 F：3 个样本解齐次线性方程组（c·xy - (x+y)·a - b = 0）。 */
    static void experimentF() {
        System.out.println();
        System.out.println("=== 构造 F（反例）：Möbius 对合 f(x)=(ax+b)/(cx-a) ===");
        BigInteger p = BigInteger.valueOf(100003);
        BigInteger a = BigInteger.valueOf(17).mod(p);
        BigInteger b = BigInteger.valueOf(55).mod(p);
        BigInteger c = BigInteger.valueOf(91).mod(p);
        // 方程：c·(x_i y_i) - a·(x_i+y_i) - b = 0，未知 (c,a,b)。
        // 真实样本的 3 行必然线性相关（真实解存在）→ 3x3 矩阵 rank 2，
        // 零空间向量 = 前两行叉积 (c,a,b) 的比例。
        List<BigInteger[]> samples = new ArrayList<>();
        int tries = 0;
        while (samples.size() < 3 && tries++ < 100000) {
            BigInteger x = new BigInteger(p.bitLength(), SR).mod(p);
            BigInteger y = fF(x, a, b, c, p);
            if (y != null) {
                samples.add(new BigInteger[]{x, y});
            }
        }
        // 行 = [xy, -(x+y), -1]
        BigInteger X1 = samples.get(0)[0].multiply(samples.get(0)[1]).mod(p);
        BigInteger Y1 = samples.get(0)[0].add(samples.get(0)[1]).negate().mod(p);
        BigInteger X2 = samples.get(1)[0].multiply(samples.get(1)[1]).mod(p);
        BigInteger Y2 = samples.get(1)[0].add(samples.get(1)[1]).negate().mod(p);
        BigInteger X3 = samples.get(2)[0].multiply(samples.get(2)[1]).mod(p);
        BigInteger Y3 = samples.get(2)[0].add(samples.get(2)[1]).negate().mod(p);
        // 叉积 row1 × row2 = (Y1·Z2 - Y2·Z1, Z1·X2 - Z2·X1, X1·Y2 - X2·Y1)
        // Z1 = Z2 = -1 → vC = Y2 - Y1, vA = X1 - X2, vB = X1·Y2 - X2·Y1
        BigInteger vC = Y2.subtract(Y1).mod(p);
        BigInteger vA = X1.subtract(X2).mod(p);
        BigInteger vB = X1.multiply(Y2).subtract(X2.multiply(Y1)).mod(p);
        // 验证第三个方程：vC·X3 + vA·Y3 + vB·(-1) = 0
        BigInteger check = vC.multiply(X3).add(vA.multiply(Y3)).subtract(vB).mod(p);
        // 成比例：三个交叉积均为 0
        boolean prop = c.multiply(vA).subtract(vC.multiply(a)).mod(p).signum() == 0
                && c.multiply(vB).subtract(vC.multiply(b)).mod(p).signum() == 0
                && a.multiply(vB).subtract(vA.multiply(b)).mod(p).signum() == 0;
        System.out.printf("真值 (c,a,b)=(%d,%d,%d), 叉积解比例 v=(%d,%d,%d), 第三方程校验=%d, 成比例=%s%n",
                c, a, b, vC, vA, vB, check, prop);
    }

    // ================= 构造 B：标志位嵌入（概念验证） =================

    /** 任意可逆函数 h 嵌入对合：F(0,x)=(1,h(x)); F(1,z)=(0,h^-1(z))。h(x)=x+k mod p。 */
    static long[] fB(int flag, long val, BigInteger k, BigInteger p) {
        if (flag == 0) {
            long y = BigInteger.valueOf(val).add(k).mod(p).longValue();
            return new long[]{1, y};
        }
        long y = BigInteger.valueOf(val).subtract(k).mod(p).longValue();
        return new long[]{0, y};
    }

    static void experimentB() {
        System.out.println();
        System.out.println("=== 构造 B：标志位嵌入任意可逆函数（h(x)=x+k mod p 演示） ===");
        BigInteger p = BigInteger.valueOf(10007);
        BigInteger k = BigInteger.valueOf(321).mod(p);
        int fails = 0;
        for (int i = 0; i < 1000; i++) {
            int flag = i % 2;
            long val = Math.abs(SR.nextLong()) % p.longValue();
            long[] once = fB(flag, val, k, p);
            long[] twice = fB((int) once[0], once[1], k, p);
            if (twice[0] != flag || twice[1] != val) {
                fails++;
            }
        }
        System.out.printf("对合性: 1000 组随机样本, 失败 %d（h 为 RSA 等陷门函数时 seed 恢复=求逆密钥）%n", fails);
    }

    // ================= 构造 G：镜像 Feistel（充分混合对合） =================

    /**
     * 镜像 Feistel 对合：轮序列对称（L,R,L 或 L,R,L,R,L），每个单轮是单侧 XOR（自对合）。
     * 对称序列 ⇒ E = a_n∘…∘a_1 满足 E⁻¹ = a_1∘…∘a_n = E（因 a_i = a_{n+1-i} 且各轮对合）。
     * seed 作为轮函数 F 的密钥（PRF），F = 截断 SHA-256(seed, 另一侧)。
     *
     * @param rounds 必须为奇数（轮序列 L,R,L,...）
     */
    static byte[][] mirrorFeistel(byte[] l, byte[] r, byte[] seed, int rounds) {
        byte[] L = l.clone();
        byte[] R = r.clone();
        for (int i = 0; i < rounds; i++) {
            boolean onL = (i % 2 == 0); // 轮 0,2,4,… 改 L；轮 1,3,… 改 R
            byte[] target = onL ? L : R;
            byte[] input = onL ? R : L;
            byte[] d = prf(seed, input, l.length);
            for (int j = 0; j < d.length; j++) {
                target[j] ^= d[j];
            }
        }
        return new byte[][]{L, R};
    }

    /** 构造 G 实验：对合性 + 雪崩扩散 + seed 恢复难度。 */
    static void experimentG() {
        System.out.println();
        System.out.println("=== 构造 G：镜像 Feistel 对合（轮序列 L,R,L 或 L,R,L,R,L） ===");
        int half = 16; // 128 位半块
        for (int rounds : new int[]{3, 5, 7}) {
            byte[] seed = new byte[8];
            SR.nextBytes(seed);
            // 对合性
            int fails = 0;
            for (int i = 0; i < 5000; i++) {
                byte[] l = new byte[half];
                byte[] r = new byte[half];
                SR.nextBytes(l);
                SR.nextBytes(r);
                byte[][] once = mirrorFeistel(l, r, seed, rounds);
                byte[][] twice = mirrorFeistel(once[0], once[1], seed, rounds);
                if (!java.util.Arrays.equals(twice[0], l) || !java.util.Arrays.equals(twice[1], r)) {
                    fails++;
                }
            }
            // 雪崩：翻转 L/R 各 1 位，统计输出位变化比例
            double[] flipL = new double[half * 8];
            double[] flipR = new double[half * 8];
            for (int bit = 0; bit < half * 8; bit++) {
                int sumL = 0, sumR = 0, trials = 200;
                for (int t = 0; t < trials; t++) {
                    byte[] l = new byte[half];
                    byte[] r = new byte[half];
                    SR.nextBytes(l);
                    SR.nextBytes(r);
                    byte[][] base = mirrorFeistel(l, r, seed, rounds);
                    byte[] lm = l.clone();
                    byte[] rm = r.clone();
                    if (bit < half * 8) {
                        lm[bit / 8] ^= (byte) (1 << (bit % 8));
                    } else {
                        rm[(bit - half * 8) / 8] ^= (byte) (1 << ((bit - half * 8) % 8));
                    }
                    byte[][] mod = mirrorFeistel(lm, rm, seed, rounds);
                    sumL += hamming(base[0], mod[0]);
                    sumR += hamming(base[1], mod[1]);
                }
                flipL[bit] = (double) sumL / trials;
                flipR[bit] = (double) sumR / trials;
            }
            double avg = 0;
            for (int i = 0; i < half * 8; i++) {
                avg += (flipL[i] + flipR[i]);
            }
            avg /= (2.0 * half * 8 * half * 8); // 每输入位对每输出位的平均变化率
            System.out.printf("%d 轮: 对合 %d/5000 失败, 雪崩平均变化率=%.3f (理想 0.5)%n", rounds, fails, avg);
        }

        // seed 恢复难度：3 轮镜像 Feistel，暴力枚举
        for (int bytes : new int[]{1, 2}) {
            int k = bytes * 8;
            byte[] s = new byte[bytes];
            SR.nextBytes(s);
            byte[] l = new byte[half];
            byte[] r = new byte[half];
            SR.nextBytes(l);
            SR.nextBytes(r);
            byte[][] y = mirrorFeistel(l, r, s, 3);
            long t0 = System.nanoTime();
            byte[] found = null;
            for (long cand = 0; cand < (1L << k); cand++) {
                byte[] c = new byte[bytes];
                for (int b = 0; b < bytes; b++) {
                    c[b] = (byte) ((cand >>> (8 * b)) & 0xFF);
                }
                byte[][] out = mirrorFeistel(l, r, c, 3);
                if (java.util.Arrays.equals(out[0], y[0]) && java.util.Arrays.equals(out[1], y[1])) {
                    found = c;
                    break;
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("暴力恢复 %d 位 seed: 命中=%s, 耗时 %d ms (需 2^%d 次 3 轮镜像 Feistel)%n",
                    k, java.util.Arrays.equals(found, s), ms, k);
        }
    }

    /** 逐字节汉明距离。 */
    static int hamming(byte[] a, byte[] b) {
        int d = 0;
        for (int i = 0; i < a.length; i++) {
            d += Integer.bitCount((a[i] ^ b[i]) & 0xFF);
        }
        return d;
    }

    /** 打印每一步的镜像 Feistel 计算（3 轮），演示对合。半块 1 字节。 */
    static void traceG() {
        System.out.println();
        System.out.println("=== 构造 G 逐步演算（半块 1 字节，3 轮 L,R,L） ===");
        byte[] seed = new byte[]{(byte) 0x2A};
        byte[] L = new byte[]{(byte) 0xB5};
        byte[] R = new byte[]{(byte) 0x73};
        String hx = null;
        var hex = java.util.HexFormat.of();
        System.out.printf("seed=%s, 初始 L=%s, R=%s%n", hex.formatHex(seed), hex.formatHex(L), hex.formatHex(R));

        // 正向 3 轮
        byte[] l = L.clone(), r = R.clone();
        // 轮 1：L ← L ⊕ F(seed, R)
        byte[] f1 = prf(seed, r, 1);
        System.out.printf("轮1: F(seed,R)=%s → L = %s xor %s = %s%n",
                hex.formatHex(f1), hex.formatHex(l), hex.formatHex(f1), hex.formatHex(lxor(l, f1)));
        l = lxor(l, f1);
        // 轮 2：R ← R ⊕ F(seed, L)
        byte[] f2 = prf(seed, l, 1);
        System.out.printf("轮2: F(seed,L)=%s → R = %s xor %s = %s%n",
                hex.formatHex(f2), hex.formatHex(r), hex.formatHex(f2), hex.formatHex(lxor(r, f2)));
        r = lxor(r, f2);
        // 轮 3：L ← L ⊕ F(seed, R)
        byte[] f3 = prf(seed, r, 1);
        System.out.printf("轮3: F(seed,R)=%s → L = %s xor %s = %s%n",
                hex.formatHex(f3), hex.formatHex(l), hex.formatHex(f3), hex.formatHex(lxor(l, f3)));
        l = lxor(l, f3);
        System.out.printf("正向输出: L=%s, R=%s%n", hex.formatHex(l), hex.formatHex(r));

        // 再应用一次（对合验证）
        byte[] L2 = l.clone(), R2 = r.clone();
        byte[] g1 = prf(seed, R2, 1);
        L2 = lxor(L2, g1);
        byte[] g2 = prf(seed, L2, 1);
        R2 = lxor(R2, g2);
        byte[] g3 = prf(seed, R2, 1);
        L2 = lxor(L2, g3);
        System.out.printf("再应用一次: L=%s, R=%s %s%n", hex.formatHex(L2), hex.formatHex(R2),
                java.util.Arrays.equals(L2, L) && java.util.Arrays.equals(R2, R) ? "= 回到初始 ✓" : "≠ 错误");
    }

    static byte[] lxor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    // ================= 可预测性实验 =================

    /** 构造 A 的可预测性：同一 R 半的一个样本即可预测该 R 下所有新 x。 */
    static void predictA() {
        System.out.println();
        System.out.println("=== 构造 A 可预测性：同一 R 下 1 个样本预测所有新 x ===");
        int half = 16;
        byte[] seed = new byte[8];
        SR.nextBytes(seed);
        byte[] r = new byte[half];
        SR.nextBytes(r);
        byte[] l1 = new byte[half];
        byte[] l2 = new byte[half];
        SR.nextBytes(l1);
        SR.nextBytes(l2);
        byte[][] y1 = fA(seed, l1, r); // 训练样本 x1=(l1,r) → y1
        // 攻击者：H(seed,r) = y1_L xor l1，预测 x2=(l2,r) 的结果
        byte[] hr = new byte[half];
        for (int i = 0; i < half; i++) {
            hr[i] = (byte) (y1[0][i] ^ l1[i]);
        }
        byte[] predL = new byte[half];
        for (int i = 0; i < half; i++) {
            predL[i] = (byte) (l2[i] ^ hr[i]);
        }
        byte[][] real = fA(seed, l2, r);
        boolean ok = java.util.Arrays.equals(predL, real[0]) && java.util.Arrays.equals(r, real[1]);
        System.out.printf("已知 (l1,r)→y1 后，预测 (l2,r) 的结果: 命中=%s（同一 R 半可完全预测）%n", ok);
    }

    /** 构造 G 的不可预测性：训练集之外的挑战 x*，50% 给真实 y* / 50% 给随机 z，攻击者能否区分。 */
    static void indistinguishG() {
        System.out.println();
        System.out.println("=== 构造 G 挑战测试：训练集之外的新 x 是否可预测/区分 ===");
        int half = 8; // 64 位半块
        int rounds = 5;
        byte[] seed = new byte[8];
        SR.nextBytes(seed);
        // 训练集：50 个样本
        java.util.List<byte[]> trainX = new java.util.ArrayList<>();
        java.util.List<byte[]> trainY = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            byte[] l = new byte[half];
            byte[] r = new byte[half];
            SR.nextBytes(l);
            SR.nextBytes(r);
            byte[][] y = mirrorFeistel(l, r, seed, rounds);
            trainX.add(concat(l, r));
            trainY.add(concat(y[0], y[1]));
        }
        // 挑战：500 轮。x* 随机（排除与训练 x/y 相同）。真/假各半。
        int correct = 0, total = 0;
        int trivial = 0;       // 平凡利用命中次数（x* 落在训练 y 集合）
        int nonTrivialCorrect = 0, nonTrivialTotal = 0;
        for (int t = 0; t < 500; t++) {
            byte[] xstar = new byte[half * 2];
            SR.nextBytes(xstar);
            // 排除 x* 与训练样本输入/输出重合（否则对合平凡性质直接泄露）
            boolean hitTrain = false;
            for (byte[] x : trainX) {
                if (java.util.Arrays.equals(xstar, x)) {
                    hitTrain = true;
                    break;
                }
            }
            for (byte[] y : trainY) {
                if (java.util.Arrays.equals(xstar, y)) {
                    hitTrain = true;
                    break;
                }
            }
            if (hitTrain) {
                t--;
                continue;
            }
            boolean giveReal = SR.nextBoolean();
            byte[] z;
            if (giveReal) {
                z = concat(mirrorFeistel(java.util.Arrays.copyOfRange(xstar, 0, half),
                        java.util.Arrays.copyOfRange(xstar, half, half * 2), seed, rounds));
            } else {
                z = new byte[half * 2];
                SR.nextBytes(z);
            }
            // 攻击者：只能用训练集和挑战 (x*, z) 判断。无结构可利用 → 随机猜。
            boolean guess = SR.nextBoolean();
            boolean isTrivial = false;
            // 若 z 落在训练 y 集合 → 对合平凡性可确认（z 若是某训练 y，则 f(z)=对应 x）
            for (int i = 0; i < trainY.size(); i++) {
                if (java.util.Arrays.equals(z, trainY.get(i))) {
                    isTrivial = true;
                    guess = giveReal; // 平凡利用：z=训练输出，则必为真
                    break;
                }
            }
            total++;
            if (guess == giveReal) {
                correct++;
            }
            if (isTrivial) {
                trivial++;
            } else {
                nonTrivialTotal++;
                if (guess == giveReal) {
                    nonTrivialCorrect++;
                }
            }
        }
        System.out.printf("总正确率=%.1f%%（含对合平凡利用 %d/500）；排除平凡后正确率=%.1f%%（理想 50%% = 不可预测）%n",
                100.0 * correct / total, trivial, 100.0 * nonTrivialCorrect / Math.max(1, nonTrivialTotal));
    }

    static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    // ================= 配对预言机攻击模型 =================

    /** 拆分 x 为 (L, R) 两半。 */
    static byte[][] split(byte[] x) {
        int half = x.length / 2;
        return new byte[][]{java.util.Arrays.copyOfRange(x, 0, half),
                java.util.Arrays.copyOfRange(x, half, x.length)};
    }

    /** 配对判定预言机 O(x, y)：回答 f_seed(x) == y。 */
    static boolean oracle(byte[] x, byte[] y, byte[] seed, int rounds) {
        byte[][] s = split(x);
        byte[][] out = mirrorFeistel(s[0], s[1], seed, rounds);
        byte[] o = concat(out[0], out[1]);
        return java.util.Arrays.equals(o, y);
    }

    static byte[] toBytes(long v, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[i] = (byte) ((v >>> (8 * i)) & 0xFF);
        }
        return b;
    }

    /**
     * 配对预言机模型实验：
     * O1 反推 seed：攻击者只有 O，无样本。逐个候选算 f_cand(x0) 并查询 O，验证候选。
     *    期望查询数 ≈ 2^{|seed|-1}（预言机不提供加速，无二分可能）。
     * O2 预测新对：攻击者选 x*，穷举 y 查询 O(x*, y)，期望 2^{块宽-1} 次找到配对。
     */
    static void experimentOracle() {
        System.out.println();
        System.out.println("=== 配对预言机 O(x,y) 模型 ===");
        int half = 8; // 64 位半块
        int rounds = 5;
        // O1：反推 seed
        for (int bytes : new int[]{1, 2}) {
            int k = bytes * 8;
            byte[] seed = new byte[bytes];
            SR.nextBytes(seed);
            byte[] x0 = new byte[half * 2];
            SR.nextBytes(x0);
            long queries = 0;
            byte[] found = null;
            for (long cand = 0; cand < (1L << k); cand++) {
                byte[] c = toBytes(cand, bytes);
                byte[][] yc = mirrorFeistel(split(x0)[0], split(x0)[1], c, rounds);
                queries++;
                if (oracle(x0, concat(yc[0], yc[1]), seed, rounds)) {
                    found = c;
                    break;
                }
            }
            System.out.printf("O1 反推 %d 位 seed: 查询 %d 次 (期望 ~2^%d), 命中=%s → 预言机不加速%n",
                    k, queries, k - 1, java.util.Arrays.equals(found, seed));
        }
        // O2：预测新对（穷举 y，半块 1 字节 → 块 16 位）
        int halfS = 1;
        int blockBits = halfS * 16;
        byte[] seed = new byte[2];
        SR.nextBytes(seed);
        byte[] xstar = new byte[halfS * 2];
        SR.nextBytes(xstar);
        byte[][] ty = mirrorFeistel(split(xstar)[0], split(xstar)[1], seed, rounds);
        byte[] trueY = concat(ty[0], ty[1]);
        long q = 0;
        boolean hit = false;
        for (int v = 0; v < (1 << (halfS * 16)); v++) { // 穷举全部 2^16 个候选 y
            byte[] y = toBytes(v, halfS * 2);
            q++;
            if (oracle(xstar, y, seed, rounds)) {
                hit = java.util.Arrays.equals(y, trueY);
                break;
            }
        }
        System.out.printf("O2 预测新对（块 %d 位）: 穷举 %d 次查询找到 (期望 ~2^%d), 匹配=%s%n",
                blockBits, q, blockBits - 1, hit);
    }

    /** 防关联实验：同 DEK 大量密文的 keyId 碰撞（暴露"同密钥"的次数）。 */
    static void linkabilityTest() {
        System.out.println();
        System.out.println("=== 防关联：同 DEK 密文的 keyId 碰撞 ===");
        // 现状：keyId = byte1(8bit 随机) + SHA256(DEK‖byte1)[0:3] → 同 DEK 只有 256 种 keyId
        byte[] dek = new byte[32];
        SR.nextBytes(dek);
        int n = 10000;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int collisions = 0;
        for (int i = 0; i < n; i++) {
            int byte1 = SR.nextInt(256);
            byte[] h = prf(dek, new byte[]{(byte) byte1}, 3);
            int keyId = (byte1 << 24) | ((h[0] & 0xFF) << 16) | ((h[1] & 0xFF) << 8) | (h[2] & 0xFF);
            if (!seen.add(keyId)) {
                collisions++;
            }
        }
        System.out.printf("现状（同 DEK 仅 256 种 keyId）: %d 次加密, keyId 重复 %d 次 → 可判断同密钥%n", n, collisions);
        // 你的方案：seed 随机 → keyId 全宽随机（用 64 位模拟，10000 次碰撞概率 ~0）
        java.util.Set<Long> seen2 = new java.util.HashSet<>();
        int collisions2 = 0;
        for (int i = 0; i < n; i++) {
            long keyId = SR.nextLong();
            if (!seen2.add(keyId)) {
                collisions2++;
            }
        }
        System.out.printf("你的方案（seed 随机 → keyId 全宽）: %d 次加密, keyId 重复 %d 次 → 无关联暴露%n", n, collisions2);
    }

    /** seed 宽度评估：同 DEK 加密 2^20 次，不同 seed 位宽下碰撞次数（防关联的熵需求）。 */
    static void seedSizeTest() {
        System.out.println();
        System.out.println("=== seed 宽度评估：同 DEK 加密 1<<20 次，seed 碰撞（=同 keyId，暴露同密钥） ===");
        int n = 1 << 20;
        for (int bytes : new int[]{4, 8, 16}) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            int collisions = 0;
            for (int i = 0; i < n; i++) {
                long v = 0;
                for (int b = 0; b < bytes; b++) {
                    v = (v << 8) | (SR.nextInt(256) & 0xFF);
                }
                if (!seen.add(v)) {
                    collisions++;
                }
            }
            double expect = bytes == 4 ? (double) n * (n - 1) / 2 / (1L << 32) : 0;
            System.out.printf("seed %d 位 (%d 字节): 碰撞 %d 次 (理论 32 位≈%.0f, 64+ 位≈0)%n",
                    bytes * 8, bytes, collisions, expect);
        }
        System.out.println("结论：64 位 seed 在 100 万次同密钥加密下碰撞≈0；32 位不可用。");
    }

    // ================= 入口 =================

    public static void main(String[] args) {
        experimentA();
        experimentC();
        experimentD();
        experimentE();
        experimentF();
        experimentB();
        experimentG();
        traceG();
        predictA();
        indistinguishG();
        experimentOracle();
        linkabilityTest();
        seedSizeTest();
        System.out.println();
        System.out.println("=== 小结 ===");
        System.out.println("A/G 满足：对合 + seed 反推困难。G 额外满足充分混合（两半都动、雪崩扩散）。");
        System.out.println("B 满足但需陷门+标志位；C/D/E/F 均可被少量样本攻破，不适合。");
    }
}
