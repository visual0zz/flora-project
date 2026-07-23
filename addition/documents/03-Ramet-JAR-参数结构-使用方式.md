# 03 — Ramet JAR 参数结构与使用方式

## CLI 用法

```bash
java -jar flora-ramet-0.1.jar <templatesDir> <outputDir> [--dry-run] [--help]
```

| 参数 | 说明 |
|------|------|
| `templatesDir` | 模板文件所在目录，递归扫描 `.ramet` 文件 |
| `outputDir` | 生成代码的输出目录 |
| `--dry-run` | 仅打印将要生成的文件路径，不写入磁盘 |
| `--help` | 打印语法参考 |

示例：

```bash
java -jar flora-ramet-0.1.jar src/main/templates src/main/java
java -jar flora-ramet-0.1.jar src/main/templates src/main/java --dry-run
```

## Maven 插件用法

```xml
<plugin>
    <groupId>com.demiurge</groupId>
    <artifactId>flora-ramet-maven-plugin</artifactId>
    <version>0.1</version>
    <configuration>
        <templatesDir>${project.basedir}/src/main/templates</templatesDir>
        <outputDir>${project.basedir}/src/main/java</outputDir>
    </configuration>
</plugin>
```

命令行调用：

```bash
mvn com.demiurge:flora-ramet-maven-plugin:generate \
  -Dramet.templatesDir=src/main/templates \
  -Dramet.outputDir=src/main/java
```

## JAR 说明

`flora-ramet-0.1.jar` 是 **自包含的 fat JAR**（嵌入了 `flora-root`），可在任意 JDK 26+ 环境下直接运行。

| 模块 | 用途 |
|------|------|
| `flora-ramet` | 核心引擎（shade 打包了 flora-root），可 `java -jar` 独立运行 |
| `flora-ramet-maven-plugin` | Maven Mojo 封装，不 shade（依赖由 Maven 提供） |

## include 机制

- include 默认相对于发起 include 的文件所在目录；以 `/` 开头的路径视为操作系统绝对路径
- 多个模板之间共享宏定义和变量上下文
- 循环 include 检测：重复包含同一模板抛异常，避免无限递归
