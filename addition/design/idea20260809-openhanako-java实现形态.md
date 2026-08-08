# Java 版 openhanako 实现形态分析

> 日期：2026-08-09
> 范围：为复刻 openhanako（个人 AI Agent，记忆/人格/工具/多 Agent/书桌/定时任务/沙盒/多平台桥接）选择 Java 实现形态
> 前置文档：`explore20260809-openhanako基座能力评估.md`（flora-root 需要新增的通用基座能力）

---

## 一、openhanako 的功能构成与形态无关性

先厘清哪些决策**与实现形态无关**，避免形态选择时被误导：

| 能力 | 与形态的关系 |
|---|---|
| LLM 调用（OpenAI 兼容协议，SSE 流式） | 与形态无关。flora-root `ai.api` 已封装多 provider；任何形态都用 JDK HttpClient 或复用现有客户端 |
| 多平台桥接（Telegram/飞书/QQ） | 与形态无关。纯 HTTP API 调用 + 长轮询/WebSocket，任何形态可做 |
| 操作系统沙盒（Seatbelt/Bubblewrap） | 与形态无关。Java 用 `ProcessBuilder` 调外部 OS 命令 |
| 记忆/事实存储、定时任务、事件总线 | 与形态无关。属于 flora-root 基座能力（见前置文档），任何形态共用 |
| **UI 呈现**（聊天界面、书桌、画布） | **形态相关**。这是各形态差异最大的地方 |
| **进程形态**（桌面应用 vs 常驻服务 vs CLI） | **形态相关**。决定依赖、分发、体验 |

因此，**形态选择的本质是选 UI 呈现层与进程形态**。下面按"依赖从少到多"枚举五种形态。

---

## 二、五种实现形态

### 形态 A：零第三方依赖（纯 JDK + flora-root）

- **技术栈**：JDK 内置 `HttpClient`（LLM SSE 调用）+ `com.sun.net.httpserver`（嵌入式 HTTP）+ 自研 WebSocket server（JDK 无内置服务端实现）+ Swing（内置 GUI，界面陈旧）或 CLI + flora-root（codec/ai/api/文件存储基座）
- **直接依赖**：**零第三方**（仅 flora-root 内部模块）
- **优点**：
  - 依赖最少，单 jar 分发最简单；
  - 完全可控、无供应链风险，与 flora-root 零依赖哲学完全一致；
  - 最适合"先验证基座能力、把库做厚"的路径。
- **缺点**：
  - 开发量最大：WebSocket server、SSE 流式解析、前端轮询/推送都要自研（对应基座能力评估中的 C2 流式解析、E1 append-only 流等）；
  - UI 体验最差：Swing 是"能用但陈旧"，CLI 则无图形界面，与 openhanako 的图形化定位差距大；
  - 迭代效率低，人力成本高。
- **适用**：研究验证、库能力验证、嵌入式/极简分发场景。

### 形态 B：轻量嵌入式 Web（嵌入式 HTTP + 浏览器前端）【推荐】

- **技术栈**：Javalin（内置 Jetty：HTTP + WebSocket + SSE 全齐）+ 序列化复用 flora-root `codec.json`（不引 Jackson）+ slf4j + sqlite-jdbc（可选，或用 flora-root 文件存储基座）+ 静态 Web 前端（React/Vue/vanilla 打包进 jar 资源，随服务一起提供）
- **直接依赖**：Javalin 一个主库（含 Jetty）+ slf4j-api + 可选 sqlite-jdbc，约 4~6 个直接依赖
- **优点**：
  - **工程量最小**：HTTP/WebSocket/SSE 全部开箱即用，不碰 UI 原生线程模型；
  - **用户体验好**：浏览器就是 UI，现代前端生态（CodeMirror 聊天、拖拽书桌、画布）直接可用，天然跨平台（macOS/Windows/Linux）；
  - 与 flora-root 契合度高：ai.api（LLM）、codec.json（序列化）、文件存储基座（书桌/记忆）都能复用，WebSocket 是唯一需要第三方补的部分；
  - 迭代快，前端热更新。
