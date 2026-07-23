package com.flora.entropy.id;

import com.flora.entropy.LongIdGenerator;
import com.flora.entropy.StringIdGenerator;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;


/**
 * Snowflake ID 生成器，采用美团版位分配生成全局唯一的 64 位 ID。
 * <p>ID 结构（高位→低位）：1 位符号位（恒为 0）+ 时间戳（41 位，相对自定义纪元）+ 工作机器 ID（10 位）+ 序列号（12 位）。
 * 线程安全。</p>
 *
 * <p>workerId 为 10 位工作机器 ID（0~1023），在构造期固定；未指定时由 {@link SecureRandom} 随机分配。</p>
 */
public final class Snowflake64Generator implements LongIdGenerator, StringIdGenerator {


    private static final long EPOCH = 1700000000000L; // 2023-11-14 自定义纪元

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 最大容忍回拨时长（毫秒）：
     *  回拨不超过此值时通过睡眠等待时钟追平，不抛异常。 */
    private static final long MAX_BACKWARD_MS = 1000L;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 创建生成器，workerId 由 {@link SecureRandom} 随机分配（0~1023）。
     */
    public Snowflake64Generator() {
        SecureRandom random = new SecureRandom();
        this.workerId = random.nextInt((int) MAX_WORKER_ID + 1);
    }

    /**
     * 创建生成器，显式指定 10 位工作机器 ID。
     *
     * @param workerId 工作机器 ID（0~1023）
     */
    public Snowflake64Generator(long workerId) {
        CheckUtil.mustTrue(workerId >= 0 && workerId <= MAX_WORKER_ID,
                "workerId 必须在 0 到 " + MAX_WORKER_ID + " 之间");
        this.workerId = workerId;
    }

    /**
     * 生成下一个唯一 ID。
     * <p>同一毫秒内通过递增序列号支持最多 4096 个 ID，
     * 序列号耗尽时等待下一毫秒。</p>
     * <p>允许容错最多 {@value #MAX_BACKWARD_MS}ms 的时钟回拨，
     * 超过此范围才抛出异常。</p>
     *
     * @return 64 位唯一 ID
     * @throws RuntimeException 时钟回拨超过 {@value #MAX_BACKWARD_MS}ms
     */
    @Override
    public synchronized long nextLongId() {
        long now = System.currentTimeMillis();
        sequence = (sequence + 1) & SEQUENCE_MASK;
        if(now != lastTimestamp || sequence == 0){
            now=waitUntil(lastTimestamp+1);
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个唯一 ID 的十六进制字符串（16 字符）。
     * <p>等价于 {@link #nextLongId()} 的 16 位小写十六进制表示，高字节在前。</p>
     *
     * @return 16 字符的十六进制字符串
     */
    @Override
    public String nextStrId() {
        return String.format("%016x", nextLongId());
    }

    private long waitUntil(long target) {
        long now;
        do{
            now=System.currentTimeMillis();
            long backward = target - now;
            if(backward > MAX_BACKWARD_MS){
                throw new RuntimeException("时钟回拨 " + backward + "ms，超出最大容忍 " + MAX_BACKWARD_MS + "ms，拒绝生成 ID");
            }else if(backward > 1){
                try {
                    Thread.sleep(backward);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }while(now < target);
        return now;
    }
}
