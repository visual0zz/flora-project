# Changelog

## [0.8.2]
### Fixed
- 修复被动文本区（TEXT）被错误高亮的问题：普通被动区仅以暗淡色显示，不再对 `@Override`、`rehash(` 等做细分着色
- 仅 `<#xxx ...>`、`<#meta>...</#meta>`、`${...}` 三类逻辑结构内部保留语法高亮

## [0.8.1]
### Fixed
- 暗色主题（Darcula）下被动块配色修正，避免变亮白

## [0.8.0]
### Added
- 新增 `continue`/`break`/`meta` 指令语法高亮支持
- Meta 块（`@Param`/`@Cartesian`/`@Path`/`@Config`/`@SkipWhen`）完整 Lson 语法高亮
- 逻辑块内标识符亮色、被动块灰色

## [0.6.0]
### Added
- 初始 `.ramet` 模板语言语法高亮支持
