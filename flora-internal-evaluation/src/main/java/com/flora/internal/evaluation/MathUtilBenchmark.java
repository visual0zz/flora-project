package com.flora.internal.evaluation;

import com.flora.root.algebra.MathUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * {@link MathUtil} 素数相关方法的微基准测试。
 * <p>独立运行:直接执行 {@link #main} 即可启动本基准。</p>
 */
@BenchmarkMode({Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class MathUtilBenchmark {

    @Param({"2", "997", "1000003", "1000000", "2147483547"})
    public int primeCandidate;

    // ==================== isPrime 基准测试 ====================

    @Benchmark
    public boolean isPrime() {
        return MathUtil.isPrime(primeCandidate);
    }

    // ==================== nextPrime 基准测试 ====================

    @Benchmark
    public int nextPrime() {
        return MathUtil.nextPrime(primeCandidate);
    }

    // ==================== prevPrime 基准测试 ====================

    @Benchmark
    public int prevPrime() {
        return MathUtil.prevPrime(primeCandidate);
    }

    // ==================== primeCount 基准测试 ====================

    @Benchmark
    public long primeCount() {
        return MathUtil.primeCount(primeCandidate);
    }

    @Benchmark
    public long primeCountRange() {
        return MathUtil.primeCount(primeCandidate, 1_000_000);
    }

    /** 独立启动本基准测试,结果输出到 {@code absent/benchmark/} 目录。 */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("absent", "benchmark"));
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Options opt = new OptionsBuilder()
                .include(Pattern.quote(MathUtilBenchmark.class.getName()))
                .shouldDoGC(true)
                .output("absent/benchmark/math-util-" + time + ".txt")
                .build();
        new Runner(opt).run();
    }
}
