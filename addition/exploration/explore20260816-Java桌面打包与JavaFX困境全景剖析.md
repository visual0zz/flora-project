# explore20260816-Java桌面打包与JavaFX困境全景剖析

> 从 flora-sanctum 开发中遇到的"JavaFX 应用打包分发"问题出发，系统剖析：
> JPMS 模块系统、jlink/jpackage 打包原理、JavaFX 原生库结构、fat jar 冲突、JWebView 跨平台、
> 浏览器渲染硬件加速、纯 Java GUI 能力边界，以及完整的打包方案对比与出路。
> 关联设计：addition/design/flora-sanctum/07-UI设计、08-依赖清单。

---

## 目录

1. 背景：从一次打包失败说起
2. 前置：JPMS 模块系统基础
3. 为什么一个 jar 只能一个 module-info（fat jar 失败原理）
4. jlink 原理与为何容不下 JavaFX
5. jpackage 原理与跨平台打包规则
6. javafx-maven-plugin 为什么需要 module-path 配置
7. "JRE 打包"：jlink/jpackage 会不会带 JRE
8. JWebView 为什么不能"一次打包"
9. 浏览器 UI 的硬件加速（纯 Java 画不出来的部分）
10. JavaFX 的完整缺陷清单
11. 打包方案全景对比
12. 对 flora-sanctum 的完整结论与出路

---

## 1. 背景：从一次打包失败说起

flora-sanctum 选用 JavaFX（见 07-UI设计）后，想打一个可执行 jar，结果连环失败：

1. `java -jar` 跑不了 → `NoClassDefFoundError: com/flora/shell/Command`。
2. 想打 fat jar（shade）→ 多模块 module-info 冲突。
3. 想用 jlink 打精简 JRE → `自动模块不能用于 jlink: javafx`.
4. 想用 jpackage → 底层 jlink 失败。

每一条背后都是一个独立的原理问题。下面逐一展开。

---

## 2. 前置：JPMS 模块系统基础

Java 9+ 引入模块系统（JPMS），把"包/类"组织成"模块"。

### 2.1 module-info.class 声明了什么

每个命名模块根目录有 `module-info.class`，声明：
- `module com.flora.sanctum.core`：模块名。
- `exports 包`：对外暴露的包。
- `requires 模块`：依赖的其它模块。
- `uses / provides`：服务发现。

### 2.2 三种模块形态

| 形态 | 说明 | 有无 module-info |
|---|---|---|
| **命名模块** | 有真实 `module-info.class`（flora-core/shell/root/app、javafx 官方 jar） | ✅ 有 |
| **自动模块** | 无 module-info，但 jar 有 `Automatic-Module-Name` 属性（jgit、bouncycastle、sshd 等第三方） | ❌ 无，靠属性名 |
| **未命名模块** | classpath 上的所有内容拼成的一个整体（classic 模式） | ❌ 无 |

### 2.3 module-path vs classpath

| | classpath | module-path |
|---|---|---|
| 语义 | 类查找，无双亲委派之外的模块边界 | 模块解析，`requires`/`exports` 生效 |
| 命名模块行为 | 被当未命名模块，`exports`/`requires` 全失效 | 作为独立命名模块 |
| 何时用 | 传统/未模块化 | Java 9+ 模块化应用 |

**关键**：用了命名模块 + `requires`，就必须 module-path 启动（`java --module-path ... -m`），否则模块边界失效或报"包不可访问"。

---

## 3. 为什么一个 jar 只能一个 module-info（fat jar 失败原理）

### 3.1 JVM 只认一个 module-info.class

JVM 在模块解析时，把**一个 jar 视为一个模块**，只看**该 jar 根路径下的那一个** `module-info.class`。

### 3.2 fat jar 合并导致冲突

Maven Shade 把多个 jar 解包合并进一个 fat jar。若每个源 jar 都有 module-info：
- 合并后 fat jar 里出现**多个 `module-info.class`**，而它们路径都是 `META-INF/versions` 或根路径 → 冲突。
- Shade 默认只保留其中一个，或报"module-info 重复"。
- 保留一个后：**其它模块的 module-info 丢失** → 它们退化成普通包，原命名模块之间的 `requires com.flora.sanctum.shell` 找不到对应模块 → 启动失败 `Module not found`。

### 3.3 结论

**"多个独立命名模块 → 单个 fat jar"在 JPMS 下不兼容**。要么放弃模块化（fat jar 用 classpath 运行，但封装失效），要么保留模块化（并列多 jar / jlink / jpackage）。

---

## 4. jlink 原理与为何容不下 JavaFX（重点）

### 4.1 jlink 是什么

