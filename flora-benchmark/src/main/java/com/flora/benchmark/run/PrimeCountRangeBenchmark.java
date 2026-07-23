package com.flora.benchmark.run;

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
public class PrimeCountRangeBenchmark {

    @Param({"1", "10", "100", "1000","10000", "100000", "1000000", "10000000", "100000000", "1000000000", "2147483547"})
    public int primeA;

    @Param({"200000", "3000000", "40000000", "500000000", "2000000000"})
    public int primeB;
    @Benchmark
    public long primeCount() {
        return MathUtil.primeCount(primeA, primeB);
    }

}
