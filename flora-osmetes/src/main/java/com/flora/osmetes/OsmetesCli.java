package com.flora.osmetes;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * osmetes 命令行入口：解析参数、驱动 {@link Osmetes} 引擎、打印报告，并在存在
 * ERROR 级问题时以非零退出码结束进程。
 * <p>
 * 引擎本身只负责计算（见 {@link Osmetes#run}），输出与退出码等 CLI 关注点集中于此，
 * 使引擎可被程序化复用而无需触碰 {@code System.out}。
 * <p>
 * 用法：{@code java com.flora.osmetes.OsmetesCli <sourceRoot>}
 */
public final class OsmetesCli {

    /** CLI 输出前缀，用于区分 osmetes 自身的提示与检查结果。 */
    private static final String CLI_PREFIX = "[flora-osmetes]";

    private OsmetesCli() {
    }

    /**
     * 命令行入口：扫描指定根路径并打印结果。
     * 存在任何 ERROR 级别问题时抛出 {@link RuntimeException}（退出码非零）。
     */
    public static void main(String[] args) throws IOException {
        forceUtf8Output();
        if (args.length < 1) {
            System.err.println("用法: OsmetesCli <sourceRoot>");
            throw new RuntimeException("缺少参数 sourceRoot");
        }
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.out.println(CLI_PREFIX + " 跳过（目录不存在）: " + root);
            return;
        }
        List<CheckIssue> issues = Osmetes.run(root, Osmetes.discoverChecks());
        print(issues);
        long errors = Osmetes.countErrors(issues);
        if (errors > 0) {
            throw new RuntimeException("osmetes 检查失败，共 " + errors + " 个错误、"
                    + Osmetes.countWarnings(issues) + " 个警告");
        }
    }

    /** 打印全部问题（或"检查通过"）。 */
    public static void print(List<CheckIssue> issues) {
        long errors = Osmetes.countErrors(issues);
        long warnings = Osmetes.countWarnings(issues);
        if (issues.isEmpty()) {
            System.out.println(CLI_PREFIX + " 检查通过");
            return;
        }
        System.out.println(CLI_PREFIX + " 共发现 " + errors + " 个错误、" + warnings + " 个警告：");
        for (CheckIssue issue : issues) {
            String marker = issue.severity() == Severity.ERROR ? "ERROR" : "WARN ";
            System.out.printf("  [%s] %s [%s] %s%n",
                    marker, issue.location(), issue.check(), issue.message());
        }
    }

    /** 把标准输出强制切到 UTF-8，避免 Windows 控制台编码导致的乱码。 */
    private static void forceUtf8Output() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }
}
