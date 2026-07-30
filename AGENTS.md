## 项目架构

**flora-project**：Java 26 多模块 Maven 项目（JPMS）

```
flora-project/            -- 根 POM（pom 打包类型，Java 26）
├── absent/               -- 不应纳入版本控制的文件（已 gitignore）
│   └── tmp/              -- 临时文件
├── action/               -- 开发工作流脚本（测试、构建、重新生成）
├── addition/             -- 工具脚本、配置、报告
│   ├── codereview/       -- 代码审查报告
│   │   └── review{YYYYMMDD}-{编号}-{主题}.md
│   ├── decision/         -- 决策记录
│   │   └── decision{YYYYMMDD}-{编号}-{模块}.md
│   ├── design/           -- 方案/设计文档
│   │   └── idea{YYYYMMDD}-{编号}-{主题}.md
│   └── exploration/      -- 算法/协议/技术的详细剖析笔记
│       └── explore{YYYYMMDD}-{主题}.md
├── flora/                -- 聚合模块（root + garden）
├── flora-benchmark/      -- JMH 微基准测试
├── flora-garden/         -- 占位模块
├── flora-osmetes/        -- 源码分析与校验库
├── flora-ramet/          -- 基于模板的代码生成引擎
├── flora-root/           -- 零依赖工具库
├── flora-tangle/         -- Java 字节码混淆器
│   └── testbed/          -- Tangle 集成测试环境
└── plugins/              -- IDE 和构建工具插件
    └── maven-plugins/    -- Maven Mojo 插件
        ├── flora-osmetes-plugin/   -- 编码检查 Mojo
        └── flora-ramet-plugin/     -- Ramet 代码生成 Mojo
```

## 构建与测试命令

- `./action/test.cmd` — 运行所有单元测试（Maven，快速）
- `./action/test-slow.cmd` — 慢测试：标记了 `@Tag("slow")` 的 Maven 测试
  以及 IntelliJ 插件沙箱 fixture 测试
- `./action/produce.cmd` — 完整构建（跳过测试）
- `./action/regenerate.cmd` — 从模板重新生成代码
- 
- `./push.cmd "提交信息"` — 推送到 `addition/config/remoteRepoList.txt` 中列出的所有远程仓库。
  这是一个跨平台脚本：`.cmd` 后缀仅为约定——它同时适用于 Windows（cmd.exe）
  和 Unix 类 Shell（bash/zsh，通过 shebang + goto 回退机制）。

## AI 行为规范

- **每完成一个任务后提交并推送**：完成一个实质性任务并验证测试通过后，提交变更并通过 `./push.cmd` 上传。不要将无关工作合并到一次提交中。
- **Git 提交**：提交时在提交信息中包含你的 AI 代理名称（例如 `feat(ramet) by AgentName: add numberFormat function.`）。
- **代码审查**：将 AI 生成的代码审查报告保存在 `addition/codereview/` 中。命名格式：`review{YYYYMMDD}-{编号}-{主题}.md`。
- **方案设计**：将 AI 生成的方案或设计文档保存在 `addition/design/` 中。命名格式：`idea{YYYYMMDD}-{编号}-{主题}.md`。
- **决策记录**：每当 AI 做出决策（如技术选型或实现方案）时，记录到 `addition/decision/` 中。命名格式：`decision{YYYYMMDD}-{编号}-{模块}.md`。
- **更新日志**：如果子模块包含 `CHANGELOG.md` 文件，每次代码改动后更新它，反映修改、新增或删除的内容。
- **技术探索**：将 AI 撰写的算法/协议/技术详细剖析笔记保存在 `addition/exploration/` 中。命名格式：`explore{YYYYMMDD}-{主题}.md`。
- **所有控制台脚本必须使用纯英文。** 这包括所有跨平台脚本，如 `*.cmd`、`*.sh`、`*.ps1` 以及从终端调用的任何 Shell 辅助脚本。**不要**在代码、注释、`echo`/`printf` 字符串或标签中使用中文（或任何非 ASCII 字符）。
  原因：在 Windows 上，`cmd.exe` 以系统代码页（如 GBK）读取 `.cmd` 文件。UTF-8 中文会使 `for /f "eol=#"` 和其它解析逻辑静默跳过所有行，导致脚本失效。提交信息仍可包含中文——那里的乱码无害。
- **`addition/config/` 下的所有文件必须使用纯英文**（仅 ASCII），包括 `remoteRepoList.txt`、`pushConfig.txt`、
  `tagPrefixes.txt` 等文件中的注释。同样的代码页陷阱：被 `cmd` 读取的配置文件中的中文注释可能导致整个文件读取失败。键、值和注释全部使用英文。

## 代码风格要求

- **基础工具模块采用两层语义包结构**：像 `flora-root`（零依赖基础库）这样的模块必须按两层语义层次组织包：
  - 第一层表示宽泛的类别（如 `com.flora.collect`、`com.flora.text`）。
  - 第二层表示该类别的更具体的子类别。
  - 只通过 `module-info.java` 的 `exports` 导出供外部代码消费的包。当一个类别包同时包含可导出的和内部的类型时，将内部类型移到专门的 `impl` 子包中（如 `com.flora.collect.impl`），父包只保留公开 API。
- **注释描述契约和行为，而非历史**：代码注释必须聚焦于代码的*约定、实际运行时行为和外部可观察的功能*。不要用注释记录演变历史、变更日志或描述当前实现与替代方案的差异。注释应向前看，描述代码*是什么以及做什么*，而非它是如何演变成这样的。
