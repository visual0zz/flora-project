# Ramet 模板语言 VS Code 扩展

为 `.ramet` 文件提供语法高亮和定义跳转支持。

## 功能

- **语法高亮** — 指令标签、变量插值、注释、字符串、数字、内置函数等按语义着色
- **定义跳转** (Ctrl+Click / Go to Definition)：
  - `<#include "path.ftl">` → 跳转到目标 `.ftl` 文件
  - `${varName}` → 跳转到 `@Param{ varName: ... }` 定义
  - `<@macroName>` → 跳转到 `<#macro macroName ...>` 定义

## 项目结构

```
ramet-language-support/
├── package.json                  # 扩展清单（语言注册、激活事件、脚本）
├── tsconfig.json                 # TypeScript 编译配置
├── language-configuration.json   # 注释、括号、单词匹配规则
├── .vscodeignore                 # 打包时忽略的文件
├── icons/
│   ├── ramet-light.svg           # 浅色主题图标
│   └── ramet-dark.svg            # 深色主题图标
├── syntaxes/
│   └── ramet.tmLanguage.json     # TextMate 语法高亮规则
└── src/
    └── extension.ts              # 扩展入口 + DefinitionProvider
```

## 配置项

### 文件关联

`package.json` 中注册 `.ftl` 后缀关联到 `ramet` 语言：

```json
"languages": [{
    "id": "ramet",
    "extensions": [".ftl"],
    ...
}]
```

如需增加其他扩展名，在 `extensions` 数组中追加即可。

### 语法高亮

`syntaxes/ramet.tmLanguage.json` 是 TextMate grammar，控制所有语法元素的着色。如需调整某类语法的作用域（scope），修改对应正则和 `name` 字段。

默认配色（可通过 `editor.tokenColorCustomizations` 覆盖）的语义约定：

| 作用域 (scope) | 含义 | 默认色 |
|----------------|------|--------|
| `variable.other.interpolation.ramet` | **变量引用**，即 `${...}` 插值 | 粉红 `#FF79C6` |
| `meta.definition.ramet` | **变量定义**，即 `@Param` 中的 key | 橙黄 `#E5C07B` |
| `meta.annotation.ramet` | 注解 / 元数据标记 | 橙黄 `#E5C07B` |

> 关键区分：**定义处**（如 `@Param{ name: ... }` 的 `name`）保持橙黄，
> **引用处**（如 `${name}` 的 `name`）变为粉红。两者通过独立的 TextMate scope 区分，便于一眼辨认变量的定义与使用。

`#elseif` 作为 `<#if>` 多分支链的一环，与 `#if`/`#else`/`#list` 等指令一样按关键字着色。

### 语言配置

`language-configuration.json` 控制：
- **注释切换**：`Ctrl+/` 会插入 `<#-- -->`
- **括号匹配**：`${` `}`、`<#` `>` 等配对高亮
- **单词选取**：`Ctrl+D` 选中 `identifier.property`

## 构建

```bash
# 安装依赖
npm install

# 编译 TypeScript
npm run compile

# 打包 .vsix
npm run package
```

或者直接用 npx：

```bash
npx tsc -p ./tsconfig.json
npx vsce package
```

## 构建产物

打包后的插件安装包在当前目录：

```
ramet-language-support-0.1.0.vsix
```

安装方式：VS Code → Extensions → ⋯ → Install from VSIX

## 本地调试

在 VS Code 中打开本目录，按 `F5` 启动 Extension Development Host，会自动加载扩展并打开一个包含 `.ftl` 文件的测试窗口。

## 开发环境要求

- **Node.js**: 18+
- **npm**: 9+
- **VS Code**: 1.96+
- **TypeScript**: 5.7+
