package com.flora.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Flora 模块 JMH 微基准测试统一入口。
 * <p>
 * 运行此 main 方法即可执行 {@code com.flora.benchmark} 包下所有带 {@code @Benchmark} 的方法。
 * </p>
 */
public final class FloraBenchmarkRunner {

    private FloraBenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        Options opt = new OptionsBuilder()
                // 扫描 com.flora.benchmark 包下所有带 @Benchmark 的方法
                .include("com\\.flora\\.benchmark\\.run\\..*")
                .shouldDoGC(true)
                .output("absent/benchmark/flora-" + time + ".txt")
                .build();

        new Runner(opt).run();
    }
}
