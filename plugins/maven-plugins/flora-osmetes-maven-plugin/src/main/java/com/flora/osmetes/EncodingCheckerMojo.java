package com.flora.osmetes;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;

/**
 * Maven Mojo 封装：调用 flora-osmetes 编码检查。
 */
@Mojo(name = "check-encoding")
public final class EncodingCheckerMojo extends AbstractMojo {

    @Parameter(property = "sourceRoot", defaultValue = "${project.basedir}/src/main/java", required = true)
    private String sourceRoot;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            EncodingChecker.main(new String[]{sourceRoot});
        } catch (IOException e) {
            throw new MojoExecutionException("编码检查执行失败", e);
        } catch (RuntimeException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
