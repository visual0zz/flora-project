# explore20260812-jwebview 调研笔记

## 目的

为 flora-sanctum（及其他 Java 桌面项目）评估 Java 侧 WebView 方案：克隆 jwebview，
梳理其依赖、判断哪些可替换为 flora-root，并评估"Tauri 式按 OS 调不同 web 框架"的实现难度。

## 调研对象

- 仓库：https://github.com/WasabiThumb/jwebview（Apache-2.0，2025-09 创建，Java 8+）
- 定位：跨平台 Java WebView 库。**不是** WebView2 专绑，而是包装 C 库 `webview`（webview/webview，
  其内部按 OS 选后端：Windows=WebView2/Edge、macOS=WKWebView、Linux=WebKitGTK）。
- 绑定方式：**JNI**（自带 C stub：bind.c/bridge.c/dispatch.c，显式拒绝 JNA 的性能损耗）；
  natives 按 win/linux/macos × amd64/arm64 分发，构建期用 gradle-cmake-plugin 编译。

## 依赖清单（运行时）

| 依赖 | 用途 | 位置 | 能否替换为 flora-root |
|---|---|---|---|
| org.jetbrains:annotations | 纯注解（compileOnly） | api/internals | 否（tag 语义注解不 1:1；compileOnly 零运行时成本） |
| com.google.code.gson:gson | JS↔Java 绑定用 JSON | **仅 bridge 可选模块** | ✅ `com.flora.codec.json.JsonUtil`（自带 JsonParser/JsonBuilder/JsonPath） |
| junit-jupiter / io.github.wasabithumb:xpdy | 测试（xpdy 是测试用 HTTP server） | 测试 | 无需替换 |

核心无第三方 Java 运行时依赖：本体 = JNI + C 原生库。

## 可替换项细化

1. **Gson → flora-root JsonUtil**：bridge 模块的 `GsonBindCallback`/`ReflectGsonBindCallback` 换用
   `com.flora.codec.json` 即可，低成本。
2. **OS/架构检测**：`Natives.calcHostOS` 内联的 os.name 判断 → `com.flora.os.OsUtil.isWindows/isLinux/isMac`；
   架构检测为 3 行 switch，可直接复用。
3. **JNI → FFM（Panama）**：flora-root 已有 `com.flora.os.natives.ffm.Native/NativeLib/CStruct`
   （基于 java.lang.foreign，Java 26 稳定）。jwebview 的 C stub 三部分均可等价映射：
   - downcall（Java→C）：`linker.downcallHandle`；
   - upcall 回调（C→Java，bind/dispatch）：`linker.upcallStub`；
   - 原生库提取：`Natives.load` 的 classpath/jar 资源解压逻辑可抽为 flora-root 工具。
   难度：**中等**。收益：删掉 JNI 头文件、C 编译矩阵（CMake + gradle-cmake-plugin + 每平台 CI），
   构建变纯 Java + 单份原生库。

## "Tauri 式"按 OS 独立绑 web 框架的难度

| 目标 | 途径 | 难度 |
|---|---|---|
| Windows WebView2 | COM vtable + HRESULT，FFM 绑 COM 繁琐 | 难 |
| macOS WKWebView | Objective-C runtime（objc_msgSend）via FFM | 极难、脆弱 |
| Linux WebKitGTK | C API via FFM | 中等 |

结论：**高难度且收益低**。跨平台分发已被 C `webview` 库解决（jwebview 或 webview_java 均已做到），
自研纯 Java 三平台绑定属于重复造轮子，且长期维护成本高。

## 与 flora-hanako（形态 B）的集成评估

flora-hanako 采用"嵌入式 HTTP（Javalin）+ 静态 Web 前端 + 浏览器即 UI"。替换为 jwebview
= 把"用户开浏览器"换成"应用内嵌 WebView 指向同一 localhost"，**前端代码零改动**。

- 改动：加依赖 → 建 WebView 窗口 `load(http://localhost:<port>/)` → 生命周期（关窗停服务）；
  可选 JS↔Java 桥（剪贴板/托盘/通知）。工作量几百行、1-2 天。
- 调试：jwebview 无 devtools，保留"真实浏览器打开 localhost"作调试/兜底入口。
- 代价：需 java.desktop 模块（Swing 嵌入），jlink 打包约 80MB 量级。
- 风险：库太新、Linux 依赖系统 WebKitGTK 版本、Windows 需 WebView2 运行时。

## 打包形态

- **fat jar 可行**：jwebview 以 jar 分发，原生库是 jar 内资源，运行时自动解压 + `System.load`
  （Natives 显式支持 jar 协议）；含 `natives-all` 的 fat jar 可跨平台。Swing 嵌入需 `java.desktop`
  （标准 JRE 自带）。限制仍是"运行需 JVM"。
- **免 JRE 单文件**：需 jpackage 安装包或 GraalVM 原生镜像，纯 jar 做不到。
- 平台运行时依赖：Windows 需 WebView2 运行时（Win11 默认、Win10 大多有）；Linux 依赖发行版
  WebKitGTK（版本旧则特性缺失、缺失则无法渲染）；macOS 用系统 WKWebView。Tauri（wry）同样如此。

## 建议

1. flora-sanctum 若走 Web UI：直接依赖 jwebview（或 webview_java），跨平台现成。
2. 想发挥 flora-root：bridge 换 JsonUtil（小事）；进一步用 FFM 重写 JNI 层（中等，收益=纯 Java 构建）。
3. 不推荐"Tauri 式每 OS 独立绑定"（难、脆、无收益）。
4. 风险提示：jwebview 极新（2025-09），API 面小、未久经考验；C `webview` 库在部分平台
   有已知局限（如某些平台键盘/焦点处理、Linux 依赖 GTK），接入前需按目标平台实测。