`jlink`（JDK 9+）生成**自定义运行时镜像**（runtime image）。流程：

```
输入：module-path 上的模块（JDK 模块 + 应用模块）
  ↓ ModuleFinder 模块解析
  ↓ 建立模块图（--add-modules 根 + 传递依赖）
  ↓ 静态链接（把所需模块的类字节码/资源复制进新运行时）
  ↓ 生成 JRT 文件系统（lib/modules 镜像 + bin/java + lib/*.so/.dylib）
输出：精简的、只含所需模块的单平台 Java 运行时目录
```

特征：
- **只含模块图可达模块**（裁剪用不到的 JDK 模块，体积小）。
- **输出绑定当前平台**（bin/java 是当前 OS 的可执行文件）。
- **假设模块是"可静态链接的纯 Java"**。

### 4.2 为什么 jlink 容不下 JavaFX：三个层面

**(a) JavaFX 含平台原生库，不在 JRT 类机制里**
- JavaFX jar 里是 `.class` + 平台特定原生库（mac `libprism_es2.dylib`、win `prism_d3d.dll`、linux `.so`）。
- jlink 的静态链接只处理**类字节码/资源**，原生库是运行时经 `System.loadLibrary`/native 加载的，**不在 JRT 的类资源机制内**。
- jlink 无法把"模块内的 native 库"正确嵌入运行镜像，也没有跨平台选择 native 的机制。

**(b) JavaFX 依赖系统图形/窗口环境**
- glass 层调系统窗口（mac Cocoa、win Win32、linux X11/Wayland）。
- jlink 生成"裁剪版 JVM"，不验证/不携带这些图形环境依赖，跨平台直接缺失。

**(c) classifier jar 是自动模块，jlink 拒绝链接**
- JavaFX 的 native 在 classifier jar（`javafx-controls-23.0.1-mac-aarch64.jar`，`Automatic-Module-Name: javafx.controls`）。
- jlink 的 `--add-modules` 要求链接**显式模块**（有真实 module-info）。自动模块**不能作为链接根** → jlink 报错 `自动模块不能用于 jlink: javafx.*`。
- 官方主 jar 有 module-info，但 native 在 classifier jar；打包时常用 classifier jar，被识别为自动模块 → 被 jlink 拒绝。

### 4.3 一句话总结

> jlink 做的是"把**纯 Java 显式模块**静态链接成**单平台运行镜像**"。JavaFX 既不是纯 Java（含平台原生库、依赖图形环境），又常以自动模块形态出现——jlink 既无法嵌入其 native，又拒绝自动模块，**根本性不兼容**。

---

## 5. jpackage 原理与跨平台打包规则

### 5.1 jpackage 是什么

JDK 14+ 的原生打包工具，内部**基于 jlink** 生成运行时，再打成本地安装包（.dmg/.app/.exe/.msi/.deb/.rpm）。

### 5.2 为什么 jpackage 也失败

jpackage 生成运行时仍走 jlink → 遇到 JavaFX（jlink 不兼容）或第三方自动模块（jgit/bc/sshd 也是自动模块，jlink 拒绝作为链接根）→ 整体失败。

### 5.3 跨平台规则：一次只能打当前平台的包

- **jpackage 只能在当前 OS 生成该 OS 的安装包**：mac 上只出 .dmg/.app，win 只出 .exe/.msi，linux 只出 .deb/.rpm。
- **不能交叉打包**（mac 上不能打 win 包）。
- **架构也要分**：Apple Silicon（aarch64）与 Intel（x64）的 mac 包不同，需在对应架构机器上打。
- 多平台发布需 CI（GitHub Actions 等，每平台一个 job）。

### 5.4 结论

jpackage = jlink 运行时 + 原生安装包。因底层 jlink 限制，对 JavaFX + 第三方自动模块的项目同样受限；且严格单平台构建。

---

## 6. javafx-maven-plugin 为什么需要 module-path 配置

### 6.1 谁依赖谁

```
com.flora.sanctum.app
  ├─ requires com.flora.sanctum.core (命名模块)
  │    ├─ requires org.eclipse.jgit       (自动模块)
  │    ├─ requires org.bouncycastle.provider (自动模块)
  │    └─ requires org.eclipse.jgit.ssh.apache (自动模块)
  ├─ requires javafx.controls (JavaFX 模块)
  └─ requires javafx.graphics
```

### 6.2 为什么必须 module-path

JPMS 里，命名模块 `requires` 另一个模块，运行时该模块必须在 **module-path** 上，否则 JVM 启动时模块解析失败（`Module ... not found`）。

