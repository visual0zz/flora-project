package com.flora.internal.evaluation.run;

import com.flora.root.algebra.MathUtil;
import org.openjdk.jmh.annotations.*;
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

    /** 独立启动本基准测试,结果输出到 {@code absent/benchmark/} 目录。 */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("absent", "benchmark"));
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Options opt = new OptionsBuilder()
                .include(Pattern.quote(PrimeCountRangeBenchmark.class.getName()))
                .shouldDoGC(true)
                .output("absent/benchmark/prime-count-range-" + time + ".txt")
                .build();
        new Runner(opt).run();
    }
}
