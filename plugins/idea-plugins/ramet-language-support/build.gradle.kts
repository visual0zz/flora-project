import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.flora.ramet"
version = "0.6"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

repositories {
    mavenLocal {
        allowInsecureContinueWhenDisabled.set(true)
    }
    maven("https://maven.aliyun.com/repository/public") {
        allowInsecureContinueWhenDisabled.set(true)
    }
    maven("https://maven.tuna.tsinghua.edu.cn/repository/public") {
        allowInsecureContinueWhenDisabled.set(true)
    }
    maven("https://repo.huaweicloud.com/repository/maven") {
        allowInsecureContinueWhenDisabled.set(true)
    }
    mavenCentral {
        allowInsecureContinueWhenDisabled.set(true)
    }
    maven("https://maven.jtexpress.com.cn/nexus/content/groups/public") {
        allowInsecureContinueWhenDisabled.set(true)
    }

    intellijPlatform {
        defaultRepositories()
    }
}

val osName = System.getProperty("os.name").lowercase()

val ideaHome: String? = when {
    osName.contains("windows") -> {
        // 在 JetBrains 目录下搜索任意 IntelliJ IDEA 安装，不依赖版本号
        val jetBrainsDirs = listOf(
            "C:/Program Files/JetBrains",
            System.getenv("LOCALAPPDATA")?.let { "$it/JetBrains" },
            System.getenv("ProgramFiles")?.let { "$it/JetBrains" }
        ).filterNotNull()
        val candidates = mutableListOf<String>()
        for (base in jetBrainsDirs) {
            val dir = File(base)
            if (dir.isDirectory()) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory && f.name.startsWith("IntelliJ IDEA")) {
                        candidates.add(f.absolutePath)
                    }
                }
            }
        }
        // 按目录名降序排列，优先选版本号更高的
        candidates.sortDescending()
        candidates.add(0, System.getenv("IDEA_HOME") ?: "")
        candidates.firstOrNull { it.isNotEmpty() && File(it).exists() }
    }
    osName.contains("mac") || osName.contains("darwin") -> {
        listOf(
            "/Applications/IntelliJ IDEA.app/Contents",
            "/Applications/IntelliJ IDEA CE.app/Contents",
            "/Applications/IntelliJ IDEA Ultimate.app/Contents",
            "${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app/Contents",
            "${System.getProperty("user.home")}/Applications/IntelliJ IDEA CE.app/Contents",
            System.getenv("IDEA_HOME")
        ).firstOrNull { it != null && File(it).exists() }
    }
    osName.contains("linux") -> {
        listOf(
            "/opt/idea",
            "/opt/intellij-idea",
            "/usr/local/idea",
            "/snap/intellij-idea-community/current",
            "${System.getProperty("user.home")}/idea-IC-2026.1.2",
            System.getenv("IDEA_HOME")
        ).firstOrNull { it != null && File(it).exists() }
    }
    else -> null
}

    dependencies {
        intellijPlatform {
            if (ideaHome != null && File(ideaHome).exists()) {
                local(ideaHome)
            } else {
                intellijIdea("2026.1.2")
            }
            // 让 test-framework（BasePlatformTestCase 等）进入 test 编译/运行 classpath
            testFramework(TestFrameworkType.Platform)
            testFramework(TestFrameworkType.JUnit5)
        }
        testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
        testImplementation("junit:junit:4.13.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    }

    intellijPlatformTesting {
        testIde {
            create("_runInSandbox") {
                // 叠加从 Maven 拉取的 test-framework（含 BasePlatformTestCase），
                // 复用本地 IDEA 作为运行平台，无需下载整个 IDE。
                testFramework(TestFrameworkType.Platform)
                testFramework(TestFrameworkType.JUnit5)
            }
        }
    }

    // 测试沙箱用的 descriptor 解析器（test-framework）不认识 <icon> 元素，
    // 会报 "Unknown element: icon" 并把整个插件判定为加载失败，
    // 导致 .ramet 文件被当纯文本、所有引用/跳转失效。
    // 这里在 prepareSandbox 之后，把沙箱 jar 内 plugin.xml 的 <icon> 行剥掉，
    // 让插件能在 fixture 测试里正常加载。生产用的 plugin.xml 保持不变。
    tasks.register("stripIconFromTestSandbox") {
        dependsOn("prepareSandbox__runInSandbox")
        val sandboxRoot = layout.projectDirectory.dir(".intellijPlatform/sandbox/${project.name}")
        // 沙箱目录名随 IDE 版本变化（如 IU-2026.1），不能写死，否则 jar 找不到、图标剥离与
        // disabled_plugins.txt 全部失效，导致插件在 fixture 里加载失败、测试 bootstrap 崩溃。
        val iuDirName = sandboxRoot.asFile.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.startsWith("IU-") }
            ?.name ?: "IU-2026.1"
        val sandboxBase = sandboxRoot.dir(iuDirName)
        doLast {
            val jars = sandboxBase.asFile.walkTopDown()
                .filter { it.isFile && it.name == "${project.name}-${project.version}.jar" }
                .toList()
            if (jars.isEmpty()) {
                logger.lifecycle("stripIconFromTestSandbox: 未找到沙箱 jar，跳过")
                return@doLast
            }
            for (jar in jars) {
                val tmpDir = Files.createTempDirectory("ramet-sandbox-patch").toFile()
                try {
                    ZipFile(jar).use { zf ->
                        for (e in zf.entries()) {
                            val out = File(tmpDir, e.name)
                            if (e.isDirectory) out.mkdirs()
                            else {
                                out.parentFile.mkdirs()
                                zf.getInputStream(e).use { ins -> out.outputStream().use { os -> ins.copyTo(os) } }
                            }
                        }
                    }
                    val pluginXml = File(tmpDir, "META-INF/plugin.xml")
                    if (pluginXml.exists()) {
                        val patched = pluginXml.readLines()
                            .filterNot { it.trim().startsWith("<icon") }
                        pluginXml.writeText(patched.joinToString("\n"))
                    }
                    val tmpJar = File.createTempFile("ramet-patched", ".jar")
                    ZipOutputStream(tmpJar.outputStream()).use { zos ->
                        tmpDir.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = tmpDir.toPath().relativize(f.toPath()).toString().replace("\\", "/")
                            zos.putNextEntry(ZipEntry(rel))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                    jar.delete()
                    tmpJar.copyTo(jar)
                } finally {
                    tmpDir.deleteRecursively()
                }
                logger.lifecycle("stripIconFromTestSandbox: 已剥离 ${jar.path} 中的 <icon>")
            }
            // 测试沙箱会加载 IDEA 安装目录里的全部 bundled 插件，其中 Vue 插件的
            // LSP 扩展（VueLspServerSupportProvider）在测试 classpath 下无法实例化，
            // 会抛错并污染整个测试（BasePlatformTestCase 把任意 error 日志判为失败）。
            // 这里把 Vue 插件加入 disabled_plugins.txt，避免加载它，不影响本插件功能。
            val configDir = sandboxBase.dir("config__runInSandbox").asFile
            configDir.mkdirs()
            File(configDir, "disabled_plugins.txt").writeText("org.jetbrains.plugins.vue\n")
            logger.lifecycle("stripIconFromTestSandbox: 已写入 disabled_plugins.txt 禁用 Vue 插件")
        }
    }

    // 纯 JUnit5（Jupiter）单元测试在常规 test 任务里跑，使用 Gradle 原生的 JUnit Platform，
    // 避免 IntelliJ Platform Gradle Plugin 的已知 bug IJPL-159134：TestFrameworkType.JUnit5
    // 会被错误解析成 JUnit4，导致 Jupiter 的 @Test 注解不被识别、发现 0 个测试（no tests discovered）。
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
    // _runInSandbox 仅用于未来的 BasePlatformTestCase fixture 测试；当前没有此类测试，
    // 关闭“未发现测试即失败”，避免它被误触发时报错。
    tasks.named<Test>("_runInSandbox") {
        dependsOn("stripIconFromTestSandbox")
        failOnNoDiscoveredTests = false
    }

tasks {
    withType<JavaCompile> {
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    buildSearchableOptions {
        enabled = false
    }
}
