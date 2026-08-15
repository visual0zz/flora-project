# explore20260816-JavaFX打包困境与jlink原理剖析

> 针对 flora-sanctum 开发中遇到的"JavaFX 应用打包分发"问题，剖析 jlink 为何无法兼容 JavaFX，并梳理 JavaFX 打包的整体困境与可选出路。
> 关联设计：addition/design/flora-sanctum/07-UI设计（JavaFX 选型）。

## 结论速览

- **JavaFX 打包难是结构性**的：它含平台原生库、独立于 JDK、且渲染依赖系统窗口/图形环境。
- **jlink 不支持 JavaFX** 的根因是：**jlink 只链接纯 JDK 模块（JRT 文件系统内的类 + 无平台原生库的模块）**，而 JavaFX 模块含**平台相关原生库（.dylib/.dll/.so）**，且其模块描述符依赖不在 JRT 内的资源与 native 代码——jlink 的"静态链接 + 单平台运行镜像"模型无法承载。
- 由此衍生：`java -jar`（瘦包缺依赖）、fat jar（多 module-info 冲突）、jpackage（底层仍 jlink）都受限。

---

## 一、背景：JavaFX 打包为什么是"坑"

flora-sanctum 选用 JavaFX（见 07-UI设计）后，打包遇到连环问题：

1. **`java -jar` 跑不了**：JavaFX 是独立模块（Java 9+ 从 JDK 剥离），瘦 jar 不含依赖 → `NoClassDefFoundError`。
2. **fat jar 不行**：多命名模块（app/core/shell/root）各有 `module-info.class`，合并进一个 jar 违反"一个 jar 只能有一个 module-info"。
3. **jlink 不行**：`jlink: 自动模块不能用于来自 javafx-*.jar 的 jlink`。
4. **jpackage 不行**：底层仍走 jlink，同样失败。

核心卡点在 jlink。下面深入其原理。

---

## 二、jlink 的原理：它是什么

`jlink`（JDK 9+ 工具）生成**自定义运行时镜像**（runtime image），流程：

```
输入：一堆模块（JDK 模块 + 你的应用模块）
  ↓ 模块解析（ModuleFinder 从 module-path 找模块）
  ↓ 建立模块图（--add-modules 指定的根模块 + 其传递依赖）
  ↓ 静态链接（把所需模块的字节码/资源复制进新的运行时）
  ↓ 生成 JRT 文件系统（lib/modules 二进制镜像 + bin/ 可执行 + lib/ 动态库）
输出：一个精简的、只含所需模块的 Java 运行时目录
```

关键特征：
- **只包含模块图可达的模块**（裁剪掉用不到的 JDK 模块，体积小）。
- **输出是一个特定平台的原生运行时**（jlink 生成的 bin/java 是当前 OS 的可执行文件）。
- **模块必须是"可链接"的**：能在 JRT 文件系统里被静态打包。

---

## 三、jlink 为什么容不下 JavaFX：具体原理

### 3.1 jlink 链接的粒度：模块 = JRT 内的类 + 无平台差异的资源

jlink 的核心假设是：**被链接的模块是"平台无关的 Java 代码"**，可以静态地复制进统一的运行镜像。

- 纯 JDK 模块（java.base、java.desktop 等）的类字节码 + 资源，被 jlink 复制进 `lib/modules` 镜像。
- 平台相关的 JDK 原生库（libjava.so、libjvm.dylib 等）由 JDK 自带的 `--add-modules` 运行时自带，jlink 把它**从当前 JDK 复制**过来。

### 3.2 JavaFX 模块的问题：含平台原生库 + 不在 JRT 内

JavaFX 的 jar（javafx-controls-mac-aarch64.jar 等）包含：
- **平台特定原生库**：mac 的 `libprism_es2.dylib`、`libglass.dylib`，win 的 `prism_d3d.dll`、`glass.dll`，linux 的 `.so`。
- **模块描述符** `module-info.class`（声明 `module javafx.controls` 等）。

问题在于：

**(a) jlink 的"静态链接"只处理类字节码，不处理平台原生库的加载**
- jlink 把模块的 `.class` 复制进 `lib/modules`，但**原生库（.dylib/.dll/.so）是运行时经 System.loadLibrary / native 方法加载**的，不在 JRT 的类资源机制里。
- jlink 复制原生库时，**无法跨平台**——它不知道你要 mac 的还是 win 的，只能照搬当前平台的。
- 更根本：**JavaFX 的原生库分布在 jar 里，而非 JDK 运行时目录**，jlink 没有"把模块内 native 库正确嵌入运行镜像"的机制。

**(b) JavaFX 依赖系统图形/窗口环境，运行时不纯**
- JavaFX 的 glass 层要调用系统窗口系统（mac Cocoa、win Win32、linux X11/Wayland）。
- jlink 生成的运行时是"裁剪版 JVM"，它假设运行在目标平台的桌面环境——但 jlink **不验证/不携带**这些图形环境的原生依赖，只是照搬当前 JDK 的，跨平台时直接缺失。

