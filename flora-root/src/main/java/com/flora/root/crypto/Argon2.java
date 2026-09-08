package com.flora.root.crypto;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Argon2 口令派生函数（RFC 9106）自研实现，纯 Java，无第三方依赖。
 * <p>支持 Argon2d / Argon2i / Argon2id 三种类型（v1.3，version 0x13）。结构：H0 由参数域经
 * BLAKE2b-512 派生；内存按 lane/slice 填充，块间经压缩函数 {@code G}（BLAKE2b 轮 + trunc 乘法）混合；
 * 引用索引按类型（数据无关/相关/混合）寻址；最终对末列 XOR 再 BLAKE2b 出标签。</p>
 *
 * <p>作为 KDBX / 1Password 等外部保险库格式的密钥派生底层实现。</p>
 */
public final class Argon2 {

    private static final int BLOCK_SIZE = 1024;
    private static final int SYNC_POINTS = 4;
    private static final int VERSION = 0x13;

    /** 并行门槛：总块数低于此值时 fork/join 与屏障开销盖过并行收益，回退串行。 */
    private static final int PARALLEL_MIN_BLOCKS = 4096;
    /** 并行门槛：单个任务（lane 段）的块数下限，低于此值任务过碎。 */
    private static final int PARALLEL_MIN_SEGMENT = 128;

    private Argon2() {
    }

    /** Argon2 类型：0=Argon2d，1=Argon2i，2=Argon2id。 */
    public static final int TYPE_D = 0;
    public static final int TYPE_I = 1;
    public static final int TYPE_ID = 2;

    /**
     * Argon2id 摘要（等价于 {@code digest(TYPE_ID, ...)}）。
     *
     * @param password    密码字节
     * @param salt        盐
     * @param memoryKiB   内存 KiB（须不小于 8 * parallelism）
     * @param iterations  迭代次数
     * @param parallelism 并行度（lane 数）
     * @param outLen      输出长度
     */
    public static byte[] digest(byte[] password, byte[] salt, int memoryKiB, int iterations, int parallelism, int outLen) {
        return digest(TYPE_ID, password, salt, memoryKiB, iterations, parallelism, outLen);
    }

    /**
     * Argon2 摘要（指定类型 d/i/id，secret 与关联数据留空）。
     *
     * @param type        {@link #TYPE_D}/{@link #TYPE_I}/{@link #TYPE_ID}
     * @param password    密码字节（KDBX 场景下为复合主密钥）
     * @param salt        盐
     * @param memoryKiB   内存 KiB（须不小于 parallelism）
     * @param iterations  迭代次数
     * @param parallelism 并行度（lane 数）
     * @param outLen      输出长度
     */
    public static byte[] digest(int type, byte[] password, byte[] salt, int memoryKiB, int iterations, int parallelism, int outLen) {
        return digest(type, password, salt, null, null, memoryKiB, iterations, parallelism, outLen);
    }

