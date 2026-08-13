package com.flora.ramet;
import com.flora.ramet.engine.CodeGenException;
import com.flora.ramet.engine.Template;
import com.flora.ramet.engine.TemplateEngine;
import com.flora.ramet.engine.TemplateRepository;
import com.flora.ramet.engine.TemplateSource;

import com.flora.shell.CommandService;
import com.flora.shell.InputEvent;
import com.flora.shell.UsageScenario;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: Ramet <templatesDir> <outputDir> [--dry-run]");
            System.exit(2);
            return;
        }
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new com.flora.ramet.cli.RametCommand());
        // 本工具以命令名作为首参，保持原有 "Ramet <templatesDir> <outputDir> [--dry-run]" 的命令行契约
        List<String> cliArgs = new ArrayList<>(args.length + 1);
        cliArgs.add("ramet.gen");
        cliArgs.addAll(List.of(args));
        int exitCode = commandService.submit(InputEvent.ofCliArgs(cliArgs)).exitCode();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
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
