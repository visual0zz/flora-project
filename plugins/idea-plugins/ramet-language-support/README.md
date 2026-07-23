# Ramet 模板语言 IntelliJ IDEA 插件

为 `.ramet` 文件提供语法高亮和引用跳转支持。

## 功能

- **语法高亮** — 指令标签、变量插值、注释、字符串、数字、内置函数等分类着色

  > **变量定义 vs 引用配色区分**：`@Param` 元数据块中的变量 **key 定义**为橙黄色
  > （`RAMET_LSON_KEY`）；`${...}` 插值里的变量 **引用**为粉红色（`RAMET_VARIABLE`）。
  > 这一区分由 `RametAnnotator` 在 range 级别按上下文着色实现——定义位置（宏参数、@Param key、
  > 指令表达式内的标识符）走橙黄，引用位置（插值内标识符）走粉红。
  > `#elseif` 与 `#if`/`#else`/`#list` 等指令同样按关键字（`RAMET_KEYWORD`）着色。
- **引用跳转** (Ctrl+Click / Go to Declaration)：
  - `<#include "path.ftl">` → 跳转到目标 `.ftl` 文件
  - `${varName}` → 跳转到 `@Param{ varName: ... }` 定义
  - `<@macroName>` → 跳转到 `<#macro macroName ...>` 定义

  > 只有上述三种**真实引用**会尝试解析跳转。注释（`#-- -->`）、纯文本、字面量等内容
  > 不会注册引用，点击时不会出现「找不到要转到的声明」的报错。
  > 跳转解析基于 PSI 复合节点类型（`RametTypes.COMMENT` / `RametTypes.DIRECTIVE`），而非底层词法 Token 类型。

## 项目结构

```
flora-ramet-idea-plugin/
├── build.gradle.kts              # Gradle 构建脚本
├── gradle.properties             # Gradle 配置（JVM 参数等）
├── settings.gradle.kts           # 项目名称
├── gradlew / gradlew.bat         # Gradle Wrapper
├── gradle/wrapper/               # Wrapper JAR 和属性
└── src/main/
    ├── java/com/flora/ramet/idea/
    │   ├── RametLanguage.java            # 语言定义
    │   ├── RametFileType.java            # .ftl 文件类型
    │   ├── RametIcons.java               # 图标
    │   ├── lexer/                        # 词法分析器
    │   ├── parser/                       # 语法分析器 + PSI 定义
    │   ├── psi/                          # PSI 文件/Token 集合
    │   ├── highlighter/                  # 语法高亮
    │   └── reference/                    # 引用跳转
    └── resources/
        ├── META-INF/plugin.xml           # 插件描述符
        └── icons/ramet.svg               # 文件图标
```

## 配置项

### 本地 IDEA 安装路径

`build.gradle.kts` 中通过 `local()` 指向本地安装的 IntelliJ IDEA：

```kotlin
val ideaHomeByOs = mapOf(
    "windows" to "C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.2",
    "macos"   to "/Applications/IntelliJ IDEA.app/Contents",
    "linux"   to "/Users/shutie.zhao/idea-IC-2026.1.2"
)
```

如果你的 IDEA 安装在不同路径，修改对应操作系统的路径即可。

### 目标 IDEA 版本

如果改用下载模式而非本地模式，在 `build.gradle.kts` 中替换：

```kotlin
// 本地模式（当前）
intellijPlatform { local(ideaHome) }

// 下载模式（替换上面这行）
intellijPlatform { intellijIdea("2026.1.2") }
```

### Gradle JVM

`gradle.properties` 中可设置 Gradle 运行时的 JVM 参数：

```properties
org.gradle.jvmargs=-Xmx2048m
```

如果需要指定 JDK 路径（默认使用系统 JAVA_HOME）：

```properties
org.gradle.java.home=C:\\Program Files\\JavaJDKs\\jdk-17.0.8.7-hotspot
```

## 构建

```bash
# 编译 Java
./gradlew compileJava

# 完整构建（含代码插桩、打包）
./gradlew buildPlugin

# 运行测试
./gradlew test

# 用本地 IDEA 运行插件
./gradlew runIde
```

## 构建产物

打包后的插件安装包在：

```
build/distributions/flora-ramet-idea-plugin-0.1.zip
```

安装方式：IntelliJ IDEA → File → Settings → Plugins → ⚙ → Install Plugin from Disk

## 开发环境要求

- **JDK**: 17+（当前项目 Toolchain 配置为 JDK 26，和系统 JDK 保持一致即可）
- **Gradle**: Wrapper 已配置为 9.6.1，自动下载
- **IntelliJ IDEA**: 2026.1.2（Community Edition 或 Ultimate）
- **IntelliJ Platform Gradle Plugin**: 2.18.1（`build.gradle.kts` 中声明）
