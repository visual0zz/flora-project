package com.flora.osmetes;

import com.flora.osmetes.check.EncodingCheck;
import com.flora.osmetes.check.SecretCheck;
import com.flora.osmetes.check.TabCheck;
import com.flora.osmetes.check.TrailingWhitespaceCheck;
import com.flora.osmetes.gitignore.GitIgnore;
import com.flora.osmetes.gitignore.GitIgnoreChain;
import com.flora.osmetes.suppress.SuppressWarningsScanner;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

/**
 * osmetes 综合检查引擎：接收一个根路径，对匹配的文件执行所有已注册检查项，
 * 收集全部问题并打印，返回是否通过（存在 ERROR 即不通过）。
 * <p>
 * 检查项来源：
 * <ul>
 *   <li>内置检查项（编码、密钥、Tab 缩进、行尾空白），见 {@link #builtinChecks()}；</li>
 *   <li>通过 SPI（{@code FileCheck} 的 {@code ServiceLoader}）提供的第三方检查项。</li>
 * </ul>
 * <p>
 * 用法：{@code java com.flora.osmetes.Osmetes <sourceRoot>}
 */
public final class Osmetes {

    private static final String PREFIX = "[flora-osmetes]";

    private Osmetes() {
    }

    /**
     * 命令行入口：扫描指定根路径并打印结果。
     * 存在任何 ERROR 级别问题时抛出 {@link RuntimeException}（退出码非零）。
     */
    public static void main(String[] args) throws IOException {
        forceUtf8Output();
        if (args.length < 1) {
            System.err.println("用法: Osmetes <sourceRoot>");
            throw new RuntimeException("缺少参数 sourceRoot");
        }
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.out.println(PREFIX + " 跳过（目录不存在）: " + root);
            return;
        }
        List<FileCheck> checks = discoverChecks();
        List<CheckIssue> issues = run(root, checks);
        print(issues);
        long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
        if (errors > 0) {
            throw new RuntimeException("osmetes 检查失败，共 " + errors + " 个错误、"
                    + (issues.size() - errors) + " 个警告");
        }
    }

    public static List<CheckIssue> run(Path root, List<FileCheck> checks) throws IOException {
        return run(root, checks, "");
    }

    /**
     * 程序化入口：对根路径执行所有检查项，返回收集到的全部问题（不打印）。
     * <p>
     * 遍历时自动遵循 git 的忽略规则：读取扫描根及其子目录中的 {@code .gitignore}，
     * 按 git 语义跳过被忽略的文件与目录（被忽略目录整棵剪枝，其内部内容不可被
     * 取反重新包含），并始终跳过 {@code .git} 目录。
     *
     * @param root   待扫描的根目录
     * @param checks 参与本次扫描的检查项列表
     * @return 全部检查发现的问题，按文件路径与位置排序
     */
    public static List<CheckIssue> run(Path root, List<FileCheck> checks, String ignorePatterns) throws IOException {
        List<CheckIssue> all = new ArrayList<>();
        Path absRoot = root.toAbsolutePath().normalize();
        GitIgnoreChain chain = new GitIgnoreChain(GitIgnore.parsePatterns(absRoot, ignorePatterns));
        Files.walkFileTree(absRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // 扫描根本身不参与忽略判定，始终进入
                if (!dir.equals(absRoot)) {
                    if (isGitInternalDir(dir) || chain.isIgnored(dir, true)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                chain.pushGitIgnore(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                chain.popGitIgnore(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile() || chain.isIgnored(file, false)) {
                    return FileVisitResult.CONTINUE;
                }
                String ext = extension(file);
                if (ext == null) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = relativize(file, absRoot);
                List<CheckIssue> fileIssues = new ArrayList<>();
                for (FileCheck check : checks) {
                    if (check.fileExtensions().contains(ext)) {
                        check.check(file, rel, fileIssues);
                    }
                }
                suppressByAnnotation(file, ext, fileIssues);
                all.addAll(fileIssues);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        all.sort(Comparator
                .comparing(CheckIssue::relativeFile)
                .thenComparing(CheckIssue::line)
                .thenComparing(CheckIssue::column)
                .thenComparing(CheckIssue::check));
        return all;
    }

    /** git 内部目录，永远不扫描。 */
    private static boolean isGitInternalDir(Path dir) {
        return dir.getFileName() != null && dir.getFileName().toString().equals(".git");
    }

    /**
     * 按源码中的 {@code @SuppressWarnings("osmetes:<检查名>")} 注解过滤行级问题。
     * <p>
     * 仅对 {@code .java} 文件生效；文件级问题（行号为 0）不受注解影响。
     */
    private static void suppressByAnnotation(Path file, String ext, List<CheckIssue> issues) {
        if (issues.isEmpty() || !ext.equals(".java")) {
            return;
        }
        SuppressWarningsScanner scanner;
        try {
            scanner = SuppressWarningsScanner.parse(file);
        } catch (IOException | RuntimeException e) {
            return; // 解析失败则不做抑制，问题照常报告
        }
        issues.removeIf(issue -> issue.line() > 0 && scanner.isSuppressed(issue.line(), issue.check()));
    }

    /**
     * 发现参与本次扫描的检查项：内置检查项 + SPI 提供的第三方检查项。
     */
    public static List<FileCheck> discoverChecks() {
        List<FileCheck> checks = new ArrayList<>(builtinChecks());
        for (FileCheck check : ServiceLoader.load(FileCheck.class)) {
            if (checks.stream().noneMatch(c -> c.name().equals(check.name()))) {
                checks.add(check);
            }
        }
        return checks;
    }

    /**
     * 内置检查项集合（可被子类/第三方通过 SPI 扩展）。
     */
    public static List<FileCheck> builtinChecks() {
        return List.of(
                new EncodingCheck(),
                new SecretCheck(),
                new TabCheck(),
                new TrailingWhitespaceCheck());
    }

    /** 打印全部问题。 */
    public static void print(List<CheckIssue> issues) {
        long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
        long warnings = issues.size() - errors;
        if (issues.isEmpty()) {
            System.out.println(PREFIX + " 检查通过");
            return;
        }
        System.out.println(PREFIX + " 共发现 " + errors + " 个错误、" + warnings + " 个警告：");
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

    /** 提取文件后缀（小写、含点）；无后缀返回 null。 */
    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    /** 计算相对路径，统一使用 {@code /} 分隔。 */
    static String relativize(Path file, Path root) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