    /**
     * Argon2 摘要（指定类型 d/i/id，含可选 secret 与关联数据）。
     *
     * @param type        {@link #TYPE_D}/{@link #TYPE_I}/{@link #TYPE_ID}
     * @param password    密码字节（KDBX 场景下为复合主密钥）
     * @param salt        盐
     * @param secret      密钥（可为空）
     * @param ad          关联数据（可为空）
     * @param memoryKiB   内存 KiB（须不小于 parallelism）
     * @param iterations  迭代次数
     * @param parallelism 并行度（lane 数）
     * @param outLen      输出长度
     */
    public static byte[] digest(int type, byte[] password, byte[] salt, byte[] secret, byte[] ad,
            int memoryKiB, int iterations, int parallelism, int outLen) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("t 须为正");
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("p 须为正");
        }
        // 参考实现仅做算法可行性下限校验；低于 8p 的安全下限仍可被参考向量使用。
        if (memoryKiB < parallelism) {
            throw new IllegalArgumentException("m 须不小于 p");
        }
        if (outLen <= 0) {
            throw new IllegalArgumentException("outLen 须为正");
        }
        int lanes = parallelism;
        int mPrime = 4 * lanes * (memoryKiB / (4 * lanes)); // 块数（1 KiB/块）
        int laneLength = mPrime / lanes;
        int segmentLength = laneLength / SYNC_POINTS;

        byte[] h0 = initialHash(type, password, salt, secret, ad, memoryKiB, iterations, parallelism, outLen);
        // 所有块预分配为零字节数组：退化参数（referenceAreaSize=0）下可能引用尚未填充的块，
        // 与参考实现一致，未初始化块按全零参与计算。
        byte[][] blocks = new byte[mPrime][];
        for (int i = 0; i < mPrime; i++) {
            blocks[i] = new byte[BLOCK_SIZE];
        }
        for (int i = 0; i < lanes; i++) {
            blocks[i * laneLength] = variableHash(concat(h0, le32(0), le32(i)), BLOCK_SIZE);
            blocks[i * laneLength + 1] = variableHash(concat(h0, le32(1), le32(i)), BLOCK_SIZE);
        }

        FillMemory fill = new FillMemory(blocks, type, iterations, mPrime, lanes, laneLength, segmentLength);
        if (useParallel(lanes, mPrime, segmentLength)) {
            PoolHolder.POOL.invoke(fill);
        } else {
            fill.computeSequentially();
        }

        byte[] c = new byte[BLOCK_SIZE];
        for (int lane = 0; lane < lanes; lane++) {
            byte[] last = blocks[lane * laneLength + laneLength - 1];
            for (int i = 0; i < BLOCK_SIZE; i++) {
                c[i] ^= last[i];
            }
        }
        // 参考实现 finalize 用 H'（blake2b_long），非直接 H
        return variableHash(c, outLen);
    }

    /** 数据无关寻址：id 仅前两个 slice，i 全程，d 全程数据相关。 */
    private static boolean useDataIndependent(int pass, int slice, int type) {
        if (type == TYPE_I) {
            return true;
        }
        if (type == TYPE_D) {
            return false;
        }
        return pass == 0 && slice < 2; // Argon2id：前两个 slice 数据无关
    }

    // ===== 内存填充 =====

    /** 是否启用按 lane 并行：多 lane、多核且工作量足够大时才有收益。 */
    private static boolean useParallel(int lanes, int mPrime, int segmentLength) {
        return lanes >= 2
                && Runtime.getRuntime().availableProcessors() >= 2
                && mPrime >= PARALLEL_MIN_BLOCKS
                && segmentLength >= PARALLEL_MIN_SEGMENT;
    }

    /**
     * 一次完整内存填充（全部 pass × slice × lane），串行与并行共用 {@link #fillSegment}。
     *
     * <p>并行依据：同一 (pass, slice) 内各 lane 之间没有数据依赖——跨 lane 引用只指向已完成
     * 的 slice 或上一 pass 的残留值（见 {@link #indexAlpha} 的引用区间），故可按 lane 并行、
     * 在每个 slice 结束后屏障。反之，同一 lane 的 segment 必须按 j 递增顺序由同一线程计算
     * （同 lane 的引用域含 {@code within-1}，会延伸到本 slice 已算前缀），因此任务只能按
     * lane 划分，绝不按 j 拆分。</p>
     */
    private static final class FillMemory extends RecursiveAction {

        private static final long serialVersionUID = 1L;

        private final byte[][] blocks;
        private final int type;
        private final int iterations;
        private final int mPrime;
        private final int lanes;
        private final int laneLength;
        private final int segmentLength;

        FillMemory(byte[][] blocks, int type, int iterations, int mPrime,
                int lanes, int laneLength, int segmentLength) {
            this.blocks = blocks;
            this.type = type;
            this.iterations = iterations;
            this.mPrime = mPrime;
            this.lanes = lanes;
            this.laneLength = laneLength;
            this.segmentLength = segmentLength;
        }

        /** 并行执行：每个 slice 内把 lane 连续分组提交，{@code invokeAll} 即该 slice 的屏障。 */
        @Override
        protected void compute() {
            int groups = Math.min(lanes, PoolHolder.POOL.getParallelism());
            ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[groups];
            for (int pass = 0; pass < iterations; pass++) {
                for (int slice = 0; slice < SYNC_POINTS; slice++) {
                    int start = segmentStart(pass, slice);
                    int end = (slice + 1) * segmentLength;
                    for (int g = 0; g < groups; g++) {
                        int from = g * lanes / groups;
                        int to = (g + 1) * lanes / groups;
                        tasks[g] = new LaneGroupTask(this, pass, slice, start, end, from, to);
                    }
                    // fork/join 提供 happens-before：本 slice 各 lane 的写入对下一 slice 可见
                    ForkJoinTask.invokeAll(tasks);
                }
            }
        }

        /** 串行执行：小参数、单 lane 或单核时走此路径，与并行路径逐字节一致。 */
        void computeSequentially() {
            for (int pass = 0; pass < iterations; pass++) {
                for (int slice = 0; slice < SYNC_POINTS; slice++) {
                    int start = segmentStart(pass, slice);
                    int end = (slice + 1) * segmentLength;
                    for (int lane = 0; lane < lanes; lane++) {
                        fillSegment(pass, slice, lane, start, end);
                    }
                }
            }
        }

        private int segmentStart(int pass, int slice) {
            return (pass == 0 && slice == 0) ? 2 : slice * segmentLength;
        }

        /** 填充一条 lane 在单个 (pass, slice) 内的 segment，必须按 j 递增顺序计算。 */
        void fillSegment(int pass, int slice, int lane, int start, int end) {
            // 字段先落到局部变量：内层循环每块都要用，避免反复读字段（串行路径为此前热路径）
            final byte[][] b = blocks;
            final int tp = type;
            final int iters = iterations;
            final int mp = mPrime;
            final int lns = lanes;
            final int laneLen = laneLength;
            final int segLen = segmentLength;
            for (int j = start; j < end; j++) {
                int cur = lane * laneLen + j;
                // 块在 segment 内的相对索引（pass0 slice0 从 2 起，其余从 0 起）
                int within = j - slice * segLen;

                // prev 块：lane 第一块（j=0）时环绕到 lane 末尾
                int prevOffset = (cur % laneLen == 0) ? cur + laneLen - 1 : cur - 1;

                long j1;
                long j2;
                if (useDataIndependent(pass, slice, tp)) {
                    long value = independentValue(tp, pass, lane, slice, mp, iters, within);
                    j1 = value & 0xffffffffL;
                    j2 = value >>> 32;
                } else {
                    long prev = readLong(b[prevOffset], 0);
                    j1 = prev & 0xffffffffL;
                    j2 = (prev >>> 32) & 0xffffffffL;
                }

                int refLane = (int) (j2 % lns);
                if (pass == 0 && slice == 0) {
                    refLane = lane;
                }
                int refIndex = indexAlpha(pass, slice, lane, refLane, j1, within, segLen, laneLen);
                // pass>0 时按 Argon2 v1.3 需与旧块 XOR
                b[cur] = fillBlock(b[prevOffset], b[refLane * laneLen + refIndex], b[cur], pass != 0);
            }
        }
    }

    /** 一组连续 lane 在单个 (pass, slice) 内的填充任务（lane 不跨组）。 */
    private static final class LaneGroupTask extends RecursiveAction {

        private static final long serialVersionUID = 1L;

        private final FillMemory owner;
        private final int pass;
        private final int slice;
        private final int start;
        private final int end;
        private final int laneFrom;
        private final int laneTo;

        LaneGroupTask(FillMemory owner, int pass, int slice, int start, int end, int laneFrom, int laneTo) {
            this.owner = owner;
            this.pass = pass;
            this.slice = slice;
            this.start = start;
            this.end = end;
            this.laneFrom = laneFrom;
            this.laneTo = laneTo;
        }

        @Override
        protected void compute() {
            for (int lane = laneFrom; lane < laneTo; lane++) {
                owner.fillSegment(pass, slice, lane, start, end);
            }
        }
    }

    /** 并行填充专用池：惰性创建、守护线程，随 JVM 退出（与 InternalExecutors 一致，不提供关闭）。 */
    private static final class PoolHolder {
        static final ForkJoinPool POOL = new ForkJoinPool(
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                new Argon2ThreadFactory(), null, false);
    }

    /** 基于默认工厂创建工作线程，仅置为守护线程并按 {@code flora-argon2-N} 命名。 */
    private static final class Argon2ThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {

        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setDaemon(true);
            t.setName("flora-argon2-" + seq.incrementAndGet());
            return t;
        }
    }

    // ===== H0 / H' =====

    private static byte[] initialHash(int type, byte[] password, byte[] salt, byte[] secret, byte[] ad, int memoryKiB,
            int iterations, int parallelism, int tagLen) {
        byte[] s = secret == null ? new byte[0] : secret;
        byte[] a = ad == null ? new byte[0] : ad;
        Blake2bDigest d = Blake2bDigest.of512();
        d.update(le32(parallelism), 0, 4);
        d.update(le32(tagLen), 0, 4);
        d.update(le32(memoryKiB), 0, 4);
        d.update(le32(iterations), 0, 4);
        d.update(le32(VERSION), 0, 4);
        d.update(le32(type), 0, 4);
        d.update(le32(password.length), 0, 4);
        d.update(password, 0, password.length);
        d.update(le32(salt.length), 0, 4);
        d.update(salt, 0, salt.length);
        d.update(le32(s.length), 0, 4);
        d.update(s, 0, s.length);
        d.update(le32(a.length), 0, 4);
        d.update(a, 0, a.length);
        byte[] out = new byte[64];
        d.doFinal(out, 0);
        return out;
    }

    /** RFC 9106 §3.3 变长哈希 H'^T(A)。 */
    private static byte[] variableHash(byte[] a, int outLen) {
        if (outLen <= 64) {
            Blake2bDigest d = new Blake2bDigest(outLen);
            d.update(le32(outLen), 0, 4);
            d.update(a, 0, a.length);
            byte[] out = new byte[outLen];
            d.doFinal(out, 0);
            return out;
        }
        int r = (outLen + 31) / 32 - 2;
        byte[] out = new byte[outLen];
        Blake2bDigest d = new Blake2bDigest(64);
        d.update(le32(outLen), 0, 4);
        d.update(a, 0, a.length);
        byte[] v = new byte[64];
        d.doFinal(v, 0);
        System.arraycopy(v, 0, out, 0, 32);
        for (int i = 2; i <= r; i++) {
            d = new Blake2bDigest(64);
            d.update(v, 0, 64);
            d.doFinal(v, 0);
            System.arraycopy(v, 0, out, (i - 1) * 32, 32);
        }
        int remaining = outLen - 32 * r;
        d = new Blake2bDigest(remaining);
        d.update(v, 0, 64);
        byte[] last = new byte[remaining];
        d.doFinal(last, 0);
        System.arraycopy(last, 0, out, r * 32, remaining);
        return out;
    }

    // ===== 压缩函数 G =====

    /** RFC 9106 §3.5 压缩函数；{@code withXor} 时按 Argon2 v1.3 pass>0 语义与旧块 XOR。 */
    private static byte[] fillBlock(byte[] prev, byte[] ref, byte[] next, boolean withXor) {
        long[] v = new long[128]; // R = prev ⊕ ref
        long[] tmp = new long[128];
        for (int i = 0; i < 128; i++) {
            v[i] = readLong(prev, i * 8) ^ readLong(ref, i * 8);
            tmp[i] = v[i];
        }
        if (withXor) {
            for (int i = 0; i < 128; i++) {
                tmp[i] ^= readLong(next, i * 8);
            }
        }
        for (int i = 0; i < 8; i++) {
            blake2Round(v, i * 16);
        }
        long[] col = new long[16];
        for (int i = 0; i < 8; i++) {
            for (int row = 0; row < 8; row++) {
                col[row * 2] = v[row * 16 + 2 * i];
                col[row * 2 + 1] = v[row * 16 + 2 * i + 1];
            }
            blake2Round(col, 0);
            for (int row = 0; row < 8; row++) {
                v[row * 16 + 2 * i] = col[row * 2];
                v[row * 16 + 2 * i + 1] = col[row * 2 + 1];
            }
        }
        byte[] out = new byte[BLOCK_SIZE];
        for (int i = 0; i < 128; i++) {
            writeLong(tmp[i] ^ v[i], out, i * 8);
        }
        return out;
    }

    private static void blake2Round(long[] x, int base) {
        g(x, base + 0, base + 4, base + 8, base + 12);
        g(x, base + 1, base + 5, base + 9, base + 13);
        g(x, base + 2, base + 6, base + 10, base + 14);
        g(x, base + 3, base + 7, base + 11, base + 15);
        g(x, base + 0, base + 5, base + 10, base + 15);
        g(x, base + 1, base + 6, base + 11, base + 12);
        g(x, base + 2, base + 7, base + 8, base + 13);
        g(x, base + 3, base + 4, base + 9, base + 14);
    }

    /** Argon2 的 G 函数：BLAKE2b 轮 + trunc 乘法项（RFC 9106 §3.6）。 */
    private static void g(long[] v, int a, int b, int c, int d) {
        // trunc(x) = 低 32 位无符号扩展；乘积按 64 位无符号运算（long 溢出即 mod 2^64）
        long ta = Integer.toUnsignedLong((int) v[a]);
        long tb = Integer.toUnsignedLong((int) v[b]);
        long tc = Integer.toUnsignedLong((int) v[c]);
        long td = Integer.toUnsignedLong((int) v[d]);
        v[a] = v[a] + v[b] + 2L * ta * tb;
        v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        tc = Integer.toUnsignedLong((int) v[c]);
        td = Integer.toUnsignedLong((int) v[d]);
        v[c] = v[c] + v[d] + 2L * tc * td;
        v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        ta = Integer.toUnsignedLong((int) v[a]);
        tb = Integer.toUnsignedLong((int) v[b]);
        v[a] = v[a] + v[b] + 2L * ta * tb;
        v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        tc = Integer.toUnsignedLong((int) v[c]);
        td = Integer.toUnsignedLong((int) v[d]);
        v[c] = v[c] + v[d] + 2L * tc * td;
        v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    // ===== 索引寻址 =====

    /** Argon2i 风格：数据无关索引，{@code G(0, G(0, Z‖LE64(counter)‖0))} 生成地址块逐对取值。 */
    private static long independentValue(int type, int pass, int lane, int slice, int mPrime, int iterations, int within) {
        byte[] input = new byte[BLOCK_SIZE];
        writeLong(pass, input, 0);
        writeLong(lane, input, 8);
        writeLong(slice, input, 16);
        writeLong(mPrime, input, 24);
        writeLong(iterations, input, 32);
        writeLong(type, input, 40);
        writeLong(within / 128 + 1, input, 48); // ref.c 每个 segment 首个地址块计数器从 1 起
        byte[] address = fillBlock(zero(), input, zero(), false);
        address = fillBlock(zero(), address, zero(), false);
        return readLong(address, (within % 128) * 8);
    }

    /** 全零块常量；{@link #fillBlock} 只读 prev/ref，可安全地被多线程共享（不得写入）。 */
    private static final byte[] ZERO_BLOCK = new byte[BLOCK_SIZE];

    private static byte[] zero() {
        return ZERO_BLOCK;
    }

    /** 从引用域 W 计算引用位置（ref.c index_alpha 语义）。 */
    private static int indexAlpha(int pass, int slice, int lane, int refLane,
            long j1, int within, int segmentLength, int laneLength) {
        boolean sameLane = refLane == lane;
        long referenceAreaSize;
        if (pass == 0) {
            if (slice == 0) {
                referenceAreaSize = within - 1;
            } else if (sameLane) {
                referenceAreaSize = slice * segmentLength + within - 1;
            } else {
                referenceAreaSize = slice * segmentLength + (within == 0 ? -1 : 0);
            }
        } else if (sameLane) {
            referenceAreaSize = laneLength - segmentLength + within - 1;
        } else {
            referenceAreaSize = laneLength - segmentLength + (within == 0 ? -1 : 0);
        }
        if (referenceAreaSize < 0) {
            referenceAreaSize = 0;
        }
        long x = j1 & 0xffffffffL;
        x = (x * x) >>> 32;
        long y = (referenceAreaSize * x) >>> 32;
        long relative = referenceAreaSize - 1 - y;
        long start = 0;
        if (pass != 0) {
            start = (slice + 1) * segmentLength;
            if (start >= laneLength) {
                start = 0;
            }
        }
        // 参考实现用 uint64 运算：relative 可能为负，对应无符号下的大正数，
        // 须以有符号取模再纠正，避免返回负索引。
        long idx = (start + relative) % laneLength;
        if (idx < 0) {
            idx += laneLength;
        }
        return (int) idx;
    }

    // ===== 工具 =====

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        byte[] out = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        System.arraycopy(c, 0, out, a.length + b.length, c.length);
        return out;
    }

    private static byte[] le32(int v) {
        return new byte[] {(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static long readLong(byte[] b, int off) {
        return (b[off] & 0xffL) | (b[off + 1] & 0xffL) << 8 | (b[off + 2] & 0xffL) << 16
                | (b[off + 3] & 0xffL) << 24 | (b[off + 4] & 0xffL) << 32 | (b[off + 5] & 0xffL) << 40
                | (b[off + 6] & 0xffL) << 48 | (b[off + 7] & 0xffL) << 56;
    }

    private static void writeLong(long v, byte[] b, int off) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
        b[off + 4] = (byte) (v >>> 32);
        b[off + 5] = (byte) (v >>> 40);
        b[off + 6] = (byte) (v >>> 48);
        b[off + 7] = (byte) (v >>> 56);
    }
}
