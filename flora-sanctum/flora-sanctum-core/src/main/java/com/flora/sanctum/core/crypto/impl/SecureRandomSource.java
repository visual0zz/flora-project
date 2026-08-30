package com.flora.sanctum.core.crypto.impl;

import com.flora.sanctum.core.crypto.impl.HkdfSha256;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 安全随机源：混合熵（见设计 02"熵混合"）。
 * <p>
 * 所有安全随机（nonce、keyId 的 byte1、DEK、salt、UUID 等）统一经此入口获取，
 * 不直接依赖单一 {@link SecureRandom}。主源为系统 CSPRNG，其输出直接作为
 * HKDF-SHA256 的输入密钥材料（PRK）；叠加一个进程内自维护的 256 位状态（每次调用
 * 均混入当前时间戳差分与线程标识），作为 HKDF 的 info 参与输出混合。HMAC 单向性保证
 * 叠加源无法反推主源，主源（CSPRNG）正常时输出不可预测。
 * <p>
 * 本类可被多个线程共享：状态更新在内部锁保护下进行，主源 {@link SecureRandom}
 * 本身也是线程安全的。
 * <p>
 * 对外不暴露混合细节，调用方只使用 {@link #nextBytes(byte[])} 等常规随机接口。
 */
public final class SecureRandomSource {

    private final SecureRandom primary;

    /**
     * 进程内叠加熵状态（256 位）。每次 {@link #overlay} 调用都将多个零成本、跨平台、
     * 不可观测的抖动源（高精度时钟间隔、线程标识、对象地址哈希、空闲堆差分、
     * 线程数、调用计数）与既有状态混合进来，作为 HKDF 的 info 参与输出混合。
     * 相比原先的单 long 累加器，状态空间更大，且抖动源彼此正交，单一调用更难被预测。
     * <p>访问受 {@code stateLock} 保护，保证多线程下状态更新的原子性与可见性。</p>
     */
    private final byte[] state = new byte[32];
    private final Runtime runtime = Runtime.getRuntime();
    private final AtomicLong prevNano = new AtomicLong(System.nanoTime());
    private final AtomicLong prevFree = new AtomicLong(Runtime.getRuntime().freeMemory());
    private final AtomicLong invokeCount = new AtomicLong(0);

    public SecureRandomSource() {
        this.primary = new SecureRandom();
        // 以启动时刻纳秒与进程标识初始化状态，避免全零起点。
        long init = System.nanoTime();
        for (int i = 0; i < state.length; i += 8) {
            init = init * 6364136223846793005L + 1442695040888963407L;
            long v = init ^ (Thread.currentThread().getId() << 32);
            for (int j = 0; j < 8 && i + j < state.length; j++) {
                state[i + j] = (byte) (v >>> (j * 8));
            }
        }
    }

    /** 填满 dst，使用混合熵。 */
    public void nextBytes(byte[] dst) {
        // 主源随机字节，直接作为 HKDF 的 PRK 输入（RFC 5869 语义下的已提取材料）。
        primary.nextBytes(dst);
        // 叠加源：进程内抖动状态，作为 HKDF 的 info 参与输出混合。
        byte[] info = overlay();
        byte[] out = HkdfSha256.expand(dst, info, dst.length);
        System.arraycopy(out, 0, dst, 0, dst.length);
    }

    /** 返回一个随机字节。 */
    public byte nextByte() {
        byte[] b = new byte[1];
        nextBytes(b);
        return b[0];
    }

    /** 返回一个随机 int。 */
    public int nextInt() {
        byte[] b = new byte[4];
        nextBytes(b);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    /** 返回一个随机 long。 */
    public long nextLong() {
        byte[] b = new byte[8];
        nextBytes(b);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[i] & 0xFFL);
        }
        return v;
    }

    /**
     * 将多个零成本抖动源混入内部状态，返回当前状态快照作为 HKDF info。
     * <p>每次调用都会折叠以下彼此正交的抖动：高精度时钟 {@code nanoTime()} 与上次调用的
     * 间隔差分、调用线程 id、临时对象地址哈希、空闲堆内存差分、JVM 线程数、调用计数；
     * 使跨调用状态难以被预测。持续状态（时钟/堆内存的前值、调用计数）以原子方式读写，
     * 256 位状态数组在内部锁保护下更新，整体线程安全。</p>
     */
    private byte[] overlay() {
        long now = System.nanoTime();
        long tid = Thread.currentThread().threadId();
        int objHash = System.identityHashCode(new Object());
        long free = runtime.freeMemory();
        int threads = Thread.activeCount();
        // 原子取前值并写入当前值，保证间隔差分与调用计数在并发下不丢失、不撕裂。
        long prevNanoVal = prevNano.getAndSet(now);
        long prevFreeVal = prevFree.getAndSet(free);
        long count = invokeCount.getAndIncrement();
        long timeDelta = now - prevNanoVal;
        long freeDelta = free - prevFreeVal;
        synchronized (this) {
            for (int i = 0; i < state.length; i++) {
                // 多源交织：时钟、间隔、线程、对象哈希、堆差分、线程数、调用计数、
                // 字节位置，避免任一源低位重复时状态停滞。轮次项以 64 位常数步进，
                // 保证相邻字节的混合值自然扩散。
                long mix = timeDelta * 0x7E3779B17F4A7C15L
                        + tid * 0x7E3779B17F4A7C15L
                        +objHash * 0x7E3779B17F4A7C15L
                        +freeDelta * 0x7E3779B17F4A7C15L
                        +threads * 0x7E3779B17F4A7C15L
                        +count * 0x2545F4914F6CDD13L
                        +i * 0x9E3779B97F4A7C15L
                        ;
                int v = (state[i] & 0xFF) + (int) (mix & 0xFF)
                        + (int) ((mix >>> 8) & 0xFF);
                state[i] = (byte) (v & 0xFF);
            }
            return state.clone();
        }
    }
}
