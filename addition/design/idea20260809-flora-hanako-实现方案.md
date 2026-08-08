# Flora Hanako 工程实现方案（方案 B 落地）

> 日期：2026-08-09
> 关联：`idea20260809-openhanako-java实现形态.md`（五种形态选型）、`explore20260809-openhanako基座能力评估.md`（基座能力清单）
> 工程：`cultivating/flora-hanako`

## 一、目标

在 `cultivating/` 下新建工程，按设计文档**方案 B（轻量嵌入式 Web）**复刻 openhanako 的核心能力与「暖纸」UI 风格：记忆、人格、工具、书桌、多会话，浏览器即 UI。

## 二、形态选择结论（来自 idea 文档）

方案 B 在「依赖少 / 最简单 / 好用」三维度同时占优：
- 依赖：Javalin 一个主库（含 Jetty）+ slf4j-api（+ 运行时 slf4j-simple），约 4~6 直接依赖。
- 简单：HTTP/WebSocket/SSE 开箱即用，不碰 UI 原生线程模型。
- 好用：现代浏览器呈现聊天 / 拖拽 / 画布，天然跨平台。

## 三、架构

```
浏览器 (web 静态资源)  ──HTTP/WS──►  Javalin (Jetty)
                                      ├─ REST: /api/agents /sessions /models /providers /memory /jians
                                      └─ WS:   /ws/chat  （流式对话事件）
                                              │
                                      HanakoEngine（编排）
                                      ├─ AiApi（flora-root：LLM 多 provider 流式）
                                      ├─ TaggedFactStore（记忆，倒排 + 命中排序）
                                      ├─ ToolRegistry（文件/终端/网页/记忆工具）
                                      └─ Jian / Session / Agent（领域模型）
```

## 四、关键实现要点

1. **流式对话**：`HanakoEngine.runTurn` 在一个虚拟线程里跑「LLM 流式推理 → 工具调用 → 回填 → 再推理」循环（最多 6 轮），通过 `EventSink` 增量广播 `text_delta / thinking_delta / tool_start / tool_end / turn_end`，对齐 openhanako ws-protocol。
2. **记忆**：`TaggedFactStore` 用 `Map<tag, Set<id>>` 倒排索引，检索按命中标签数降序（对应 fact-store.js 的 COUNT DISTINCT 排序），纯算法、与存储后端无关。
3. **工具与 PathGuard**：`read_file/write_file` 以工作目录为根做 `normalize().startsWith()` 前缀判定并 fail-closed 默认拒绝越界路径；完整四级访问控制列为后续（D1）。
4. **前端零构建**：原生 HTML/CSS/JS 打包进 jar 的 `web/` 资源，Javalin classpath 静态托管，复刻暖纸 token（`--bg:#F8F5ED`、`--accent:#537D96`）与布局（标题栏 / 侧边栏 / 聊天区 / 输入区 / 书桌抽屉 / 设置弹窗）。

## 五、与基座能力评估的对应关系

| 本工程实现 | 评估文档条目 |
|---|---|
| TaggedFactStore | B1 标签化事实存储 |
| 工具 + 工作目录白名单 | D1 PathGuard（子集） |
| web_fetch 协议校验 | D3 SSRF（子集） |
| 多会话按 Agent 隔离 | —— |
| 书桌 Jian | lib/desk（子集） |

尚未实现的高优先级基座能力（A2/A3/A4 cron 与心跳、C1 事件总线、F2 进程树杀灭等）已在「项目构造说明.md」列出为后续项。

## 六、测试

- `TaggedFactStoreTest`：入库、按命中数排序、删除更新索引。
- `ToolRegistryTest`：工作目录越界拒绝、注册表态、写后读往返。
- `HanakoServerTest`：启动 Javalin，验证 `/api/health`、`/api/agents`、`POST /api/sessions`、`/` 首页。