javafx-maven-plugin 默认只把**项目自身模块 + JavaFX** 放 module-path，**不自动放第三方 jar**（jgit/bc/ssh.apache）。所以：
- 默认运行时，core 的 `requires org.eclipse.jgit` 找不到 jgit 模块 → 启动失败。
- 需显式配置 `modulePath = target/lib`（把所有依赖 jar 复制进去），让 jgit 等自动模块出现在 module-path 上，`requires` 才能解析。

### 6.3 结论

因为**用了 JPMS 命名模块 + requires**，运行必须 module-path；javafx-maven-plugin 默认不全放第三方 jar，故要配置 modulePath。

---

## 7. "JRE 打包"：jlink/jpackage 会不会带 JRE

- **jlink/jpackage（默认）会打包精简 JRE**：jlink 生成的自定义运行时就是"裁剪版 JRE"，只含所需模块，体积远小于完整 JDK。用户免装 Java。
- **jlink 生成的 JRE 绑定当前平台**：不能跨平台复用。
- **但 JavaFX 项目 jlink 根本走不通**（见第 4 节），所以"免装 Java 的精简 JRE"对 JavaFX 应用不可得。

### 7.1 现实：能打精简 JRE 的前提

- **纯 JDK 应用**（无 JavaFX，如 Swing/AWT 或纯后端）→ jlink 能打精简 JRE → 免装 Java 的一次打包可行。
- **JavaFX 应用** → jlink 不支持 → 免装 Java 不可得（除非 jpackage 特殊处理且解决自动模块）。

---

## 8. JWebView 为什么不能"一次打包"

### 8.1 WebView 系统自带，但 Java 不能直接用

- 系统自带 WebView（mac WKWebView、win WebView2、linux WebKitGTK），但它们是**系统原生组件**。
- Java 不能直接 `new` 原生 WebView，需**桥接库**（JWebView/JCEF）经 JNI/FFM 调原生。
- 桥接库**每平台一个 JNI 绑定**（mac 调 ObjC/NSView、win 调 COM/HWND、linux 调 GTK）——**不是纯 Java，跨平台仍需各平台原生库**。

### 8.2 为什么"系统自带"反而麻烦

| 点 | 情况 |
|---|---|
| 系统有 WebView | ✅ 是 |
| Java 直接能用 | ❌ 需桥接库 |
| 桥接库跨平台？ | ❌ 每平台一个 JNI 绑定 |
| 一次打包？ | ❌ 每平台带对应原生绑定 |

### 8.3 真正利用系统 WebView 的正解

- **Java 后端 + `java.awt.Desktop.browse(url)` 打开系统浏览器**：浏览器就是系统 WebView 应用，Java 标准 API 直接调，**无需任何桥接库**。
- 纯 JDK（无 JavaFX/无 JNI）→ jlink 能打精简 JRE → 真·一次打包 + 免装 Java。
- 代价：UI 在系统浏览器里（网页体验），非桌面窗口。

### 8.4 若坚持桌面内嵌 WebView

- JCEF（现成 Chromium 桥接，跨平台 jar）是"自己写 FFM 桥接"的省力替代；但 JCEF 每平台仍有 native，打包仍每平台一次，且体积大。
- **用 FFM 自己桥 WKWebView/WebView2/WebKitGTK** 是**重写 GUI 框架级**工作量（ObjC runtime/COM/GTK 事件循环 + 原生窗口），对密码管理器是过度工程，不推荐。

---

## 9. 浏览器 UI 的硬件加速（纯 Java 画不出来的部分）

浏览器渲染页面的 GPU 管线做了大量工作：

| 环节 | 浏览器 | 纯 Java 要自己做 |
|---|---|---|
| 布局 | HTML/CSS 盒模型/flex/grid | 无内置，靠库或自写 |
| 光栅化 | 文字/矢量/图片转像素 | 字体渲染、矢量路径 |
| 图层合成 | 分层 + GPU 合成（Skia） | 合成器 |
| GPU 加速 | 纹理上传、shader、合成（WebGL/CSS 3D/滤镜） | GPU 上下文 + shader |
| 文本 | 亚像素渲染、HarfBuzz 级排版、多语言回退 | FreeType 级排版 |
| 动效 | 合成器动画、垂直同步 | 帧调度 + VSync |

### 9.1 纯 Java（JavaFX）能做到与做不到

| 能力 | JavaFX |
|---|---|
| 圆角/阴影/渐变/简单动效（Material） | ✅ Prism 有 GPU 加速 |
| 复杂滤镜/大面积模糊/毛玻璃 | ⚠️ 勉强/性能差 |
| 独立合成器/VSync 帧循环 | ❌ 无，大量控件或复杂动效掉帧 |
| 3D/WebGL | ❌ 不实用 |
| 现代 CSS 布局（flex/grid/transform/filter） | ❌ JavaFX CSS 只覆盖控件样式 |
| 复杂文本排版（连字/可变字体/emoji） | ⚠️ 有限 |

