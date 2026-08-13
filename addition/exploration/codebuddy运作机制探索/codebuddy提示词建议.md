
当需要提示词建议的时候，agent会构造一个伪对话历史给llm，里面的消息如下:

system消息:

``````markdown
You are a prompt suggestion generator. Your ONLY purpose is to suggest the user's next action.

Your job:
1. Read the conversation context (user's last message and assistant's last response)
2. Suggest what CodeBuddy could help with next

CRITICAL CONSTRAINTS:
- You are NOT a code generator, writer, or task executor
- You MUST respond with ONLY the suggestion text, 3-8 words
- NEVER generate, implement, code, or produce any content
- NEVER provide explanations, reasoning, or extra text
- NEVER use quotes, markdown, or formatting
- Be specific when you can — name files, functions, or actions
- Say "done" ONLY if the work is truly complete with no natural follow-ups

IMPORTANT: The suggestion MUST be written in 中文普通话. Do not use English even though these instructions are in English.


``````

user消息:

``````markdown
[User Message]
nihao
...
[Assistant Response]
已回复。
``````

``````markdown
...
[Assistant Response]
…unctionDeclarations` 数组：
```json
{"tools":[{"functionDeclarations":[{"name":"read_file","description":"...","parameters":{...schema...}}]}]}
```

## 工具调用结果回执（TOOL 消息）格式差异

**OpenAI / DeepSeek**（`OpenAiProtocol.java:95`）—— 独立 `tool` role + `tool_call_id`：
```json
{"role":"tool","tool_call_id":"call_1","content":"结果文本"}
```

**Anthropic**（`AnthropicProtocol.java:151`）—— **没有 tool role**，改用 `role:"user"` + content 里的 `tool_result` 块，错误时加 `is_error:true`：
```json
{"role":"user","content":[{"type":"tool_result","tool_use_id":"tu_1","content":"结果文本"}]}
```

**Gemini**（`GeminiProtocol.java:132`）—— `role:"user"` + `functionResponse` part，**按工具名关联（无 call id）**，失败时 response 换 `{"error":...}`：
```json
{"role":"user","parts":[{"functionResponse":{"name":"read_file","response":{"result":"..."}}}]}
```

## assistant 发起调用格式差异

**OpenAI / DeepSeek**（`OpenAiProtocol.java:129`）—— `arguments` 是 **JSON 字符串**（需二次 parse）：
```json
{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"/x\"}"}}]}
```

**Anthropic**（`AnthropicProtocol.java:177`）—— content 里的 `tool_use` 块，`input` 直接是对象：
```json
{"role":"assistant","content":[{"type":"tool_use","id":"tu_1","name":"read_file","input":{"path":"/x"}}]}
```

**Gemini**（`GeminiProtocol.java:65`）—— `functionCall` part，`args` 直接是对象，**无 id**：
```json
{"role":"model","parts":[{"functionCall":{"name":"read_file","args":{"path":"/x"}}}]}
```

## 其他相关差异

- **system 提示**：OpenAI/DeepSeek 合成 `role:"system"` 首条消息（`OpenAiProtocol.java:46`）；Anthropic 走顶层 `system` 字段（`AnthropicProtocol.java:71`）；Gemini 走顶层 `systemInstruction`（`GeminiProtocol.java:83`）。
- **JSON 模式**：Anthropic 用"强制单工具 `tool_choice`"实现（`AnthropicProtocol.java:112`），Gemini 用 `responseMimeType`，OpenAI 用 `response_format`。
- **响应解析**：OpenAI/DeepSeek 的 `arguments` 是字符串需再 parse（`OpenAiProtocol.java:206`）；Anthropic 的 `input`、Gemini 的 `args` 已是对象。Gemini 的 `ToolCall.id` 为 null。

要不要我把这个整理成一篇 `addition/exploration/` 的剖析笔记？
``````


在我具体采集的某一次，大模型显然理解错了要做的事情，大模型回复:

```text
请问有什么可以帮您，
```

正常情况下，大模型会回复:
```text
请整理成 exploration 剖析笔记
```