**(c) 实际报错：`自动模块不能用于来自 ...javafx-*.jar 的 jlink`**
- 当我们把 javafx 的 **classifier jar**（如 `javafx-controls-23.0.1-mac-aarch64.jar`，有 `Automatic-Module-Name: javafx.controls`）放入 module-path，jlink 把它当**自动模块**。
- jlink 的模块解析要求：**被 `--add-modules` 链接的模块必须是"显式模块"（有真正的 module-info）**。自动模块（只有 Automatic-Module-Name 属性、无真实 module-info）**不能作为 jlink 的链接根**——jlink 报错拒绝。
- JavaFX 官方 jar 的 module-info 存在于**主 jar**（`javafx-controls-23.0.1.jar`），但**native 在 classifier jar**；打包时往往只把 classifier jar 放 module-path，导致被识别为自动模块 → jlink 拒绝。

### 3.3 一句话总结 jlink 不支持 JavaFX 的原理

> jlink 做的是"把**纯 Java 模块**静态链接成**单平台运行镜像**"。JavaFX 既不是纯 Java（含平台原生库、依赖系统图形环境），又常以自动模块形态出现——jlink 既无法静态嵌入其 native 库，也拒绝链接自动模块，所以**根本性不兼容**。

---

## 四、派生问题链

| 打包方式 | 失败点 | 原理 |
|---|---|---|
| `java -jar` 瘦包 | `NoClassDefFoundError` | 不含依赖 jar，JavaFX/第三方缺失 |
| fat jar（shade） | module-info 冲突 | 一 jar 只能一个 module-info；多命名模块合并破坏模块化 |
| jlink | 自动模块/native 库 | 只链接纯 Java 显式模块，JavaFX native 无法嵌入 |
| jpackage | 底层 jlink 失败 | jpackage 内部用 jlink 生成运行时，jlink 失败则整体失败 |

---

## 五、可选出路与取舍

### 5.1 保持 JavaFX（需预装 Java 的并列多 jar）
- **打包**：并列多 jar + `java --module-path lib -m ...` 启动脚本 + zip。
- **跨平台**：jar 平台无关；但 JavaFX 用平台 classifier（mac-aarch64 等），**每平台需各自构建**。
- **优点**：现代 Material UI，功能够用。
- **缺点**：需预装 Java 17+，分发包不是单文件/免装。

### 5.2 jpackage（每平台原生包）
- 理论上 jpackage 能处理 JavaFX，但**报第三方自动模块错误**（jgit/bc/sshd 也是自动模块，jlink 拒绝）。
- 需把第三方转为显式模块或特殊处理；**只在当前平台产出**（mac 上只能出 .dmg/.app）。

### 5.3 Java 后端 + 系统浏览器（推荐用于"一次打包"）
- 用 `java.awt.Desktop.browse` 打开系统浏览器，UI 走本地 HTTP（flora-sanctum 已有 SanctumHttpServer）。
- **纯 JDK、无 JavaFX** → **jlink 能打包精简 JRE** → **免装 Java + 真·一次打包**（jar 平台无关，各平台 jlink 生成对应运行时）。
- 缺点：UI 在系统浏览器里，非桌面窗口，体验偏网页。

### 5.4 WebView 内嵌（JWebView/JCEF）
- JWebView 依赖平台 WebView 绑定（每平台 JNI 库），**不解决"一次打包"**。
- JCEF（现成 Chromium 桥接）桌面内嵌，但每平台 native，打包仍每平台一次，且体积大。

### 5.5 纯 Java 自绘（JavaFX/Swing）能力边界
- JavaFX 的 Prism 有 GPU 加速（渐变/圆角/阴影），可做 Material；但复杂合成/大面积模糊/3D/现代 CSS 布局弱于浏览器。
- 密码管理器界面需求（圆角卡片/列表/表单/TOTP 倒计时）JavaFX **完全够用**。

---

## 六、对 flora-sanctum 的结论与建议

- **UI 能力**：JavaFX 够做密码管理器，无需为"漂亮"上 WebView。
- **打包分发**：这是 JavaFX 的硬伤。
  - 若**免装 Java + 一次打包**是硬需求 → 走 **5.3（Java 后端 + 浏览器，jlink 打包精简 JRE）**，复用现有 SanctumHttpServer。
  - 若**接受预装 Java 或每平台打包** → 保持 JavaFX（并列 jar + 脚本，或 jpackage）。
- **决策**：取决于"打包分发要求" vs "桌面窗口现代感"哪个优先。当前为开发运行态，`mvn javafx:run` 已可用。

## 关联文档
- JavaFX 选型：addition/design/flora-sanctum/07-UI设计
- 依赖（JavaFX classifier）：addition/design/flora-sanctum/08-依赖清单
- 外部密钥 HTTP 服务（可用于 5.3 浏览器 UI）：addition/design/flora-sanctum/02-加密设计
