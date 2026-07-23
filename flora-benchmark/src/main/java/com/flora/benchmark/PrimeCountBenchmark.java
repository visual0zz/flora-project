package com.flora.benchmark;

import com.flora.algebra.MathUtil;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * {@link MathUtil} 素数相关方法的微基准测试。
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class PrimeCountBenchmark {

    @Param({"1", "10", "100", "1000","10000", "100000", "1000000", "10000000", "100000000", "1000000000", "2147483547"})
    public int primeCandidate;

    @Benchmark
    public boolean isPrime() {
        return MathUtil.isPrime(primeCandidate);
    }

    @Benchmark
    public long primeCount() {
        return MathUtil.primeCount(primeCandidate);
    }

}
