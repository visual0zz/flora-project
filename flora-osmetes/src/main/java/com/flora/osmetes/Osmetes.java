package com.flora.osmetes;

import com.flora.osmetes.check.EncodingCheck;
import com.flora.osmetes.check.SecretCheck;
import com.flora.osmetes.check.TabCheck;
import com.flora.osmetes.check.TrailingWhitespaceCheck;
import com.flora.osmetes.gitignore.GitIgnore;
import com.flora.osmetes.gitignore.GitIgnoreChain;
import com.flora.osmetes.suppress.SuppressWarningsScanner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * osmetes 综合检查引擎：接收一个根路径，对匹配的文件执行所有已注册检查项，
 * 收集全部问题并返回（不负责打印，命令行输出见 {@link OsmetesCli}）。
 * <p>
 * 检查项来源：
 * <ul>
 *   <li>内置检查项（编码、密钥、Tab 缩进、行尾空白 trailing-whitespace），见 {@link #builtinChecks()}；</li>
 *   <li>通过 SPI（{@code FileCheck} 的 {@code ServiceLoader}）提供的第三方检查项。</li>
 * </ul>
 * <p>
 * 命令行用法：{@code java com.flora.osmetes.OsmetesCli <sourceRoot>}
 */
public final class Osmetes {

    /** Java 源文件后缀（小写、含点），用于注解抑制等场景。 */
    static final String JAVA_EXTENSION = ".java";

    private Osmetes() {
    }

    public static List<CheckIssue> run(Path root, List<FileCheck> checks) throws IOException {
        return run(root, checks, "", Set.of());
    }

    public static List<CheckIssue> run(Path root, List<FileCheck> checks, String ignorePatterns) throws IOException {
        return run(root, checks, ignorePatterns, Set.of());
    }

    public static List<CheckIssue> run(Path root, List<FileCheck> checks, String ignorePatterns,
                                       Set<String> disabledChecks) throws IOException {
        return run(root, checks, ignorePatterns, disabledChecks, Map.of());
    }

    /**
     * 程序化入口：对根路径执行所有检查项，返回收集到的全部问题（不打印）。
     * <p>
     * 遍历时自动遵循 git 的忽略规则：读取扫描根及其子目录中的 {@code .gitignore}，
     * 按 git 语义跳过被忽略的文件与目录（被忽略目录整棵剪枝，其内部内容不可被
     * 取反重新包含），并始终跳过 {@code .git} 目录。
     * <p>
     * {@code disabledChecks} 中列出的检查项名称（{@link FileCheck#name()}）将整体跳过，
     * 其余检查正常执行。该集合为空时不禁用任何检查项。
     * <p>
     * {@code checkConfig} 是检查项级的通用配置表：引擎在扫描开始前按各检查项的
     * {@link FileCheck#name()} 划分命名空间，把已剥离前缀的子集交给对应检查项
     * （键的含义由各个检查项自行约定，引擎不解析子键含义）。为空（或其中某检查项
     * 未配置）时各检查项使用自身默认值。
     *
     * @param root           待扫描的根目录
     * @param checks         参与本次扫描的检查项列表
     * @param ignorePatterns 额外忽略规则（分隔符连接的模式串，见 {@link GitIgnore#parsePatterns}）
     * @param disabledChecks 需要跳过的检查项名称集合
     * @param checkConfig    检查项级通用配置（键由各个检查项自行定义）
     * @return 全部检查发现的问题，按文件路径与位置排序
     */
    public static List<CheckIssue> run(Path root, List<FileCheck> checks, String ignorePatterns,
                                       Set<String> disabledChecks, Map<String, String> checkConfig) throws IOException {
        configureChecks(checks, checkConfig);
        List<FileCheck> active = checks.stream()
                .filter(c -> !disabledChecks.contains(c.name()))
                .toList();
        Path absRoot = root.toAbsolutePath().normalize();
        GitIgnoreChain chain = new GitIgnoreChain(GitIgnore.parsePatterns(absRoot, ignorePatterns));
        List<CheckIssue> all = new ArrayList<>();
        Files.walkFileTree(absRoot, new ScanningVisitor(absRoot, chain, active, all));
        return sortIssues(all);
    }

    /**
     * 把已剥离 {@link FileCheck#name()} 前缀的配置子集下发给每个检查项。
     *
     * @param checks 待配置的检查项列表
     * @param config 完整配置表
     */
    private static void configureChecks(List<FileCheck> checks, Map<String, String> config) {
        for (FileCheck check : checks) {
            check.configure(configFor(check, config));
        }
    }

    /**
     * 按文件路径、行、列、检查名排序，使报告顺序稳定可读。
     *
     * @param issues 待排序的问题列表（原地排序）
     * @return 同一列表的引用
     */
    private static List<CheckIssue> sortIssues(List<CheckIssue> issues) {
        issues.sort(Comparator
                .comparing(CheckIssue::relativeFile)
                .thenComparing(CheckIssue::line)
                .thenComparing(CheckIssue::column)
                .thenComparing(CheckIssue::check));
        return issues;
    }

    /**
     * 取属于某检查项、已去掉 {@link FileCheck#name()} 前缀的配置子集。
     * <p>
     * 完整配置表的键形如 {@code "<name>.<subKey>"}（如 {@code encoding.allowed}）；
     * 引擎按检查项的 {@code name()} 划分命名空间，只把前缀匹配且已剥离前缀的条目
     * 交给该检查项。这样实现类只需认自己的裸键（如 {@code allowed}），无需感知
     * 顶层前缀，也看不到其它检查项的配置。无匹配项时返回空表。
     *
     * @param check 目标检查项，其 {@code name()} 作为过滤前缀
     * @param config 完整配置表
     * @return 已剥离前缀、仅属于该检查项的配置子集
     */
    private static Map<String, String> configFor(FileCheck check, Map<String, String> config) {
        String prefix = check.name() + ".";
        Map<String, String> subset = new LinkedHashMap<>();
        for (var e : config.entrySet()) {
            String key = e.getKey();
            if (key.startsWith(prefix)) {
                subset.put(key.substring(prefix.length()), e.getValue());
            }
        }
        return subset;
    }

    /**
     * 将分隔符连接的检查项名称串解析为集合。
     * <p>
     * 以 {@code ,}、{@code ;}、{@code |}、{@code &} 中任意一个作为分隔符，多个名称取并集；
     * 各段首尾空白会被去除，空段忽略。例如 {@code "secret;tab"} 解析为包含
     * {@code secret} 与 {@code tab} 的集合。供 Maven 插件等外部配置使用。
     *
     * @param names 分隔符连接的名称串，可为空字符串
     * @return 解析出的名称集合（空串返回空集）
     */
    static Set<String> parseNames(String names) {
        if (names == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String segment : names.split(NAME_DELIMITERS)) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 分隔名称/模式串的分隔符正则（与 ignorePatterns、检查项配置一致）。 */
    static final String NAME_DELIMITERS = "[,;|&]+";

    /** git 内部目录，永远不扫描。 */
    private static boolean isGitInternalDir(Path dir) {
        return dir.getFileName() != null && dir.getFileName().toString().equals(".git");
    }

    /**
     * 按源码中的 {@code @SuppressWarnings("osmetes:<检查名>")} 注解过滤行级问题
     * （前缀见 {@link SuppressWarningsScanner#SUPPRESS_ANNOTATION_PREFIX}）。
     * <p>
     * 仅对 {@link #JAVA_EXTENSION} 文件生效；文件级问题（行号为 0）不受注解影响。
     */
    private static void suppressByAnnotation(Path file, String ext, List<CheckIssue> issues) {
        if (issues.isEmpty() || !ext.equals(JAVA_EXTENSION)) {
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
     * 统计 ERROR 级问题数量。
     *
     * @param issues 问题列表
     * @return ERROR 数量
     */
    public static long countErrors(List<CheckIssue> issues) {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    /**
     * 统计 WARNING 级问题数量。
     *
     * @param issues 问题列表
     * @return WARNING 数量
     */
    public static long countWarnings(List<CheckIssue> issues) {
        return issues.size() - countErrors(issues);
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
     * 内置检查项集合；第三方检查项可通过 SPI（{@code FileCheck} 的 ServiceLoader）
     * 在 {@link #discoverChecks()} 中追加。
     */
    public static List<FileCheck> builtinChecks() {
        return List.of(
                new EncodingCheck(),
                new SecretCheck(),
                new TabCheck(),
                new TrailingWhitespaceCheck());
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

    /**
     * 文件树遍历访问器：按 git 忽略规则跳过目录/文件，对匹配后缀的文件分派给活跃检查项，
     * 再按 {@code @SuppressWarnings} 注解抑制，收集全部问题。状态（链、活跃检查、结果集）
     * 由构造器注入，避免在 {@link #run} 中写过长内联逻辑。
     */
    private static final class ScanningVisitor extends SimpleFileVisitor<Path> {

        private final Path absRoot;
        private final GitIgnoreChain chain;
        private final List<FileCheck> active;
        private final List<CheckIssue> all;

        private ScanningVisitor(Path absRoot, GitIgnoreChain chain, List<FileCheck> active, List<CheckIssue> all) {
            this.absRoot = absRoot;
            this.chain = chain;
            this.active = active;
            this.all = all;
        }

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
            for (FileCheck check : active) {
                if (check.fileExtensions().contains(ext)) {
                    check.check(file, rel, fileIssues);
                }
            }
            suppressByAnnotation(file, ext, fileIssues);
            all.addAll(fileIssues);
            return FileVisitResult.CONTINUE;
        }
    }
}
