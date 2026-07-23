# flora-tangle 代码混淆器

`flora-tangle` 是 `flora-project` 下的一个代码混淆器：读入一个 jar，输出一个 jar，
过程中把**类名**和**成员名（字段/方法）**替换成无意义的短名字，从而提高反编译后阅读与逆向的难度。

设计目标：尽量不依赖 `flora` 以外的第三方包。混淆器底层所需的 class 文件解析/重写能力
全部自研，放在 `flora-root` 的 `com.flora.classfile` 包中，因此本模块只依赖 JDK 标准库与 `flora-root`。

---

## 1. 构建

在项目根目录（`flora-project`）下执行：

```bash
cd flora-project
mvn -pl flora-tangle -am package
```

产物：

| 文件 | 说明 |
| --- | --- |
| `flora-project/flora-tangle/target/flora-tangle-0.1.jar` | 混淆器本体（模块化 jar） |
| `flora-project/flora-root/target/flora-root-0.1.jar` | 运行所需的依赖（class 文件库） |

---

## 2. 命令行使用

`flora-tangle` 是一个 JPMS 模块化 jar，运行时需要把依赖 `flora-root` 一起放到模块路径上：

```bash
java --module-path flora-tangle-0.1.jar:flora-root-0.1.jar \
     -m com.flora.tangle/com.flora.tangle.Tangle \
     <输入.jar> <输出.jar> [--keep <类前缀> ...]
```

参数说明：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `<输入.jar>` | 是 | 待混淆的 jar 路径 |
| `<输出.jar>` | 是 | 混淆后 jar 的写出路径（若已存在会被覆盖） |
| `--keep <类前缀>` | 否 | 保留指定类内部名前缀不被重命名，可重复多次 |

示例：

```bash
# 基本用法：混淆 app.jar，结果写到 app-obf.jar
java --module-path flora-tangle-0.1.jar:flora-root-0.1.jar \
     -m com.flora.tangle/com.flora.tangle.Tangle \
     app.jar app-obf.jar

# 保留程序入口类及其所在包，避免 main 类被改名导致无法启动
java --module-path flora-tangle-0.1.jar:flora-root-0.1.jar \
     -m com.flora.tangle/com.flora.tangle.Tangle \
     app.jar app-obf.jar --keep com/example/Main --keep com/example/api
```

成功后会打印一行：`混淆完成: app.jar -> app-obf.jar (N 字节)`。

---

## 3. 编程调用（API 用法）

在依赖了 `flora-root` 与 `flora-tangle` 的模块/项目中，直接调用 `Obfuscator`：

```java
import com.flora.tangle.Obfuscator;

import java.nio.file.Files;
import java.nio.file.Path;

byte[] input = Files.readAllBytes(Path.of("app.jar"));

// 基本混淆
byte[] output = new Obfuscator().obfuscate(input);

Files.write(Path.of("app-obf.jar"), output);
```

保留特定类（例如程序入口、需要被外部反射调用的类）：

```java
Obfuscator obf = new Obfuscator();
obf.keepClassPrefix("com/example/Main");   // 该类及其同前缀类不被重命名
obf.keepClassPrefix("com/example/api/");   // 也可用包前缀
byte[] output = obf.obfuscate(input);
```

`Obfuscator` 公开方法：

| 方法 | 说明 |
| --- | --- |
| `byte[] obfuscate(byte[] inputJar)` | 执行混淆，返回输出 jar 的字节 |
| `void keepClassPrefix(String prefix)` | 登记一个“保留类”前缀，匹配的类不参与重命名 |

---

## 4. 混淆规则

- **类名**：默认对 jar 内所有应用类重命名为形如 `a/xxxx` 的短名字（统一收进一个扁平包下）。
  `module-info` 与 `java.*` 前缀的类不会被重命名。
- **成员名**：字段与方法的名字被替换为短名字；`<init>` / `<clinit>` 等特殊方法名**保留**（改名会导致无法构造/初始化）。
- **引用同步**：重命名是“一处改名、处处同步”的——所有对该类/成员的引用（字节码指令、
  常量池 `Fieldref`/`Methodref`、描述符、泛型签名、MANIFEST 的 `Main-Class`）都会被同步改写，
  因此混淆后的 jar 仍能正常加载与运行。
- **资源文件**：非 `.class` 的资源（含 `META-INF/MANIFEST.MF`）原样拷贝；仅当 `Main-Class`
  指向的类被重命名时，清单中的对应行会被更新。

---

## 5. 工作原理（简述）

`Obfuscator` 分两遍处理，以保证引用一致性：

1. **第一遍（建映射）**：扫描 jar 内每个 class，解析出类名与全部成员，建立
   “旧类名 → 新类名”“(所属类, 成员名, 描述符) → 新成员名”两张映射；同时把已有名字登记到
   名字生成器，避免新名字与旧名字撞车。
2. **第二遍（应用）**：逐个 class 应用全部映射并写回字节：
   - 类名重命名会改写常量池与描述符/签名里的文本；
   - 成员重命名会改写声明处，并通过为引用新建独立的 `NameAndType` 并重指
     `Fieldref`/`Methodref`，从而**不影响同名但属于其它类的成员**；
   - 非 class 资源原样拷贝，MANIFEST 中 `Main-Class` 若被改名则同步更新。

底层的 class 文件解析/重写由 `flora-root` 的 `com.flora.classfile` 完成：
重命名时**保持常量池索引稳定**，指令与属性体中的常量池引用无需逐条改写即可保持有效。

---

## 6. 支持范围与限制

支持：

- Java 8+ 的 class 文件（含模块化的 `module-info` 类，仅原样保留不重命名）。
- 类名、字段名、普通方法名混淆，以及所有常量池/描述符/签名引用的同步。
- 作为模块化 jar 运行（JPMS）。

当前版本的限制（后续可增强）：

- **字符串常量混淆**：源码里写死的类名/方法名字符串（如 `Class.forName("com.x.Y")`、
  反射调用）不会被改写，仍是明文。
- **控制流/逻辑混淆**：不做指令级混淆、花指令、字符串加密等。
- **资源文件名**：资源与类名同名的资源（如 `META-INF/services/...`）文件名不改。
- **复杂动态特性**：`invokedynamic`（如 lambda、`StringConcatFactory`）、注解、序列化
  等场景已尽量兼容，但对极端用法可能存在未覆盖的边角情况；生产使用前建议对混淆产物做完整冒烟测试。
- **包名保留**：被重命名的类统一收进扁平短包，原始包结构信息会被抹除（这符合混淆目的，
  但也意味着需要 `--keep` 显式保留入口类/对外 API 类）。

---

## 7. 典型工作流建议

1. 确定程序入口（`Main-Class`）与需要被框架/反射发现的类（如 Spring Bean、SPI 实现等）。
2. 用 `--keep` 把这些类（及其包）加入保留名单。
3. 运行混淆，得到 `app-obf.jar`。
4. 对 `app-obf.jar` 做完整功能/集成冒烟测试；若某些类因反射失败，补充 `--keep` 后重试。
