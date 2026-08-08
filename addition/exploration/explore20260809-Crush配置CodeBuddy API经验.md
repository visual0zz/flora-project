# Crush 配置 CodeBuddy API 经验

> 日期：2026-08-09
> 范围：将腾讯 CodeBuddy 平台的 OpenAI 兼容 API 配置为 Crush 的模型提供者

---

## 一、CodeBuddy API 概览

CodeBuddy（`copilot.tencent.com`）提供 OpenAI 兼容的 API 网关，base URL 为 `https://www.codebuddy.cn/v2`。

**获取 API Key**：访问 `https://copilot.tencent.com/profile/`，生成的 key 以 `ck_` 为前缀。

**认证方式**：需要同时传递 `Authorization: Bearer <key>` 和 `X-API-Key: <key>` 两个头（`--api-key` 自动设置前者，`--extra-header` 设置后者）。

**可用模型**（截至 2026-08）：

| 模型 ID | 名称 | 上下文窗口 | 推荐 max_tokens |
|---------|------|-----------|-----------------|
| `hy3` | Hunyuan 3 | 200K | 24000 |
| `hunyuan-chat` | Hunyuan Chat | 200K | 24000 |
| `glm-5.2` | GLM 5.2 | 200K | 48000 |
| `glm-5.1` | GLM 5.1 | 200K | 48000 |
| `glm-5.0` | GLM 5.0 | 200K | 48000 |
| `kimi-k3-2` | Kimi K3.2 | 1M | 32000 |
| `kimi-k2.7` | Kimi K2.7 | 256K | 32000 |
| `deepseek-v4-pro` | DeepSeek V4 Pro | 1M | 50000 |
| `deepseek-v4-flash` | DeepSeek V4 Flash | 1M | 50000 |
| `minimax-m3-pay` | MiniMax M3 | 200K | 32000 |

---

## 二、配置步骤

### 2.1 添加 Provider

```bash
provider add codebuddy \
  --name "CodeBuddy" \
  --type openai-compat \
  --base-url "https://www.codebuddy.cn/v2" \
  --api-key "ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  --extra-header "X-API-Key" "ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

关键点：
- `--type openai-compat`：声明为 OpenAI 兼容协议，Crush 会自动适配 chat completions 端点
- `--api-key`：设置 `Authorization: Bearer` 头
- `--extra-header "X-API-Key"`：CodeBuddy 额外要求的认证头，值同 API Key

### 2.2 注册模型

```bash
model add codebuddy/deepseek-v4-pro --name "DeepSeek V4 Pro" --context-window 1000000 --default-max-tokens 50000
model add codebuddy/deepseek-v4-flash --name "DeepSeek V4 Flash" --context-window 1000000 --default-max-tokens 50000
model add codebuddy/hy3 --name "Hunyuan 3" --context-window 200000 --default-max-tokens 24000
# ... 按需添加其他模型
```

参数说明：
- `codebuddy/<model-id>`：provider 前缀 + 模型 ID，与 API 调用时的 model 参数对应
- `--context-window`：模型声称的上下文长度上限（token 数）
- `--default-max-tokens`：Crush 请求时默认的 `max_tokens`，应小于 context window

### 2.3 设置默认模型

```bash
model large codebuddy/deepseek-v4-flash --reasoning-effort high --max-tokens 384000
```

- `model large`：设为 large（主）模型
- `--reasoning-effort high`：DeepSeek V4 系列支持 reasoning effort 参数，high 获得更强的推理能力
- `--max-tokens 384000`：实际请求的 max_tokens，应 ≤ context window 且留有余量给 prompt

small 模型同理：
```bash
model small codebuddy/hy3 --max-tokens 24000
```

---

## 三、配置文件位置

Crush 配置分为两个文件：

| 文件 | 格式 | 用途 |
|------|------|------|
| `~/.config/crush/crushrc` | Bash 脚本 | Provider 和 model 注册（声明式） |
| `~/.local/share/crush/crush.json` | JSON | 运行时状态（当前选中的 large/small 模型等） |

`crushrc` 是纯 Bash，Crush 启动时 source 执行。可以用任意文本编辑器直接编辑，也可以用 Crush 内置的 `provider add` / `model add` 命令。

---

## 四、踩坑记录

### 4.1 双头认证

CodeBuddy 的 OpenAI 兼容网关要求**同时**传递 `Authorization: Bearer` 和 `X-API-Key`，缺一不可。Crush 的 `--api-key` 只设置前者，必须通过 `--extra-header` 补充后者。

### 4.2 模型 ID 大小写

API 的 model 参数使用全小写 + 连字符格式（如 `deepseek-v4-pro`），与 CodeBuddy 网页端显示的名称可能不一致，以 API 文档为准。

### 4.3 Reasoning Effort

DeepSeek V4 系列支持 `reasoning_effort` 参数（`low` / `medium` / `high`），控制推理深度。Crush 的 `model large` 命令支持 `--reasoning-effort` 透传该参数。非 DeepSeek 模型设置此参数无效（API 会忽略）。

### 4.4 max_tokens 的层次

Crush 有三层 token 控制：
- `--context-window`（model add）：声明模型能力上限，不参与实际请求
- `--default-max-tokens`（model add）：Crush 请求的默认 max_tokens
- `--max-tokens`（model large/small）：覆盖默认值，针对具体用途微调

实际请求的 `max_tokens` 不应超过 context window 减去 prompt 长度。

---

## 五、验证

配置完成后，Crush 启动时会自动加载 `crushrc`。可通过以下方式验证：

1. Crush 正常运行且响应来自 CodeBuddy 的模型
2. 查看 `crush_info` 输出中 `[providers]` 部分，codebuddy 应显示 `enabled`
3. 查看 `[model]` 部分，large/small 模型指向 codebuddy provider

---

## 六、与其他 Provider 的关系

当前配置同时启用了 codebuddy 和 deepseek 两个 provider。Crush 按模型粒度选择 provider：每个模型绑定到唯一的 provider，切换模型即切换 provider。不存在 provider 级别的 fallback 或负载均衡。

---

## 七、尝试配置阿里云 Qoder（失败）

### 7.1 背景

阿里云 Qoder（`qoder.com`）提供编码 Agent 服务，API key 格式为 `pt-...`（Personal Access Token）。

### 7.2 结论：无法配置

Qoder 的 API 是自有的 **Cloud Agents API**（Agent-as-a-Service），不是 OpenAI 兼容协议：

- Base URL：`https://api.qoder.com/api/v1/cloud`
- 认证：`Authorization: Bearer pt-...`
- API 端点：Agents、Sessions、Events（SSE 流）、Files、Vaults 等——全是 Agent 生命周期管理
- **不存在 `/v1/chat/completions` 端点**，无法作为 OpenAI 兼容的 drop-in replacement

Crush 的 provider type 支持 `openai`、`openai-compat`、`anthropic` 及本地类型（ollama/lmstudio/llamacpp），不包含 Qoder 的自有协议。因此 Qoder 无法直接接入 Crush。

### 7.3 替代思路

如果未来需要接入，可能的路径：
1. 自建一个 Qoder → OpenAI 兼容协议的转换代理（将 `/v1/chat/completions` 请求转为 Qoder Session + SSE 事件流）
2. 等 Qoder 官方推出 OpenAI 兼容端点
3. 等 Crush 支持自定义 provider 协议适配