- **缺点**：
  - 需要"常驻服务进程 + 浏览器访问"，桌面分发要配启动脚本（可选再套 Electron/Tauri 壳做桌面化）；
  - 前后端两套代码，前端工程量不可忽略。
- **适用**：**主力推荐**——"做起来最简单 + 好用"的平衡点。

### 形态 C：JavaFX 桌面应用

- **技术栈**：JavaFX（openjfx 的 javafx-controls/javafx-graphics 等模块，非 JDK 自带需额外依赖）+ 内部 HTTP server（复用 B 的服务层）或 JavaFX 直接调 ai.api + SQLite / flora 文件存储
- **直接依赖**：openjfx 多模块（约 10+ MB，平台相关）+ 内部 HTTP 库
- **优点**：
  - 真正的原生桌面窗口，体验最接近原版 Electron；
  - 无浏览器依赖，可离网分发。
- **缺点**：
  - 依赖重：JavaFX 不再是 JDK 一部分，各平台需打包对应 native 库；
  - 打包分发复杂（jlink/jpackage 定制运行时）；
  - JavaFX 线程模型（FX Application Thread）有学习成本，WebView/Canvas 能力弱于浏览器；
  - 若退回 Swing 则零依赖但界面更陈旧，体验退化为"能看不好看"。
- **适用**：确需原生桌面体验、愿意承担打包成本时。相比 B，工程量和依赖都更重，收益有限。

### 形态 D：Spring Boot 全家桶

- **技术栈**：spring-boot-starter-web + spring-boot-starter-websocket + Spring Data（可选）+ Spring AI 或 LangChain4j + H2/SQLite + 前端（同 B）
- **直接依赖**：数十个（starter 传染），传递依赖上百
- **优点**：
  - 生态最全，注解驱动，开发效率高（对熟手）；
  - 可维护性、团队协作、监控（Actuator）强。
- **缺点**：
  - **依赖最重**：启动慢（数百 MB 依赖），内存占用高，分发难；
  - 与 flora-root 的"零依赖自研"哲学冲突，`ai.api`/`codec.json` 等自研能力会被 Spring 生态（Jackson、RestTemplate）替代或并存造成双轨；
  - 约束强，定制"轻量个人应用"反而笨重。
- **适用**：企业级/大规模/多团队，对个人 AI Agent 应用明显过重。

### 形态 E：AI 框架优先（LangChain4j / Spring AI 为核心）

- **技术栈**：langchain4j（+ langchain4j-openai 等 model 绑定）+ 薄编排层 + UI 选 B/C 的前端
- **直接依赖**：langchain4j 核心 + model 绑定，中等偏多
- **优点**：
  - Agent 编排（tool calling、memory、RAG、message 聚合）框架代劳，写胶水代码最少；
  - 快速出原型。
- **缺点**：
  - 框架约束强，Agent 记忆/人格/多 Agent 编排等 openhanako 的核心差异化点难以在框架内自由定制；
  - 与 flora-root 冲突：memory/tool 编排正是基座能力评估要自建的部分，用框架会架空基座；
  - 依赖偏重，版本升级风险。
- **适用**：只想快速验证 Agent 效果，不追求长期可控。

---

## 三、对比矩阵

| 维度 | A 零依赖 | B 轻量Web | C JavaFX | D SpringBoot | E AI框架 |
|---|---|---|---|---|---|
| 第三方依赖数 | **0** | 少（4~6） | 中（openjfx） | 多（数十） | 中多 |
| 实现难度 | 最难 | **最简** | 中 | 中（模板多） | 中 |
| 用户体验 | 差（Swing/CLI） | **好**（现代浏览器） | 好（原生窗口） | 好 | 好 |
| 跨平台 | 全 | 全（浏览器） | 全（但需逐平台打包） | 全 | 全 |
| 桌面分发 | 最简（单 jar） | 中（进程+浏览器，可套壳） | 复杂（jlink/jpackage） | 复杂 | 复杂 |
| 与 flora-root 契合 | **最高** | 高 | 中 | 低（双轨） | 低（被框架架空） |
| 可控性/可定制 | **最高** | 高 | 中 | 低 | 最低 |
| 启动体积/内存 | 最小 | 小 | 中 | 大 | 中 |

