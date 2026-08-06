package com.flora.ramet;
import com.flora.ramet.engine.CodeGenException;
import com.flora.ramet.engine.Template;
import com.flora.ramet.engine.TemplateEngine;
import com.flora.ramet.engine.TemplateRepository;
import com.flora.ramet.engine.TemplateSource;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * flora-ramet 的命令行入口：递归扫描模板文件夹，按模板自声明的元数据生成源码。
 *
 * <p>Ramet 负责文件系统相关操作：扫描模板目录、读取模板文件、拼接输出路径、写入结果文件。
 * 模板解析与渲染委托给 {@link TemplateEngine}，子模板通过 {@link TemplateRepository} 按需加载。</p>
 *
 * <p>文件系统操作通过 {@code java.nio.file.FileSystem} 抽象，
 * 默认使用 {@code FileSystems.getDefault()}（真实文件系统）；
 * 亦可传入任意 {@code FileSystem} 实现（如内存文件系统）创建的 Path，
 * 使整个生成过程运行于该虚拟文件系统之上。</p>
 */
public final class Ramet {
    private Ramet() {
    }

    public static void main(String[] args) throws IOException {
        boolean dryRun = false;
        Path templatesDir = null;
        Path outputDir = null;

        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return;
            } else if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if (templatesDir == null) {
                templatesDir = Paths.get(arg).toAbsolutePath();
            } else if (outputDir == null) {
                outputDir = Paths.get(arg).toAbsolutePath();
            }
        }

        if (templatesDir == null || outputDir == null) {
            System.err.println("用法: Ramet <templatesDir> <outputDir> [--dry-run]");
            System.exit(2);
            return;
        }

        run(templatesDir, outputDir, dryRun);
    }

    /**
     * 递归扫描 templatesDir 下所有 .ramet 文件（不区分大小写），
     * 按模板元数据生成源码到 outputDir。
     * <p>文件系统由 {@code templatesDir} 所属的 {@code FileSystem} 决定 ——
     * 传入任意 {@code FileSystem} 实现（如内存文件系统）创建的 Path，
     * 即运行于该虚拟文件系统上。</p>
     */
    public static void run(Path templatesDir, Path outputDir, boolean dryRun) throws IOException {
        if (!Files.isDirectory(templatesDir)) {
            throw new IllegalArgumentException("模板目录不存在: " + templatesDir);
        }

        // 子模板仓库：按需读取并缓存解析结果
        FileSystemTemplateRepository repo = new FileSystemTemplateRepository(templatesDir);

        int count = 0;
        try (Stream<Path> stream = Files.walk(templatesDir)) {
            List<Path> templateFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".ramet"))
                    .toList();

            Set<String> seenLowerPaths = new HashSet<>();

            for (Path templatePath : templateFiles) {
                String tplContent = Files.readString(templatePath, StandardCharsets.UTF_8);
                String source = templatesDir.relativize(templatePath).toString().replace('\\', '/');
                TemplateSource src = TemplateSource.of(source, tplContent);

                List<TemplateEngine.Generated> results;
                try {
                    results = TemplateEngine.generate(src, repo);
                } catch (CodeGenException e) {
                    throw new CodeGenException("模板 " + templatePath + ": " + e.getMessage(), e);
                }

                for (TemplateEngine.Generated g : results) {
                    if (!seenLowerPaths.add(g.relativePath().toLowerCase())) {
                        throw new CodeGenException(
                                "路径已被其他模板输出占用（不区分大小写）: " + g.relativePath());
                    }
                    Path outputFile = outputDir.resolve(g.relativePath()).toAbsolutePath().normalize();
                    count++;
                    String normalized = normalize(g.content());
                    if (dryRun) {
                        System.out.println("[dry-run] " + outputFile);
                    } else {
                        Path parent = outputFile.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.writeString(outputFile, normalized, StandardCharsets.UTF_8);
                        System.out.println("generated: " + outputFile);
                    }
                }
            }
        }

        System.out.println((dryRun ? "[dry-run] " : "done: ") + count + " file(s)");
    }

    /**
     * 打印帮助信息到标准输出。
     */
    private static void printHelp() {
        System.out.println("""
                用法: Ramet <templatesDir> <outputDir> [--dry-run] [--help]

                flora-ramet 模板代码生成引擎
                ================================

                基本语法:
                  ${expr}           变量插值，输出表达式值
                  ${a.b.c}          属性链访问
                  func(args...)     函数调用（可用于 ${} 和 <#if> 等指令表达式中）
                  <#if cond>...</#if>         条件分支（支持 <#else> 和 <#elseif>）
                  <#for x:items>...</#for>     循环（支持 <#else>）
                  <#continue>                 跳过当前迭代（可选 [depth:][cond]）
                  <#break>                    退出循环（可选 [depth:][cond]）
                  <#macro name:p1,p2=default>...</#macro>   宏定义（:分隔宏名和参数，逗号分隔参数，=指定默认值）
                  <@name args/>              宏调用
                  <#include "path">          引入子模板
                  <#meta>...</#meta>         元数据块
                  <#-- comment -->           注释

                说明:
                  - 模板分为被动区域（普通文本）和逻辑区域（<#...> / ${...}）。
                  - 被动区域零转义，所有字符原样输出。
                    如需输出 ${ 或 <#，使用 ${"${"} 或 ${"<#"}。
                  - 逻辑区域中，字符串字面量 "..." 内部支持 Java 风格转义：
                    \\" \\\\ \\n \\r \\t \\b \\f \\' \\uXXXX
                  - 中缀表达式: a greaterThan b        → greaterThan(a, b)
                  - 中缀无优先级: a greaterThan b and c greaterThan d      需用括号: (a greaterThan b) and (c greaterThan d)

                Meta 标签（写在 <#meta>...</#meta> 中）:
                  @Param{ ... }      模板参数定义（键: 值, ...）
                  @Cartesian{ ... }    笛卡尔积轴定义（轴名 → 值列表或函数调用）
                  @Path{ ... }       输出路径模板（支持 ${} 插值和 Lson 表达式）
                  @SkipWhen{ ... }    跳过条件（布尔表达式，为 true 时跳过本组合的生成）
                  @Config{ ... }     模板级行为配置

                内置函数:
                  [比较逻辑] greaterThan, lessThan, greaterThanOrEquals,
                            lessThanOrEquals, equals, notEquals, and, or, not
                  [字符串]   capitalize, lowercase, uppercase, javaString, concat, contains,
                            replace, startsWith, repeat, join
                  [判空]     notNull, isNull, isEmpty, isBlank
                  [算术]     plus, minus
                  [范围序列] range, sequenceJoin
                  [工具]     firstNonNull, length, now, javaPackageToPath, numberFormat
                  [组合生成] selfCartesian, permutation, combination, multiCombination,
                            cartesian, concatList, concatField, sortBy

                配置项（写在 @Config{ ... } 中）:
                  autoWarning  [boolean]  默认 true
                    生成文件头部自动注入"此文件由模板生成"的警告注释。
                    设为 false 可关闭。
                """);
    }

    /**
     * 基于文件系统的子模板仓库：按 key 读取并解析，带缓存。
     * 路径解析：操作系统绝对路径（平台相关：Unix 以 '/' 开头，Windows 以盘符开头）
     * 原样作为 key（相对 OS 根）；否则为相对于发起 include 的文件所在目录的相对路径。
     */
    private static final class FileSystemTemplateRepository implements TemplateRepository {
        private final Path root;
        private final Map<String, Template> cache = new HashMap<>();

        FileSystemTemplateRepository(Path templatesDir) {
            this.root = templatesDir.toAbsolutePath().normalize();
        }

        @Override
        public Template load(String key) throws CodeGenException {
            Template t = cache.get(key);
            if (t == null) {
                Path file = root.resolve(key);
                if (!Files.isRegularFile(file)) {
                    throw new CodeGenException("#include 未找到模板: " + key);
                }
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    t = Template.parse(TemplateSource.of(key, text));
                } catch (IOException e) {
                    throw new CodeGenException("读取模板失败: " + key, e);
                }
                cache.put(key, t);
            }
            return t;
        }

        @Override
        public String resolve(String fromKey, String path) throws CodeGenException {
            if (Paths.get(path).isAbsolute()) {
                // 操作系统绝对路径（平台相关：Unix 以 '/' 开头，Windows 以盘符开头），
                // 原样作为 key（相对 OS 根，不受模板根限制）
                return Paths.get(path).normalize().toString().replace('\\', '/');
            }
            // 相对：以发起 include 的文件所在目录为基准
            String base = (fromKey != null) ? parentOf(fromKey) : "";
            Path resolved = Paths.get(base).resolve(path).normalize();
            return resolved.toString().replace('\\', '/');
        }

        private static String parentOf(String key) {
            Path p = Paths.get(key).getParent();
            return p != null ? p.toString() : "";
        }
    }

    private static @NotNull String normalize(String content) {
        String normalized = content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", System.lineSeparator());
        if (!normalized.endsWith(System.lineSeparator())) {
            normalized += System.lineSeparator();
        }
        return normalized;
    }

}
