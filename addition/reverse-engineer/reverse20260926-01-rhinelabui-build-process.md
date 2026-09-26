# RhineLabUI 开发流程推测（逆向分析）

> 分析对象：`absent/otherprojects/RhineLabUI`（GitHub `LBEILC/RhineLabUI`，main 分支，2026-09 快照）
> 分析方式：只读研读仓库内 `README.md`、`DESIGN.md`、`AGENTS.md`（带日期的决策日志）、`src/` 模块与 `verification/` 对照记录，未运行、未修改其代码。
> 说明：本文是**基于代码与文档的推测**，并非作者公开的开发日志；凡标注"推断"处均为推理，非确证事实。

## 一、项目定位

把《明日方舟》特别映像「莱茵生命：访问」的终端界面做成**可操作的三维前端复刻**。

- 技术栈：**TypeScript + Three.js + Vite**，纯静态、无后端、无 API Key，可托管到 Vercel / Cloudflare Pages。
- 核心流程：白底开场 → 五列循环档案阵列 → 抽取一份档案 → 玻璃与正文同步解密 → 进入 360° 独立查看器观察内构。
- 代码由 GPT-6 Astra 协助完成，三维模型经 Blender MCP 制作。

## 二、主要设计思路

### 1. 参考驱动的"原生实现"——逐帧还原，不用现成动效框架
`AGENTS.md` 反复强调：以原 PV 的 **5–40 秒**为视觉/动效参考，"尽量逐像素还原"；明确**不使用前端或动效 Skill**，由代理自选技术栈；美术资源必须经 Blender MCP 生成并保留可复现脚本。设计核心是"复刻约束"而非"自由创作"——所有运动曲线、相机轨迹都有数值规范。

### 2. 视觉/运动参数写进 `DESIGN.md` 当成硬约束
不是模糊的"好看"，而是可验证常量：
- 基准 `1920×1080 / 25fps`，16:9 为视觉基准、等比例适应窗口。
- 相机：31.48 秒处方位角 59°、仰角 19°、垂直取景 7.33 世界单位；特写距离 72。
- 配色：暖灰白底、黑字、暖杏金选中信号；灯光统一在 `src/archive-lighting.ts`（曝光 1.0、环境 0.52、暖白主光 1.7）。
- 运动：临界阻尼保留速度（`archive-loop.ts`）、波浪基线 `aeddd8f`、升降阻尼 4.2。
- 几何：卡片宽 5 / 高 3.7 / 行距 0.62，端面深度 0.31；抽取只变高度，构图交给相机。

### 3. "内容/几何分离"的循环阵列模型
40 份档案（5 类 × 8 份）按周期映射到连续的行列坐标（`src/data.ts` + `src/archive-loop.ts`），方向操作每次移动一个物理位置，跨首尾沿原方向续接；用"可见窗口 + 外围卡片补位"（`9×32` 候选）让有限内容无限循环，且切列保留该列记忆。这是对原片 160 个阵列位置的**交互化改造**。

### 4. 真实透射玻璃 + 实时渲染
用 Three.js physical transmission 做磨砂→清晰玻璃（`src/decryption.ts`），解密线沿对角线合拢（参考 34–39 秒），内部双环结构与外部盖板连续插值颜色/粗糙度/透光率。自适应阵列按实际镜头与视口裁剪候选数量。

### 5. 验证优先的工作流（最大架构特征）
每个功能都配 `verification/*.md` + `reference/*.html` 对照工具 + `npm run check:*` 脚本（motion / loop / appearance / decryption / shell / quality 等）。`reference/review.html` 在开发环境叠加原片逐帧比对；产品构建**不嵌入原片**。本质是"用对照和断言把复刻质量锁死"。

### 6. 数据驱动 + 可复现资产管线
`content/archives.json` 是页面与 TXT 导出的唯一数据源；`art/*.py`（Blender 脚本）从自身位置定位项目、重新生成 GLB，保证模型可复现。

### 7. 多形态复用同一套原生实现
同一代码通过构建模式分出：网页版（PWA）、Wallpaper Engine 壁纸版（`workbench` / `wallpaper-*` 系列，含 3D 卸载、自定义壁纸、HUD 曲面视差）、Cloudflare 静态部署——共用视觉规范与渲染，仅启用/关闭部分模块。

## 三、推断的构建顺序

