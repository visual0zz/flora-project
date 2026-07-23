package com.flora.benchmark;

import com.flora.algebra.MathUtil;
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

import java.util.concurrent.TimeUnit;

/**
 * {@link MathUtil} 素数相关方法的微基准测试。
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
}
