# idea20260807-ramet-vs-freemarker

## 背景与目标

flora-ramet 当前定位为「基于模板的代码生成引擎」，已具备插值、条件、循环、宏、include、
元数据驱动的多文件生成等能力。本笔记记录：**若要把 ramet 当作通用模板引擎、替代 Apache
FreeMarker**，目前**已实现**与**仍缺失**的能力对照，作为后续迭代的路线图。

> 状态：截至 2026-08-07，已落地「严格模式」与「输出转义」两项（见下）；其余为待办。

## 已具备的能力（对照 FreeMarker）

| 能力 | ramet 现状 |
|------|-----------|
| 变量插值 | `${expr}`，支持属性链 `${a.b.c}`、索引 `${arr[0]}` |
| 条件分支 | `<#if>/<#elseif>/<#else>` |
| 循环 | `<#for var:items>`、`<#for idx,var:items>`，支持 `<#else>`、`<#break>`、`<#continue>` |
| 宏 | `<#macro name:p1,p2=default>` 定义，`<@name args/>` 调用，参数带默认值 |
| 子模板 | `<#include "path">`，带缓存与循环依赖检测 |
| 元数据驱动 | `<#meta>` 内 `@Param/@Cartesian/@Path/@SkipWhen/@Config` |
| 笛卡尔积多文件 | `@Cartesian` 展开生成多个文件，同名路径合并为单文件 |
| 表达式引擎 | `Lson`：函数/中缀/前缀/引用/引用成环检测 |
| 内置函数 | 比较、字符串、判空、算术、范围、组合生成、日期/数字格式化等 |
| 错误定位 | 行号、列号、宏调用链、include 链 |
| 严格模式 | `strictNull`：插值 null 默认抛错，可配置关闭（2026-08-07 新增） |
| 输出转义 | `escape`：`html/xml/js` 整体转义，默认关闭（2026-08-07 新增） |
| 自动警告注释 | `autoWarning`，按扩展名选注释风格、尊重 shebang |

## 仍缺失的能力（待办，按优先级）

### 高优先级（缺失会导致不可用 / 不安全）

1. **按输出格式的自动转义（防 XSS）**
   - FreeMarker 按 `OutputFormat`（HTML/XML/JS/RTF）对**插值**自动转义。
   - ramet 现状：仅支持「整段最终输出」整体转义（`escape` 配置项），无法做到「仅转义插值、
     保留字面量标签」的细粒度自动转义。
   - 后续：引入 `OutputFormat` 抽象 + 按插值自动转义；与现有 `escape` 方案共存或演进。

2. **`TemplateLoader` 多来源加载 + 缓存 + 更新检测**
   - FreeMarker：`FileTemplateLoader` / `ClassTemplateLoader` / `StringTemplateLoader` /
     `MultiTemplateLoader` + `TemplateCache`（`lastModified` 失效）。
   - ramet 现状：`TemplateRepository` 仅 `none()` 与内存 `from(Map)`；入口模板每次 `generate`
     都重新 `parse`，无跨调用解析缓存、无 `lastModified` 更新检测。
   - 后续：抽象 `TemplateLoader` 来源；解析结果（`Template` AST）不可变并缓存；按修改时间热更新。

3. **严格/容错模式 + 精确 null 处理（部分已有）**
   - 已完成：插值严格 null（默认报错，可关闭）。
   - 仍缺：缺失值存在性运算符（`??`）、默认值运算符（`!`）、严格模式对未定义变量直接抛错
     的统一开关（当前仅覆盖插值，未覆盖属性链中间 null 的短路等行为细节）。

4. **错误报告与渲染期 FTL 调用栈**
   - 已有行号/列号与宏/include 链，但渲染期异常缺少「逐层嵌套宏」的调用栈式展示。
   - 后续：异常中携带模板调用栈（类似 FreeMarker 的 `TemplateException` 栈）。

5. **并发渲染**
   - `Context` 为链式可变作用域。需确保 `Template`（AST）不可变且线程安全共享，每次渲染新建
     轻量 `Context`，以支持多线程同时渲染同一模板。

### 中优先级（影响表达力 / 开发效率）

6. **命名空间隔离 + `import` 别名 + 共享变量**
   - ramet 宏全存扁平 `Map`，无命名空间。需补：每模板即命名空间、`<#import "lib" as ns>`、
     `<#global>`/共享变量，以构建可复用模板库。

7. **宏嵌套传参 `<#nested arg>` + 模板继承/布局**
   - 缺 `<#nested>` 回传参数机制；加上命名空间后可原生支持 layout 继承（FreeMarker 本身靠宏拼，
     ramet 可做得更好，形成差异化优势）。

8. **高阶序列内建 + 类型化日期/数字**
   - 补 `filter/map/take_while/sort_by`、哈希 `keys/values`、日期 `date/time/datetime` 类型精化；
     `now`/`numberFormat` 升级为 locale 感知。

9. **国际化 / 本地化**
   - 缺 `Locale` 设定、`name_en.ftl` 按 locale 后缀查找、`ResourceBundle` + `MessageFormat` 消息。

10. **`TemplateMethodModel` / `TemplateDirectiveModel` 扩展点**
    - 已有 `TemplateFunction` SPI（需补注册示例），但缺「块级自定义指令」扩展点
      （控制嵌套体输出），这是把 Java 能力当模板一等公民的关键。

### 低优先级 / 差异化机会

11. **原生模板继承 / 布局**：FreeMarker 靠宏拼，ramet 可原生支持，反而超越它。
12. **`incompatible_improvements` 版本化配置**：可渐进升级而不破坏已有模板，利于长期演进。
13. **语法糖**：`<#list items/sep>`、`switch/case`、`attempt/recover`（渲染期异常捕获）、
    `visit/recurse` 节点递归等按需补齐。

## 本次已完成的改动

- `ConfigKey` 新增 `STRICT_NULL`、`ESCAPE` 两个配置项。
- `Context` 透传 `@Config` 配置，新增 `strictNull()`；`VarNode` 在严格模式下对 null 插值抛错。
- 新增 `OutputEscaper`（html/xml/js），`TemplateEngine` 在警告注释注入前对最终输出整体转义。
- `Template.render` 保留路径解析的非严格行为（历史兼容）。
- 新增/调整测试覆盖严格模式与转义；修正 `02-指令-函数-配置项-速查表.md` 相关描述。
