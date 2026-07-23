package com.flora.tangle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 混淆器全链路集成测试（Integration Test / IT）：验证从样本源码编译 → obfuscator CLI 混淆 →
 * 运行混淆后 jar → 打印文字校验的完整流程。
 *
 * <p>使用 {@link javax.tools.JavaCompiler} API 在 JVM 进程内完成编译，无需外部 {@code javac} 命令。
 * 使用子进程执行混淆后的 jar 以验证最终可运行性。
 */
class ObfuscatorIntegrationTest {

    /** 样本程序输出的预期文字。 */
    private static final String EXPECTED_OUTPUT = "Tangle IT: obfuscation test passed!";

    /** 样本程序的 Java 源码。 */
    private static final String HELLO_APP_SRC = """
            package com.example;
            public class HelloApp {
                public static void main(String[] args) {
                    System.out.println("Tangle IT: obfuscation test passed!");
                }
            }
            """;

    @Test
    void fullObfuscationPipeline(@TempDir Path tmp) throws Exception {
        // ===== 1. 写样本源码 =====
        Path srcFile = tmp.resolve("src/com/example/HelloApp.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, HELLO_APP_SRC, StandardCharsets.UTF_8);

        // ===== 2. javac 编译 =====
        Path classesDir = tmp.resolve("classes");
        assertCompileOk(srcFile, classesDir);

        // ===== 3. 打 jar（含 MANIFEST 指定 Main-Class） =====
        Path inputJar = tmp.resolve("in.jar");
        byte[] classBytes = Files.readAllBytes(classesDir.resolve("com/example/HelloApp.class"));
        buildJar(inputJar, "com.example.HelloApp", "com/example/HelloApp.class", classBytes);

        // ===== 4. 调用 obfuscator CLI 混淆 =====
        Path outputJar = tmp.resolve("out.jar");
        // 捕获 Tangle.main 的输出，避免与后续子进程输出混淆
        Tangle.main(new String[]{
                inputJar.toString(),
                outputJar.toString(),
                "--keep", "com/example"
        });
        assertTrue(Files.exists(outputJar), "混淆后的 jar 应已生成");
        assertTrue(Files.size(outputJar) > 0, "混淆后的 jar 不应为空");

        // ===== 5. 作为子进程运行混淆后的 jar，捕获 stdout =====
        String actual = runJarAndCaptureStdout(outputJar);

        // ===== 6. 断言输出与预期一致 =====
        assertEquals(EXPECTED_OUTPUT, actual.trim(),
                "混淆后的 jar 执行输出应与原文一致");
    }

    // ===================== 辅助方法 =====================

    /** 使用 {@link JavaCompiler} API 在 JVM 进程内编译一个 .java 文件到目标目录。 */
    private static void assertCompileOk(Path srcFile, Path destDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,
                "需要 JDK 运行此测试（ToolProvider.getSystemJavaCompiler() 返回 null）");
        try {
            Files.createDirectories(destDir);
        } catch (IOException e) {
            fail("无法创建编译输出目录 " + destDir + ": " + e.getMessage());
        }

        StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(srcFile);
        List<String> options = List.of("-d", destDir.toString());

        ByteArrayOutputStream errOut = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(errOut, StandardCharsets.UTF_8)) {
            JavaCompiler.CompilationTask task = compiler.getTask(writer, fm, null, options, null, units);
            boolean success = task.call();
            assertTrue(success,
                    "javac 编译失败:\n" + errOut.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            fail("编译过程中发生 IO 错误: " + e.getMessage());
        }
    }

    /** 获取当前 JVM 的 java 可执行文件路径。 */
    private static Path findJava() {
        // Java 9+: 从当前 JVM 进程信息获取 java 可执行路径，完全跨平台。
        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .orElse(Path.of("java"));
    }

    /** 将 class 文件打包为可执行 jar（含 MANIFEST）。 */
    private static void buildJar(Path jarPath, String mainClass,
                                  String classEntryName, byte[] classBytes) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            // MANIFEST.MF
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            String manifest = "Manifest-Version: 1.0\r\n"
                    + "Main-Class: " + mainClass + "\r\n"
                    + "\r\n";
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // .class 文件
            zos.putNextEntry(new ZipEntry(classEntryName));
            zos.write(classBytes);
            zos.closeEntry();
        }
    }

    /** 执行 {@code java -jar jarPath} 并捕获全部的 stdout 输出。 */
    private static String runJarAndCaptureStdout(Path jarPath) throws Exception {
        Path javaBin = findJava();
        Process proc = new ProcessBuilder(
                javaBin.toString(),
                "-jar",
                jarPath.toString()
        ).start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        // 读子进程的输出
        Thread reader = new Thread(() -> {
            try {
                proc.getInputStream().transferTo(stdout);
            } catch (IOException ignored) {
            }
        });
        Thread errReader = new Thread(() -> {
            try {
                proc.getErrorStream().transferTo(stderr);
            } catch (IOException ignored) {
            }
        });
        reader.start();
        errReader.start();

        boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
        reader.join(2000);
        errReader.join(2000);

        assertTrue(finished, "混淆后的 jar 应在 30 秒内执行完毕");
        assertEquals(0, proc.exitValue(),
                "混淆后 jar 应正常退出，stderr=" + stderr.toString(StandardCharsets.UTF_8));

        return stdout.toString(StandardCharsets.UTF_8);
    }
}
