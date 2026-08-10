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
- **Anthropic**：消息数组无 system 角色，协议层按序把全部 SYSTEM 角色消息的文本
  提取进顶层 `system` 字段（与 `ChatRequest.system()` 合并，段落间 `\n\n` 分隔），
  构建 messages 时跳过这些消息。不采用 `mid_conv_system` 块：当前
  `AnthropicProtocol.API_VERSION` 固定为 `2023-06-01`，mid-conversation 需要更新的
  版本头；若日后需要对话中途更新指令再单独引入。
- **Gemini**：contents 只接受 user/model 角色，遇到 SYSTEM 角色消息直接抛
  `IllegalArgumentException`，提示改用顶层 `ChatRequest.system()`。

## 影响

- 现有 `ChatRequest.system()` 顶层字段语义保持不变，两套表示可并存。
- `HanakoEngine.toApiMessages` 的 switch 已有 default 分支，不受新增枚举影响。
- 新增四个协议测试覆盖合并/透传/报错路径。