---

## 四、三个核心问题的直接回答

1. **哪种依赖更少？** —— **形态 A**（零第三方依赖）。其次是 B（4~6 个直接依赖）。
2. **哪种做起来更简单？** —— **形态 B**。HTTP/WebSocket/SSE 开箱即用、不碰 UI 线程模型，前端用现代 Web 技术即可；A 要自研 WebSocket/SSE/前端，工程量最大。D/E 看似省力但引入框架配置与约束，对"个人应用"反而繁琐。
3. **哪种更好用（用户体验）？** —— **形态 B 或 C**。B 用浏览器呈现现代 UI（聊天、拖拽、画布生态成熟）；C 是原生桌面窗口。A 的 Swing/CLI 体验最差。

综合：**B 在三个维度同时占优**（依赖少、最简单、好用），是首选。

---

## 五、推荐结论

- **主力方案：形态 B（轻量嵌入式 Web）**。服务端用 Javalin（唯一的主第三方依赖），业务逻辑全部落在 flora-root 基座能力上（LLM 用 `ai.api`、序列化用 `codec.json`、书桌/记忆用文件存储基座、定时任务/事件总线用基座能力评估中的新增项），前端用现代 Web 技术构建，跨平台体验统一。
- **并行路径：形态 A 作为库验证**。在 flora-root 中先把高优先级基座能力（cron 解析与调度、事件总线、标签化事实存储、路径权限、append-only 文件流、流式增量解析）实现并测试——这些能力形态无关，A 是它们最直接的消费场景（WebSocket server、SSE 解析自研即是对 C2/E1 的真实检验）。
- **C 按需**：若后续明确要"原生桌面分发"（如向非技术用户交付 .dmg/.exe），可在 B 的服务层外包一层 JavaFX 或 Electron/Tauri 壳，服务层无需改动。
- **不推荐 D/E**：依赖重、约束强，与 flora-root 自研哲学冲突，且会架空我们计划自建的基座能力。

---

## 六、各形态引入依赖明细

| 形态 | 直接依赖 | 关键传递依赖 | 备注 |
|---|---|---|---|
| A | 无 | 无 | WebSocket server / SSE / 前端全部自研 |
| B | `io.javalin:javalin`、`org.slf4j:slf4j-api`、可选 `org.xerial:sqlite-jdbc` | Jetty（server/websocket/http2）、jackson（Javalin 默认带，可换 flora codec） | Javalin 自带 Jetty，一库提供 HTTP+WS+SSE |
| C | `org.openjfx:javafx-controls`、`javafx-graphics`（平台 classifier）、内部 HTTP 库 | 各平台 native 库 | 需 jlink/jpackage 打包；Swing 版零依赖但陈旧 |
| D | `spring-boot-starter-web`、`spring-boot-starter-websocket`、`spring-boot-starter-data-*`、可选 `spring-ai`/`langchain4j` | 上百个（tomcat/netty/jackson/spring-*） | 依赖树最庞大 |
| E | `dev.langchain4j:langchain4j`、`langchain4j-openai` 等 | 依赖 langchain4j 各模块 | 与 A/B/C 可叠加，但会架空自研基座 |

**注意**：无论哪种形态，`com.flora.ai.api` 已覆盖 LLM 多 provider（OpenAI/Anthropic/Gemini/DeepSeek），形态 B/C/D 均可直接复用，无需引入 Spring AI 或 LangChain4j 的模型绑定层——这是选择 B 优于 D/E 的技术依据之一。
