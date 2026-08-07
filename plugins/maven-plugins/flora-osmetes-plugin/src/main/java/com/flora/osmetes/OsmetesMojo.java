package com.flora.osmetes;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Maven Mojo 封装：驱动 osmetes 综合检查引擎。
 * <p>
 * 对 {@code sourceRoot} 目录下的文件执行全部已注册检查项（编码、密钥、
 * Tab、行尾空白 trailing-whitespace 等），收集问题并通过 Maven 日志接口输出；存在 ERROR 级别
 * 问题时使构建失败。可通过 {@code disabledChecks} 关闭其中若干检查项，也可通过
 * {@code checkConfig} 给各个检查项下发其自定义的配置（如扩展编码检查允许的编码集）。
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

    /**
     * 需要关闭的检查项名称（用 {@code ,}、{@code ;}、{@code |}、{@code &} 中任意一个分隔，取并集）。
     * <p>
     * 例如 {@code secret;tab} 关闭密钥与 Tab 检查，{@code trailing-whitespace} 仅关闭行尾空白检查。
     * 取值为各检查项在报告中显示的名称（{@link FileCheck#name()}）。为空时执行全部检查项。
     * 注意：在 pom.xml 中 {@code &} 需写作 {@code &amp;}，建议优先使用 {@code ;} 或 {@code ,} 分隔。
     */
    @Parameter(property = "osmetes.disabledChecks", defaultValue = "")
    private String disabledChecks;

    /**
     * 检查项级的通用配置表（键 -> 值），原样下发给引擎。
     * <p>
     * 键以检查项名称作为命名空间前缀（如 {@code encoding.allowed}、{@code secret.minLength}）；
     * 引擎按前缀划分后，剥离前缀把仅属于自己的子集交给对应检查项，检查项本身只认裸子键。
     * 例如用户以 {@code encoding.allowed} 配置，引擎剥离 {@code encoding.} 后把
     * {@code allowed} 下发给编码检查来扩展允许的编码：
     * <pre>
     * &lt;checkConfig&gt;
     *     &lt;encoding.allowed&gt;UTF-8;GBK&lt;/encoding.allowed&gt;
     * &lt;/checkConfig&gt;
     * </pre>
     * 未配置该键时检查项使用自身默认值（编码检查默认仅 {@code UTF-8}）。
     */
    @Parameter(property = "osmetes.checkConfig")
    private Map<String, String> checkConfig;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            List<CheckIssue> issues = Osmetes.run(
                    Paths.get(sourceRoot).toAbsolutePath().normalize(),
                    Osmetes.discoverChecks(),
                    ignorePatterns,
                    Osmetes.parseNames(disabledChecks),
                    checkConfig == null ? Map.of() : checkConfig);
            logIssues(issues);
            long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
            if (errors > 0) {
                throw new MojoExecutionException(
                        "osmetes 检查失败，共 " + errors + " 个错误");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("osmetes 检查执行失败", e);
        }
    }

    /**
     * 通过 Maven 日志接口输出检查结果：ERROR 走 error 级、WARNING 走 warn 级，
     * 从而具备原生 Maven 日志前缀并响应 {@code -q}/{@code -X} 等冗长控制。
     *
     * @param issues 本次扫描收集到的问题（已按位置排序）
     */
    private void logIssues(List<CheckIssue> issues) {
        long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
        long warnings = issues.size() - errors;
        if (issues.isEmpty()) {
            getLog().info("[flora-osmetes] 检查通过");
            return;
        }
        getLog().info("[flora-osmetes] 共发现 " + errors + " 个错误、" + warnings + " 个警告：");
        for (CheckIssue issue : issues) {
            String line = "  [" + (issue.severity() == Severity.ERROR ? "ERROR" : "WARN ")
                    + "] " + issue.location() + " [" + issue.check() + "] " + issue.message();
            if (issue.severity() == Severity.ERROR) {
                getLog().error(line);
            } else {
                getLog().warn(line);
            }
        }
    }
}