依据 `README` 工程结构顺序、`AGENTS.md` 的日期决策链（09-09 → 09-15）与模块依赖关系推断：

| 阶段 | 推断内容 | 支撑依据 |
|---|---|---|
| **0. 地基** | 技术栈搭建（Vite/TS/Three）、`content/archives.json` 40 份数据、Blender 基础模型 GLB | README 工程结构；AGENTS "美术资源必须通过 Blender MCP" |
| **1. 开场白底动画** | `boot.ts` + `boot-motion.ts` + `boot-tracks.ts`（逐字输入 / 圆环 / Logo 绘制 / 身份验证） | AGENTS 最早多条围绕"前段 2D 动效逐帧修订" |
| **2. 三维循环阵列** | `scene.ts` + `archive-loop.ts`（五列循环、抽取/归位、波浪、列内记忆） | DESIGN.md "循环档案阵列"；AGENTS 大量相机/几何校准先于细节 |
| **3. 抽取 + 解密 + 详情** | `decryption.ts` + `document-decryption.ts`、详情页签/研究记录/阅读 | AGENTS "解密与清晰内构""外壳结构与磨砂揭示" |
| **4. 独立模型查看器** | `model-viewer.ts`（360°、拆解六组、复位） | AGENTS "独立模型查看器" 在解密之后 |
| **5. 交互扩展功能** | 检索/收藏/导出/设置（`main.ts` 状态机）、滚动标题 `rolling-number` | README 操作说明；AGENTS "标题滚动" |
| **6. 主题与声音** | `theme-*.ts`（明暗）、`audio.ts`（三轨配乐 + 玻璃/电子音效） | AGENTS "明暗配色""声音实现" 较晚 |
| **7. 适配与画质** | `viewport-layout.ts`/`responsive.css`（桌面→手机横竖屏→iPhone）、`render-quality.ts`/`quality-renderer.ts`（四档预设 + 超级性能模式） | AGENTS "屏幕比例与触摸适配""超级性能模式" 09-10 起 |
| **8. PWA 与线上化** | `pwa.ts`（离线、主屏安装、不中断更新） | AGENTS "PWA 与线上入口" 09-10 |
| **9. 壁纸形态** | `workbench`/`wallpaper-*`、WE 属性、HUD 曲面视差 | AGENTS "Wallpaper Engine 实验分支" 09-10 |
| **10. 部署收敛** | Cloudflare Pages 迁移（`build:cloudflare`） | AGENTS 09-15 |

**推断逻辑**：必然是"先有能渲染一帧正确画面"的 1→2 阶段（相机与几何校准最耗时，AGENTS 内条目最多），再做交互与解密，最后才是锦上添花的声音/主题/适配/PWA/壁纸/部署。AGENTS.md 的日期线印证：09-09 还在修镜头与几何，09-10 才密集出现壁纸、PWA、明暗、性能模式等外围形态。

## 四、总评

这是一个**以"逐帧复刻游戏 PV"为唯一目标、用 `DESIGN.md` 把视觉/相机/运动参数固化成硬约束、并以 `verification/` 对照工具 + 检查脚本做质量锁定**的 Three.js 单页前端。其构建顺序遵循"地基 → 开场 → 三维阵列 → 解密详情 → 查看器 → 交互功能 → 主题声音 → 适配画质 → PWA → 壁纸 → 部署"的依赖链，越往后越偏外围形态与发布。

与同仓库分析过的 SillyTavern 完全不同：后者是"前端拼 prompt、后端当代理"的工具型应用，RhineLabUI 是"把一段视频做成可操作 3D 界面"的复刻型作品，工程重心在渲染保真与动效精度，而非业务逻辑。

## 五、参考依据（仓库内文件）

- `README.md` — 工程结构、界面与动效、操作说明、修改与复核
- `DESIGN.md` — 视觉与行为基准（屏幕比例、标题滚动、相机/几何、循环阵列、光影、解密）
- `AGENTS.md` — 带日期的用户决策日志（09-09 → 09-15），是推断构建顺序的主要依据
- `src/main.ts` — 入口与状态机，import 了几乎所有模块
- `verification/` — 分阶段验证记录（`RESPONSIVE.md`、`LOOPING-ARCHIVE.md`、`DECRYPTION.md`、`PERFORMANCE.md` 等）
- `reference/` — 原片对照工具（`review.html`、`boot-review.html` 等），仅开发环境使用
