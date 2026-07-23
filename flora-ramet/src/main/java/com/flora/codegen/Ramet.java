package com.flora.codegen;

import com.flora.codegen.engine.CodeGenException;

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
 * 模板解析与渲染委托给 {@link CodeGenUtil}。
 *
 * <p>用法：
 * <pre>
 *   java -cp flora-ramet.jar com.flora.ramet.Ramet &lt;templatesDir&gt; &lt;outputDir&gt; [--dry-run]
 * </pre>
 *
 * <p>每个模板必须在头部注释块中声明 {@code @Param{...}} / {@code @Cartesian{...}} / {@code @Path{...}}。
 * <ul>
 *   <li>{@code @Param{...}}: 模板渲染参数定义</li>
 *   <li>{@code @Cartesian{...}}: 笛卡尔积轴定义（轴名 → 数组或函数调用）</li>
 *   <li>{@code @Path{...}}: 输出路径模板（支持 ${…} 插值和 Lson 表达式）</li>
 * </ul>
 *
 * <p>模板头部元数据块示例：
 * <pre>
 * &lt;#--
 * @Param{ package: "com.flora.fast" }
 * @Cartesian{ K: ["Int", "Long"], V: ["Int", "Long"] }
 * @Path{ ${K.name}2${V.name}HashMap.java }
 * --&gt;
 * </pre>
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
     */
    public static void run(Path templatesDir, Path outputDir, boolean dryRun) throws IOException {
        if (!Files.isDirectory(templatesDir)) {
            throw new IllegalArgumentException("模板目录不存在: " + templatesDir);
        }

        // 构建 include Map：相对路径 → 模板文本（带缓存）
        IncludeCache includeCache = new IncludeCache(templatesDir);

        int count = 0;
        try (Stream<Path> stream = Files.walk(templatesDir)) {
            List<Path> templateFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".ramet"))
                    .toList();

            // 跨模板输出路径去重（大小写不敏感）：在大小写不敏感的
            // 文件系统（如 Windows 的 NTFS）上，两个仅在大小写不同的路径
            // 会解析为同一物理文件并相互覆盖，故以小写形式统一键。
            Set<String> seenLowerPaths = new HashSet<>();

            for (Path templatePath : templateFiles) {
                String tplContent = Files.readString(templatePath, StandardCharsets.UTF_8);
                Map<String, CompiledTemplate> includes = includeCache.asCompiledMap();
                String source = templatesDir.relativize(templatePath).toString().replace('\\', '/');

                List<CodeGenUtil.Generated> results;
                try {
                    results = CodeGenUtil.generate(tplContent, includes, source, templatesDir.toAbsolutePath().normalize().toString().replace('\\', '/'));
                } catch (CodeGenException e) {
                    throw new CodeGenException("模板 " + templatePath + ": " + e.getMessage(), e);
                }

                for (CodeGenUtil.Generated g : results) {
                    String lowerPath = g.relativePath().toLowerCase();
                    if (!seenLowerPaths.add(lowerPath)) {
                        throw new CodeGenException(
                                "跨模板输出路径大小写不敏感碰撞: " + g.relativePath()
                                        + "\n  该路径（不区分大小写）已被其他模板占用，"
                                        + "在大小写不敏感的文件系统上会相互覆盖。");
                    }
                    Path outputFile = outputDir.resolve(g.relativePath()).toAbsolutePath().normalize();
                    count++;
                    if (dryRun) {
                        System.out.println("[dry-run] " + outputFile);
                    } else {
                        writeFile(outputFile, g.content());
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
     * include 模板缓存：接受相对路径，读取文件并预编译后缓存。
     */
    private static final class IncludeCache {
        private final Path templatesDir;
        private final Map<String, CompiledTemplate> cache = new HashMap<>();

        IncludeCache(Path templatesDir) {
            this.templatesDir = templatesDir;
        }

        /** 获取所有已缓存的 include 模板（只读视图），
         * 自动加载并预编译当前目录下所有 .ramet 文件（不区分大小写）。 */
        Map<String, CompiledTemplate> asCompiledMap() throws IOException {
            if (cache.isEmpty()) {
                try (Stream<Path> stream = Files.walk(templatesDir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.toString().toLowerCase().endsWith(".ramet"))
                            .forEach(p -> {
                                String key = templatesDir.relativize(p).toString().replace('\\', '/');
                                if (!cache.containsKey(key)) {
                                    try {
                                        String content = Files.readString(p, StandardCharsets.UTF_8);
                                        cache.put(key, CodeGenUtil.precompile(content, key));
                                    } catch (IOException ignored) {
                                    } catch (CodeGenException e) {
                                        throw new CodeGenException("模板 " + key + ": " + e.getMessage(), e);
                                    }
                                }
                            });
                }
            }
            return cache;
        }
    }

    /**
     * 将内容写入文件，自动创建父目录。
     * 写入时将换行符统一为当前操作系统的格式（Windows → CRLF, Linux/Mac → LF）。
     */
    private static void writeFile(Path file, String content) throws IOException {
        // 归一化各种换行符为操作系统对应的行尾格式
        String normalized = content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", System.lineSeparator());
        // 文件末尾补换行（POSIX 标准）
        if (!normalized.endsWith(System.lineSeparator())) {
            normalized += System.lineSeparator();
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, normalized, StandardCharsets.UTF_8);
    }

}
