package com.flora.osmetes;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 * Maven Mojo 封装：驱动 osmetes 综合检查引擎。
 * <p>
 * 对 {@code sourceRoot} 目录下的文件执行全部已注册检查项（编码、密钥、
 * Tab、行尾空白等），收集并打印所有问题；存在 ERROR 级别问题时使构建失败。
 */
@Mojo(name = "check")
public final class OsmetesMojo extends AbstractMojo {

    /**
     * 待检查的根目录。
     */
    @Parameter(property = "osmetes.sourceRoot", defaultValue = "${maven.multiModuleProjectDirectory}",
            required = true)
    private String sourceRoot;

    /**
     * 额外忽略规则（在 {@code .gitignore} 之外的显式配置，优先级最高）。
     * <p>
     * 用 {@code ,}、{@code ;}、{@code |}、{@code &} 中任意一个字符分隔多条模式，
     * 多个模式取并集，语义与 {@code .gitignore} 一致：
     * <ul>
     *   <li>{@code absent/} —— 忽略所有全路径包含名为 {@code absent} 目录的文件；</li>
     *   <li>{@code *.log} —— 忽略所有扩展名为 {@code .log} 的文件；</li>
     *   <li>{@code !src/Keep.java} —— 取反重新包含。</li>
     * </ul>
     * 例如 {@code absent/;*.log}。注意：在 pom.xml 中 {@code &} 需写作 {@code &amp;}，
     * 建议优先使用 {@code ;} 或 {@code ,} 作为分隔符。
     */
    @Parameter(property = "osmetes.ignorePatterns", defaultValue = "")
    private String ignorePatterns;

    @Override
    public void execute() throws MojoExecutionException {
        forceUtf8Output();
        try {
            List<CheckIssue> issues = Osmetes.run(
                    Paths.get(sourceRoot).toAbsolutePath().normalize(),
                    Osmetes.discoverChecks(),
                    ignorePatterns);
            Osmetes.print(issues);
            long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
            if (errors > 0) {
                throw new MojoExecutionException(
                        "osmetes 检查失败，共 " + errors + " 个错误");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("osmetes 检查执行失败", e);
        }
    }

    /** 把标准输出强制切到 UTF-8，避免 Windows 控制台编码导致的乱码。 */
    private static void forceUtf8Output() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }
}
