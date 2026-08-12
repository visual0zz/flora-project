package com.flora.osmetes;

import com.flora.osmetes.cli.OsmetesCommand;
import com.flora.shell.CommandService;
import com.flora.shell.entry.Entry;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * osmetes 命令行入口：经 {@code com.flora.shell} 命令框架解析参数、执行检查并打印报告。
 * <p>命令实现见 {@link OsmetesCommand}，本类仅负责 UTF-8 输出与进程退出码。
 * 引擎本身只负责计算（见 {@link Osmetes#run}），输出与退出码等 CLI 关注点集中在命令类。</p>
 * <p>用法：{@code java com.flora.osmetes.OsmetesCli <sourceRoot>}，或 {@code ... help} 查看全部命令。</p>
 */
public final class OsmetesCli {

    private OsmetesCli() {
    }

    /**
     * 命令行入口：扫描指定根路径并打印结果。
     * 存在任何 ERROR 级问题时以非零退出码结束进程。
     */
    public static void main(String[] args) throws IOException {
        forceUtf8Output();
        CommandService component = new CommandService();
        component.register(new OsmetesCommand());
        // 本工具以命令名作为首参，保持原有 "OsmetesCli <sourceRoot>" 的命令行契约
        String[] argv = new String[args.length + 1];
        argv[0] = "osmetes.check";
        System.arraycopy(args, 0, argv, 1, args.length);
        int exitCode = Entry.run(component, argv);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * 打印全部问题（或"检查通过"）到标准输出。供外部以编程方式复用（行为与命令一致）。
     */
    public static void print(List<CheckIssue> issues) {
        long errors = Osmetes.countErrors(issues);
        long warnings = Osmetes.countWarnings(issues);
        if (issues.isEmpty()) {
            System.out.println(OsmetesCommand.CLI_PREFIX + " 检查通过");
            return;
        }
        System.out.println(OsmetesCommand.CLI_PREFIX + " 共发现 " + errors + " 个错误、" + warnings + " 个警告：");
        for (CheckIssue issue : issues) {
            String marker = issue.severity() == Severity.ERROR ? "ERROR" : "WARN ";
            System.out.printf("  [%s] %s [%s] %s%n",
                    marker, issue.location(), issue.check(), issue.message());
        }
    }

    /**
     * 把标准输出强制切到 UTF-8，避免 Windows 控制台编码导致的乱码。
     */
    private static void forceUtf8Output() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }
}
