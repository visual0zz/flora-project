# 决策：Message 增加 SYSTEM 角色与协议层映射

**日期**：2026-08-10
**模块**：flora-root（com.flora.ai）
**类型**：功能增强

## 背景

system 提示原先仅由 `ChatRequest.system()` 顶层字段承载，无法以消息形式参与对话流。
需要为 `Message.Role` 增加 `SYSTEM` 角色，让调用方可以按消息表达系统提示，
各协议层按自家 API 语义做映射。

## 决策

`Message.Role` 增加 `SYSTEM`，三家协议映射策略：

- **OpenAI / DeepSeek**：原生支持 `role:"system"`，消息原位透传（现有通用路径
  `role().name().toLowerCase()` 直接产出 "system"）。
- **Anthropic**：消息数组无 system 角色；
  - 头部的 SYSTEM 角色消息（即排在第一条非 SYSTEM 消息之前的所有 SYSTEM 消息）
    按序合并进顶层 `system` 字段，与 `ChatRequest.system()` 以 `\n\n` 分隔，
    构建 messages 时跳过。
  - 中途出现的 SYSTEM 角色消息（出现在某条非 SYSTEM 消息之后）转为
    `mid_conv_system` 块放入 messages 数组。该能力 `2023-06-01` 版本即已支持，
    无需升级 `anthropic-version`。
- **Gemini**：contents 只接受 user/model 角色，遇到 SYSTEM 角色消息直接抛
  `IllegalArgumentException`，提示改用顶层 `ChatRequest.system()`。

## 影响

- 现有 `ChatRequest.system()` 顶层字段语义保持不变，两套表示可并存。
- `HanakoEngine.toApiMessages` 的 switch 已有 default 分支，不受新增枚举影响。
- 新增协议测试覆盖头部合并、中途 mid_conv_system 块、连续头部 SYSTEM 合并、透传与报错路径。
- `AnthropicProtocol.API_VERSION` 维持 `2023-06-01`（`mid_conv_system` 块该版本已支持）。
