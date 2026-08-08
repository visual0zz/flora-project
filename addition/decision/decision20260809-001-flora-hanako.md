# 决策记录 001 — Flora Hanako 采用方案 B 且以类路径应用方式构建

> 日期：2026-08-09
> 模块：cultivating/flora-hanako
> 关联：idea20260809-openhanako-java实现形态.md

## 决策

1. 新建 Java 版 openhanako 工程，采用**方案 B（轻量嵌入式 Web）**：Javalin（唯一主第三方依赖）+ 浏览器前端，业务逻辑复用 flora-root 基座能力。
2. 工程**不使用 JPMS module-info**，以类路径（classpath）应用方式构建。

## 理由

- 方案 B 在「依赖少 / 做起来最简单 / 好用」三个维度同时占优（见 idea 文档第 3~5 节对比矩阵）。
- Javalin 6.3.0 是命名模块，其 Jetty 传递依赖构成完整 JPMS 模块图；若本工程声明 module-info 并 `requires io.javalin`，会把整个 Jetty 模块图纳入强制解析，触发大量自动模块名冲突与维护负担。以类路径应用方式构建可规避该复杂度，符合方案 B「做起来最简单」的初衷，且仍能正常消费 flora-root（模块化 jar 放在类路径上可用）。

## 影响

- `cultivating/flora-hanako` 的 `pom.xml` 不依赖父工程的 JPMS 约定，直接编译为普通 jar。
- 启动入口为 `com.flora.hanako.Main`，运行时由 `java -jar` 或 `exec:java` 拉起 Javalin 服务。
- 序列化避免引入 Jackson：用 `com.flora.codec.json.JsonBuilder` 自研 `json(ctx, obj)` 替代 `ctx.json()`。

## 备选

- 方案 A（零依赖自研 WebSocket/SSE）：工程量最大，UI 体验差，放弃。
- 方案 D/E（Spring Boot / LangChain4j）：依赖重、与 flora-root 自研哲学冲突，放弃。
