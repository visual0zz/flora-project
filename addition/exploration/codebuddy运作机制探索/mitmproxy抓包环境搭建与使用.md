# 抓包环境搭建与使用说明（mitmproxy）

> 目的：拦截并记录 CodeBuddy 等 AI 客户端发往大模型服务器（DeepSeek / 腾讯）的请求，查看包结构、提示词、鉴权头等。
> 记录时间：2026-08-13

---

## 一、背景与结论

AI 推荐的 "Flow Monitor"（PyPI 上的 `flow-monitor` 包）是**串口流量传感器监控工具**（Kritsnam 公司做的，配 Arduino 流量计用），与拦截大模型流量毫无关系，不可用。

实际采用的是 **mitmproxy**（免费开源、跨平台、支持命令行/网页两种界面），它作为正向代理拦截 HTTP/HTTPS 流量，可以解密 HTTPS 并记录请求与响应全文。

**关键事实**：mitmproxy 是透明转发，只拦截、记录、放行，**不会改变请求目的地**。CodeBuddy 的流量发往哪里取决于它当前配置的模型地址：
- `settings.json` 配 `"model": "hy3"`（内置模型）→ 发往腾讯官方服务器
- 切回 `models-back.json` 里的 DeepSeek 模型 → 发往 `https://api.deepseek.com/v1/chat/completions`

---

## 二、安装步骤

环境：Windows，已装 Python 3.14（`C:\Python314`）。

```bash
pip install mitmproxy
```

安装产物：
- `C:\Python314\Scripts\mitmdump.exe`（命令行版）
- `C:\Python314\Scripts\mitmweb.exe`（网页版，本项目使用）
- `C:\Python314\Scripts\mitmproxy.exe`（交互式 TUI）

---

## 三、配置步骤

### 1. 抓包脚本（addon.py）
位置：`C:\Users\shutie.zhao\.mitmproxy-capture\addon.py`

作用：命中过滤规则（按域名匹配）的请求，把 REQUEST / RESPONSE 完整写入 `capture.log`（JSON 格式，含请求头、请求体即提示词、响应体）。

### 2. 过滤规则（capture-hosts.txt）
位置：`C:\Users\shutie.zhao\.mitmproxy-capture\capture-hosts.txt`

每行一个域名/关键字（子串匹配），`#` 开头为注释。当前内容：

```
# 抓包过滤规则：每行一个域名/关键字，命中即记录到 capture.log
# 修改后需重启 mitmweb（重新运行 start-capture.bat 前先跑 stop-capture.bat）
api.deepseek.com
deepseek.com
```

**改完必须重启 mitmweb 才生效。**

### 3. CA 证书
mitmproxy 首次启动时在 `C:\Users\shutie.zhao\.mitmproxy\` 生成 CA 证书。

已执行：`certutil -user -addstore Root C:\Users\shutie.zhao\.mitmproxy\mitmproxy-ca-cert.cer`
作用：把 mitmproxy 证书装入 Windows 当前用户"受信任的根证书颁发机构"，使本机程序（Python、浏览器等走系统证书库的程序）信任它，从而能解密 HTTPS。

### 4. 系统代理
已设置（HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings）：
- `ProxyEnable = 1`
- `ProxyServer = 127.0.0.1:9090`
- `ProxyOverride = <local>`

原配置已备份到 `C:\Users\shutie.zhao\.mitmproxy-capture\proxy-backup.json`（原为未开启代理）。

### 5. Node.js 环境变量（针对 CodeBuddy）
CodeBuddy 是 Node CLI，**不走 Windows 系统代理、也不认 Windows 证书库**，必须单独配环境变量（已用 `setx` 持久化）：

```
HTTP_PROXY        = http://127.0.0.1:9090
HTTPS_PROXY       = http://127.0.0.1:9090
NODE_EXTRA_CA_CERTS = C:\Users\shutie.zhao\.mitmproxy\mitmproxy-ca-cert.pem
NODE_USE_ENV_PROXY  = 1
```

---

## 四、日常使用

### 启动
双击 `C:\Users\shutie.zhao\.mitmproxy-capture\start-capture.bat`
- 代理端口：`127.0.0.1:9090`
- 网页界面：`http://127.0.0.1:9091`

### 使用流程
1. 浏览器打开 http://127.0.0.1:9091 常驻（实时流量列表 + 过滤框）
2. **重启 CodeBuddy**（让它读到新的代理环境变量）
3. 在 CodeBuddy 里正常对话/提问
4. 网页界面看实时包；或看 `C:\Users\shutie.zhao\.mitmproxy-capture\capture.log`
5. 网页顶部过滤框可输入 `~u api.deepseek.com` 之类表达式做显示过滤（只影响显示，不影响日志）

### 停止
双击 `C:\Users\shutie.zhao\.mitmproxy-capture\stop-capture.bat`
- 杀掉 mitmweb
- 恢复系统代理（从 backup 还原）
- 清空 Node 代理环境变量

---

## 五、验证记录（已实测通过）

用真实 `DEEPSEEK_API_KEY` 通过代理调用 `https://api.deepseek.com/v1/chat/completions`：
- 返回 200，正常拿到模型回复
- capture.log 完整记录了：
  - REQUEST：URL、Authorization 头（Bearer sk-xxx）、Content-Type、请求体（含提示词）
  - RESPONSE：状态码、响应头、响应体

Node 端验证：`node fetch` 走代理 + NODE_EXTRA_CA_CERTS 正常访问 DeepSeek 模型列表。

---

## 六、注意事项

1. **`capture.log` 包含明文 API Key**（Authorization 头）——不要外传、不要提交 git。
2. 代理是全局的，浏览器等其它流量也走 9090；证书已受信，一般不影响使用；做证书固定的软件会连不上，属正常现象。
3. 抓完记得 `stop-capture.bat` 恢复环境。
4. 若 CodeBuddy 改用了不走环境变量的 HTTP 客户端，或做证书固定，需要另想办法（Frida 等），此处不展开。