### 9.2 结论

- 浏览器"漂亮"来自成熟渲染引擎 + CSS + 生态，纯 Java 复刻不现实。
- 密码管理器界面（圆角卡片/列表/表单/TOTP 倒计时）**JavaFX 完全够用**，不需要浏览器级渲染。

---

## 10. JavaFX 的完整缺陷清单

### 10.1 打包分发（最大痛点）
- 平台原生库 → 每平台 classifier jar。
- jlink 不支持（第 4 节）→ 无法免装 Java 精简 JRE。
- jpackage 底层 jlink 受限。
- `java -jar` 瘦包缺依赖。

### 10.2 渲染
- Prism 有 GPU 加速，但复杂合成/大面积模糊/3D/WebGL 弱。
- 无独立合成器/VSync → 复杂动效/大量控件掉帧。

### 10.3 布局与 CSS
- CSS 只覆盖控件样式，无 flex/grid/transform/filter。
- 布局靠手动堆 VBox/HBox，复杂响应式麻烦。

### 10.4 文本
- 中英文混排、复杂字体、emoji、可变字体效果弱。

### 10.5 生态与控件
- 现代控件/UI 库少，官方控件较基础。
- 社区活跃度远低于 Web。

### 10.6 线程模型
- UI 必须在 FX Application Thread，后台更新要 `Platform.runLater`，易踩线程错误。

### 10.7 性能
- 大量节点/大列表性能差，虚拟化要自己做。

### 10.8 对照：不用 JavaFX（Swing/AWT）
- Swing/AWT 是 JDK 自带 → 无 classifier、无 module-path 麻烦。
- **jlink 能打包**（纯 JDK）→ 免装 Java 的便携包容易。
- 但 UI 现代度不如 JavaFX/Web。

---

## 11. 打包方案全景对比

| 方案 | 免装 Java | 一次打包 | 跨平台 | 模块化 | UI 形态 | 适用 |
|---|---|---|---|---|---|---|
| 瘦 jar `java -jar` | ❌ | ❌ | ✅ | ✅ | JavaFX | 仅开发 |
| fat jar（shade） | ❌ | ⚠️ | ✅ | ❌ 破坏 | JavaFX | 简单应用 |
| **并列多 jar + 脚本** | ❌ 需预装 | ✅ | ⚠️（jar 无关，javafx classifier 每平台） | ✅ | JavaFX | **当前可行** |
| jlink 精简 JRE | ✅ | ✅ | ⚠️ 每平台 | ✅ | 非 JavaFX | 纯后端/Swing |
| jpackage | ✅ | ✅ | ❌ 每平台 | ✅ | JavaFX | 每平台原生包 |
| Java 后端 + 浏览器 | ✅（jlink） | ✅ | ✅ | ✅ | 浏览器 | **一次打包最优** |
| WebView 内嵌(JCEF) | ❌ | ❌ | ❌ 每平台 | ⚠️ | 桌面Web | 富 UI |

---

## 12. 对 flora-sanctum 的完整结论与出路

### 12.1 UI 能力
JavaFX 够做密码管理器（Material/主题/托盘/TOTP 倒计时），无需为"漂亮"上 WebView。

### 12.2 打包分发（决策点）
- **当前可行**：并列多 jar + 启动脚本 + zip（需预装 Java 17+）。jar 平台无关，但 JavaFX 用平台 classifier，跨平台需各平台构建。
- **若要免装 Java + 一次打包**：需放弃 JavaFX，走 **Java 后端 + 系统浏览器（jlink 打精简 JRE）**，复用已有 SanctumHttpServer。这是 JavaFX 打包困境的最实用解。
- **若坚持桌面 JavaFX**：接受"需预装 Java + 每平台打包"或"jpackage 每平台原生包"。

### 12.3 决定因素
**"打包分发要求" vs "桌面窗口现代感"哪个优先**：
- 打包优先 → 浏览器方案（jlink 一次打包）。
- 现代桌面优先 → JavaFX，接受打包复杂度。

### 12.4 当前状态
flora-sanctum 处于开发运行态，`mvn javafx:run` 已可用；发布方式待定（上述决策）。

---

## 关联文档
- JavaFX 选型：addition/design/flora-sanctum/07-UI设计
- 依赖（JavaFX classifier）：addition/design/flora-sanctum/08-依赖清单
- 外部密钥 HTTP 服务（可支撑浏览器方案）：addition/design/flora-sanctum/02-加密设计
- 模块划分（命名模块）：addition/design/flora-sanctum/01-总体架构
