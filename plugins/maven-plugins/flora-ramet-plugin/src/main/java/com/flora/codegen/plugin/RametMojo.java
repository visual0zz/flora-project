package com.flora.codegen.plugin;

import com.flora.codegen.Ramet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Maven Mojo 封装：执行 flora-ramet 代码生成引擎。
 *
 * <p>用法：
 * <pre>
 *   &lt;plugin&gt;
 *     &lt;groupId&gt;io.gitee.visual0zz&lt;/groupId&gt;
 *     &lt;artifactId&gt;flora-ramet-plugin&lt;/artifactId&gt;
 *     &lt;configuration&gt;
 *       &lt;templatesDir&gt;src/main/templates&lt;/templatesDir&gt;
 *       &lt;outputDir&gt;src/main/java&lt;/outputDir&gt;
 *     &lt;/configuration&gt;
 *   &lt;/plugin&gt;
 * </pre>
 *
 * <p>或从命令行调用：
 * <pre>
 *   mvn io.gitee.visual0zz:flora-ramet-plugin:generate
 * </pre>
 */
@Mojo(name = "generate")
public final class RametMojo extends AbstractMojo {

    /**
     * 模板文件所在目录。
     */
    @Parameter(property = "ramet.templatesDir", defaultValue = "${project.basedir}/src/main/templates",
            required = true)
    private File templatesDir;

    /**
     * 生成代码的输出目录。
     */
    @Parameter(property = "ramet.outputDir", defaultValue = "${project.basedir}/src/main/java",
            required = true)
    private File outputDir;

    /**
     * 仅打印将要生成的文件列表，不实际写入。
     */
    @Parameter(property = "ramet.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path tplPath = templatesDir.toPath().toAbsolutePath().normalize();
        Path outPath = outputDir.toPath().toAbsolutePath().normalize();

        getLog().info("Ramet codegen: " + tplPath + " -> " + outPath
                + (dryRun ? " (dry-run)" : ""));

        try {
            Ramet.run(tplPath, outPath, dryRun);
        } catch (IOException e) {
            throw new MojoExecutionException("Ramet codegen 失败: " + e.getMessage(), e);
        }
    }

}
