# Changelog — Flora Hanako

## 0.1 (2026-08-09)

- 新增：基于方案 B（轻量嵌入式 Web）的 Java 版 openhanako 个人 AI 助理工程 `cultivating/flora-hanako`。
- 后端：Javalin（唯一主第三方依赖，内置 Jetty 提供 HTTP + WebSocket）承载 REST 路由与流式聊天 WebSocket；业务逻辑复用 flora-root 的 `ai.api`（LLM 多 provider）、`codec.json`（序列化）。
- 领域：Agent（独立人格/心识/模型）、Session、TaggedFactStore（标签化记忆，倒排索引 + 命中数排序）、Jian（书桌便签）。
- 工具：read_file / write_file / run_command（终端）/ web_fetch / remember，受 PathGuard 工作目录白名单约束。
- 前端：打包进 jar 资源的静态 Web 应用（index.html + app.js + styles.css），复刻 openhanako「暖纸 / Warm Paper」视觉与交互（侧边栏会话、流式对话、思考块、工具块、书桌抽屉、设置弹窗、斜杠命令 /xing /diary /remember）。
- 测试：TaggedFactStore、工具（含 PathGuard 越界拒绝）、Javalin 服务冒烟（health / agents / sessions / 静态首页）。
--
- 一键启动脚本：`action/devrun/devrun.cmd`（跨平台），构建 flora-root + flora-hanako 后用 `exec:java` 拉起服务，浏览器访问 `http://localhost:4567`（端口可参数指定）。
