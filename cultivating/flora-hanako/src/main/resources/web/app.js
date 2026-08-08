/* ============================================================
   Flora Hanako 前端 — 复刻 openhanako 交互与暖纸视觉
   纯原生 JS，无构建步骤；通过 REST + WebSocket 与后端通信。
   ============================================================ */
(function () {
    "use strict";

    const state = {
        agents: [],
        currentAgent: "hanako",
        sessions: [],
        currentSession: null,
        streaming: false,
        ws: null,
        pendingAssistant: null,   // 当前正在累加的 assistant 消息 DOM
        pendingThinking: null,
        pendingTool: null,
        slashMenu: [],
        slashSel: 0,
    };

    const $ = (sel) => document.querySelector(sel);

    // ── 工具：API 请求 ──
    async function api(method, path, body) {
        const opts = { method, headers: {} };
        if (body !== undefined) {
            opts.headers["Content-Type"] = "application/json";
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(path, opts);
        if (!res.ok) {
            const txt = await res.text();
            throw new Error("请求失败 " + res.status + ": " + txt);
        }
        const ct = res.headers.get("Content-Type") || "";
        return ct.includes("application/json") ? res.json() : res.text();
    }

    // ── Toast ──
    function toast(text, type) {
        const el = document.createElement("div");
        el.className = "toast";
        el.textContent = text;
        $("#toastWrap").appendChild(el);
        setTimeout(() => el.remove(), 3000);
    }

    // ── Markdown 轻量渲染（标题 / 列表 / 代码块 / 粗体 / 行内代码） ──
    function renderMarkdown(md) {
        if (!md) return "";
        const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
        const lines = md.split("\n");
        let html = "", i = 0, inCode = false, codeBuf = [];
        while (i < lines.length) {
            const line = lines[i];
            if (line.startsWith("```")) {
                if (!inCode) { inCode = true; codeBuf = []; i++; continue; }
                else { html += "<pre><code>" + esc(codeBuf.join("\n")) + "</code></pre>"; inCode = false; i++; continue; }
            }
            if (inCode) { codeBuf.push(line); i++; continue; }
            if (/^\s*[-*]\s+/.test(line)) {
                const items = [];
                while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
                    items.push("<li>" + inline(esc(lines[i].replace(/^\s*[-*]\s+/, ""))) + "</li>");
                    i++;
                }
                html += "<ul>" + items.join("") + "</ul>";
                continue;
            }
            if (/^#{1,6}\s+/.test(line)) {
                const lvl = line.match(/^#+/)[0].length;
                html += "<p><strong>" + inline(esc(line.replace(/^#+\s+/, ""))) + "</strong></p>";
                i++; continue;
            }
            if (line.trim() === "") { i++; continue; }
            html += "<p>" + inline(esc(line)) + "</p>";
            i++;
        }
        if (inCode) html += "<pre><code>" + esc(codeBuf.join("\n")) + "</code></pre>";
        return html;
    }
    function inline(s) {
        return s
            .replace(/`([^`]+)`/g, "<code>$1</code>")
            .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
            .replace(/\*([^*]+)\*/g, "<em>$1</em>");
    }

    // ── 渲染侧边栏 ──
    function renderAgents() {
        const wrap = $("#agentList");
        wrap.innerHTML = "";
        state.agents.forEach(a => {
            const el = document.createElement("div");
            el.className = "agent-item" + (a.id === state.currentAgent ? " active" : "");
            el.innerHTML = `<div class="agent-avatar">${escHtml(a.name[0] || "H")}</div><div class="agent-name">${escHtml(a.name)}</div>`;
            el.onclick = () => { state.currentAgent = a.id; renderAgents(); loadSessions(); newSession(); };
            wrap.appendChild(el);
        });
    }

    function renderSessions() {
        const wrap = $("#sessionList");
        wrap.innerHTML = "";
        state.sessions.forEach(s => {
            const el = document.createElement("div");
            el.className = "session-item" + (state.currentSession && s.id === state.currentSession.id ? " active" : "");
            el.innerHTML = `<div class="agent-avatar" style="background:var(--text-muted)">💬</div><div class="session-name">${escHtml(s.title || "新对话")}</div>`;
            el.onclick = () => openSession(s.id);
            wrap.appendChild(el);
        });
    }

    function escHtml(s) {
        return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    // ── 会话 ──
    async function loadSessions() {
        state.sessions = await api("GET", "/api/sessions?agentId=" + state.currentAgent);
        renderSessions();
    }

    async function newSession() {
        const s = await api("POST", "/api/sessions", { agentId: state.currentAgent });
        state.currentSession = s;
        await loadSessions();
        clearMessages();
        scrollBottom();
    }

    async function openSession(id) {
        const s = await api("GET", "/api/sessions/" + id);
        state.currentSession = s;
        renderSessions();
        clearMessages();
        s.messages.forEach(m => appendMessage(m.role, m.text, { toolResults: m.toolResults, toolCalls: m.toolCalls }, false));
        scrollBottom();
    }

    function clearMessages() {
        $("#messages").innerHTML = "";
    }

    // ── 消息渲染 ──
    function appendMessage(role, text, extra, animate) {
        const wrap = $("#messages");
        const welcome = $("#welcome");
        if (welcome) welcome.remove();
        const el = document.createElement("div");
        el.className = "msg " + role.toLowerCase();
        const avatar = role === "USER" ? "你" : role === "TOOL" ? "🔧" : "H";
        let body = `<div class="msg-avatar">${avatar}</div><div class="msg-body">`;
        if (extra && extra.toolResults && extra.toolResults.length) {
            extra.toolResults.forEach(tr => {
                body += `<div class="tool-block"><span class="tool-name">🔧 ${escHtml(tr.name || "tool")}</span><div class="tool-result">${escHtml(tr.result || "")}</div></div>`;
            });
        }
        if (text) body += renderMarkdown(text);
        body += `</div>`;
        el.innerHTML = body;
        wrap.appendChild(el);
        scrollBottom();
        return el;
    }

    function ensureAssistant() {
        if (state.pendingAssistant) return state.pendingAssistant;
        const el = appendMessage("ASSISTANT", "", {}, true);
        state.pendingAssistant = el;
        return el;
    }

    function pushAssistantDelta(delta) {
        const el = ensureAssistant();
        const body = el.querySelector(".msg-body");
        body.innerHTML += renderMarkdown(delta);
        scrollBottom();
    }

    function pushThinking(delta) {
        const el = ensureAssistant();
        let block = el.querySelector(".thinking-block");
        if (!block) {
            block = document.createElement("div");
            block.className = "thinking-block";
            block.textContent = "（思考中）";
            el.querySelector(".msg-body").prepend(block);
        }
        block.textContent += delta;
    }

    function pushTool(name, result) {
        const el = ensureAssistant();
        const block = document.createElement("div");
        block.className = "tool-block";
        block.innerHTML = `<span class="tool-name">🔧 ${escHtml(name)}</span>` + (result ? `<div class="tool-result">${escHtml(result)}</div>` : "");
        el.querySelector(".msg-body").appendChild(block);
        scrollBottom();
    }

    function scrollBottom() {
        const area = $("#chatArea");
        area.scrollTop = area.scrollHeight;
    }

    // ── WebSocket ──
    function connectWs() {
        const proto = location.protocol === "https:" ? "wss" : "ws";
        const ws = new WebSocket(proto + "://" + location.host + "/ws/chat");
        state.ws = ws;
        ws.onmessage = (ev) => {
            let msg;
            try { msg = JSON.parse(ev.data); } catch { return; }
            handleWs(msg);
        };
        ws.onclose = () => { setTimeout(connectWs, 2000); };
    }

    function handleWs(msg) {
        switch (msg.type) {
            case "text_delta": pushAssistantDelta(msg.delta); break;
            case "thinking_start": break;
            case "thinking_delta": pushThinking(msg.delta); break;
            case "thinking_end": break;
            case "tool_start": state.pendingTool = msg.name; pushTool(msg.name, null); break;
            case "tool_end": break;
            case "turn_end": finishTurn(); break;
            case "error": toast(msg.message, "error"); finishTurn(); break;
            default: break;
        }
    }

    function finishTurn() {
        state.streaming = false;
        state.pendingAssistant = null;
        state.pendingThinking = null;
        setSending(false);
        loadSessions();
    }

    function setSending(on) {
        state.streaming = on;
        $("#sendBtn").disabled = on;
        $("#sendBtn").textContent = on ? "思考中…" : "发送";
    }

    // ── 发送 ──
    async function send(text) {
        if (state.streaming) return;
        if (!state.currentSession) await newSession();
        appendMessage("USER", text, {}, true);
        setSending(true);
        state.ws.send(JSON.stringify({ type: "prompt", sessionId: state.currentSession.id, text }));
    }

    // ── 斜杠命令 ──
    const SLASH = [
        { name: "diary", label: "写日记", desc: "让 Hanako 把今天的事写成一篇日记", run: runDiary },
        { name: "xing", label: "沉淀工作流", desc: "从本次对话提炼可复用的工作指南（<xing>）", run: runXing },
        { name: "remember", label: "记住偏好", desc: "把一句话作为记忆写入事实库", run: runRememberPrompt },
    ];

    function runDiary() {
        // 演示：调用后端日记接口（占位实现）
        toast("日记功能为占位演示", "info");
    }
    function runXing() {
        send(XING_PROMPT);
    }
    function runRememberPrompt() {
        const t = prompt("要记住的偏好 / 事实：");
        if (!t) return;
        api("POST", "/api/memory", { text: t, tags: ["偏好"] }).then(() => toast("已记住")).catch(e => toast(e.message, "error"));
    }

    const XING_PROMPT = `回顾这个 session 里我（用户）发送的消息。只从我的对话内容中提取指导、偏好、纠正和工作流程，整理成一份可复用的工作指南。
要求：
1. 只保留可复用的模式，过滤仅限本次的具体上下文
2. 按类别组织：风格偏好、工作流程、质量标准、注意事项
3. 措辞用指令式
4. 严格按照以下格式输出（用直引号）：
<xing title="具体的工作流名称">
## 风格偏好
- 做 X
## 工作流程
1. 第一步
</xing>`;

    // ── 输入区交互 ──
    function initInput() {
        const input = $("#input");
        input.addEventListener("input", () => {
            input.style.height = "auto";
            input.style.height = Math.min(input.scrollHeight, 180) + "px";
            handleSlash(input.value);
        });
        input.addEventListener("keydown", (e) => {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                if ($("#slashMenu").classList.contains("open") && state.slashMenu.length) {
                    execSlash(state.slashMenu[state.slashSel]);
                    return;
                }
                const v = input.value.trim();
                if (v) { send(v); input.value = ""; input.style.height = "auto"; closeSlash(); }
            }
            if (e.key === "ArrowDown" && $("#slashMenu").classList.contains("open")) { e.preventDefault(); moveSlash(1); }
            if (e.key === "ArrowUp" && $("#slashMenu").classList.contains("open")) { e.preventDefault(); moveSlash(-1); }
            if (e.key === "Escape") closeSlash();
        });
        $("#sendBtn").onclick = () => {
            const v = input.value.trim();
            if (v) { send(v); input.value = ""; input.style.height = "auto"; }
        };
    }

    function handleSlash(val) {
        if (val.startsWith("/")) {
            const q = val.slice(1).toLowerCase();
            state.slashMenu = SLASH.filter(c => c.name.startsWith(q) || c.label.includes(q));
            state.slashSel = 0;
            renderSlash();
        } else closeSlash();
    }
    function renderSlash() {
        const menu = $("#slashMenu");
        if (!state.slashMenu.length) { closeSlash(); return; }
        menu.innerHTML = state.slashMenu.map((c, i) =>
            `<div class="slash-item ${i === state.slashSel ? "sel" : ""}" data-i="${i}"><span class="sc-name">/${c.name}</span><span class="sc-desc">${c.desc}</span></div>`
        ).join("");
        menu.classList.add("open");
        menu.querySelectorAll(".slash-item").forEach(it => {
            it.onclick = () => execSlash(state.slashMenu[+it.dataset.i]);
        });
    }
    function moveSlash(d) {
        state.slashSel = (state.slashSel + d + state.slashMenu.length) % state.slashMenu.length;
        renderSlash();
    }
    function closeSlash() { $("#slashMenu").classList.remove("open"); state.slashMenu = []; }
    function execSlash(cmd) {
        $("#input").value = "";
        closeSlash();
        cmd.run();
    }

    // ── 书桌（笺） ──
    async function loadJians() {
        const list = await api("GET", "/api/jians?agentId=" + state.currentAgent);
        const wrap = $("#jianList");
        wrap.innerHTML = "";
        list.forEach(j => {
            const el = document.createElement("div");
            el.className = "jian" + (j.done ? " done" : "");
            el.innerHTML = `<div>${escHtml(j.content)}</div><div class="jian-actions">
                <button data-act="toggle">${j.done ? "撤销" : "完成"}</button>
                <button data-act="del">删除</button></div>`;
            el.querySelector('[data-act="toggle"]').onclick = () => {
                api("POST", "/api/jians", { id: j.id, agentId: j.agentId, content: j.content, done: !j.done }).then(loadJians);
            };
            el.querySelector('[data-act="del"]').onclick = () => api("DELETE", "/api/jians/" + j.id).then(loadJians);
            wrap.appendChild(el);
        });
    }
    function initDesk() {
        $("#tbToggleRight").onclick = () => { $("#desk").classList.toggle("open"); if ($("#desk").classList.contains("open")) loadJians(); };
        $("#deskClose").onclick = () => $("#desk").classList.remove("open");
        $("#jianAddBtn").onclick = addJian;
        $("#jianInput").addEventListener("keydown", e => { if (e.key === "Enter") addJian(); });
    }
    function addJian() {
        const v = $("#jianInput").value.trim();
        if (!v) return;
        api("POST", "/api/jians", { agentId: state.currentAgent, content: v, done: false }).then(() => {
            $("#jianInput").value = ""; loadJians();
        });
    }

    // ── 设置弹窗 ──
    const SETTINGS_TABS = [
        { id: "providers", label: "模型提供商" },
        { id: "models", label: "模型" },
        { id: "agents", label: "Agents" },
        { id: "memory", label: "记忆" },
        { id: "about", label: "关于" },
    ];
    let settingsTab = "providers";
    let settingsCache = { providers: [], models: [], agents: [] };

    async function openSettings() {
        settingsCache.providers = await api("GET", "/api/providers");
        settingsCache.models = await api("GET", "/api/models");
        settingsCache.agents = await api("GET", "/api/agents");
        settingsTab = "providers";
        renderSettings();
        $("#settingsOverlay").classList.add("open");
    }
    function closeSettings() { $("#settingsOverlay").classList.remove("open"); }

    function renderSettings() {
        const modal = $("#settingsModal");
        const tabs = SETTINGS_TABS.map(t => `<button class="modal-tab ${t.id === settingsTab ? "active" : ""}" data-tab="${t.id}">${t.label}</button>`).join("");
        let body = "";
        if (settingsTab === "providers") body = renderProviders();
        else if (settingsTab === "models") body = renderModels();
        else if (settingsTab === "agents") body = renderAgentsSettings();
        else if (settingsTab === "memory") body = renderMemory();
        else if (settingsTab === "about") body = `<p>Flora Hanako — Java 版 openhanako（方案 B：轻量嵌入式 Web）。</p><p>后端 Javalin + flora-root ai.api；前端复刻 openhanako 暖纸视觉。</p>`;
        modal.innerHTML = `<h2>设置</h2><div class="modal-tabs">${tabs}</div>${body}
            <div class="modal-actions"><button class="btn" id="settingsClose">关闭</button></div>`;
        modal.querySelectorAll(".modal-tab").forEach(b => b.onclick = () => { settingsTab = b.dataset.tab; renderSettings(); });
        $("#settingsClose").onclick = closeSettings;
        wireSettings(modal);
    }

    function renderProviders() {
        const apiKinds = ["OPENAI_OFFICIAL", "OPENAI_LIKE", "ANTHROPIC_OFFICIAL", "GEMINI_OFFICIAL", "DEEPSEEK_OFFICIAL"];
        const rows = settingsCache.providers.map((p, i) =>
            `<div class="field"><label>名称</label><input data-p="${i}" data-k="name" value="${escHtml(p.name || "")}">
             <label>ApiKind</label><select data-p="${i}" data-k="apiKind">${apiKinds.map(k => `<option ${k === p.apiKind ? "selected" : ""}>${k}</option>`).join("")}</select>
             <label>Base URL</label><input data-p="${i}" data-k="baseUrl" value="${escHtml(p.baseUrl || "")}">
             <label>API Key</label><input data-p="${i}" data-k="apiKey" type="password" value="${escHtml(p.apiKey || "")}"></div>`
        ).join("");
        return `<p class="input-hint">OpenAI 兼容协议（支持 OpenAI / DeepSeek / 通义 / Ollama 等）。</p>${rows}
            <button class="btn primary" id="addProvider">+ 添加提供商</button>
            <button class="btn primary" id="saveProviders" style="margin-left:8px">保存</button>`;
    }
    function renderModels() {
        const roles = ["CHAT", "UTILITY", "HEAVY"];
        const allEndpoints = settingsCache.providers.map(p => `${p.apiKind}@${p.baseUrl || ""}`);
        const rows = roles.map(r => {
            const cfg = settingsCache.models[r];
            return `<div class="field"><label>${r === "CHAT" ? "对话模型" : r === "UTILITY" ? "小工具模型" : "大工具模型"}（端点）</label>
                <input data-m="${r}" data-k="displayName" placeholder="显示名 如 gpt-4o" value="${escHtml(cfg ? cfg.displayName || "" : "")}">
                <input data-m="${r}" data-k="endpointId" placeholder="端点 id（保存后可联调）" value="${escHtml(cfg ? cfg.endpointId || "" : "")}"></div>`;
        }).join("");
        return `<p class="input-hint">首次运行需选择三个模型：主对话 / 轻量工具 / 重型工具。</p>${rows}
            <button class="btn primary" id="saveModels">保存模型</button>`;
    }
    function renderAgentsSettings() {
        const rows = settingsCache.agents.map((a, i) =>
            `<div class="field"><label>名称</label><input data-a="${i}" data-k="name" value="${escHtml(a.name || "")}">
             <label>人格 (identity)</label><textarea data-a="${i}" data-k="identity" rows="3">${escHtml(a.identity || "")}</textarea>
             <label>心识 (ishiki)</label><textarea data-a="${i}" data-k="ishiki" rows="2">${escHtml(a.ishiki || "")}</textarea></div>`
        ).join("");
        return `<p class="input-hint">Agent 即文件夹：独立人格、记忆与定时任务。</p>${rows}
            <button class="btn primary" id="saveAgents">保存 Agents</button>`;
    }
    function renderMemory() {
        return `<div class="field"><label>写入一条记忆</label>
            <input id="memText" placeholder="事实 / 偏好文本">
            <input id="memTags" placeholder="标签，逗号分隔，如 偏好,工作流"></div>
            <button class="btn primary" id="memAdd">记住</button>`;
    }
    function wireSettings(modal) {
        if (settingsTab === "providers") {
            modal.querySelector("#addProvider") && (modal.querySelector("#addProvider").onclick = () => {
                settingsCache.providers.push({ name: "新提供商", apiKind: "OPENAI_OFFICIAL", baseUrl: "https://api.openai.com/v1", apiKey: "" });
                renderSettings();
            });
            modal.querySelector("#saveProviders") && (modal.querySelector("#saveProviders").onclick = saveProviders);
        } else if (settingsTab === "models") {
            modal.querySelector("#saveModels") && (modal.querySelector("#saveModels").onclick = saveModels);
        } else if (settingsTab === "agents") {
            modal.querySelector("#saveAgents") && (modal.querySelector("#saveAgents").onclick = saveAgents);
        } else if (settingsTab === "memory") {
            modal.querySelector("#memAdd") && (modal.querySelector("#memAdd").onclick = () => {
                const t = $("#memText").value.trim(); if (!t) return;
                const tags = $("#memTags").value.split(",").map(s => s.trim()).filter(Boolean);
                api("POST", "/api/memory", { text: t, tags }).then(() => { toast("已记住"); renderMemory(); });
            });
        }
    }
    function saveProviders() {
        const items = [...$("#settingsModal").querySelectorAll("[data-p]")];
        const byIdx = {};
        items.forEach(el => {
            const i = el.dataset.p, k = el.dataset.k;
            byIdx[i] = byIdx[i] || { ...settingsCache.providers[i] };
            byIdx[i][k] = el.value;
        });
        const jobs = Object.values(byIdx).map(p => api("POST", "/api/providers", p));
        Promise.all(jobs).then(() => { toast("提供商已保存"); openSettings(); }).catch(e => toast(e.message, "error"));
    }
    function saveModels() {
        const roles = ["CHAT", "UTILITY", "HEAVY"];
        const jobs = roles.map(r => {
            const dn = $(`[data-m="${r}"][data-k="displayName"]`).value;
            const ep = $(`[data-m="${r}"][data-k="endpointId"]`).value;
            return api("POST", "/api/models", { role: r, displayName: dn, endpointId: ep });
        });
        Promise.all(jobs).then(() => toast("模型已保存")).catch(e => toast(e.message, "error"));
    }
    function saveAgents() {
        const items = [...$("#settingsModal").querySelectorAll("[data-a]")];
        const byIdx = {};
        items.forEach(el => {
            const i = el.dataset.a, k = el.dataset.k;
            byIdx[i] = byIdx[i] || { ...settingsCache.agents[i] };
            byIdx[i][k] = el.value;
        });
        const jobs = Object.values(byIdx).map(a => api("POST", "/api/agents", a));
        Promise.all(jobs).then(() => { toast("Agents 已保存"); loadAgents(); }).catch(e => toast(e.message, "error"));
    }

    // ── 顶部栏交互 ──
    function initChrome() {
        $("#tbToggleLeft").onclick = () => $("#sidebar").classList.toggle("collapsed");
        $("#settingsBtn").onclick = openSettings;
        $("#settingsOverlay").onclick = (e) => { if (e.target === $("#settingsOverlay")) closeSettings(); };
        $("#newSessionBtn").onclick = newSession;
    }

    // ── 初始化 ──
    async function loadAgents() {
        state.agents = await api("GET", "/api/agents");
        if (!state.agents.find(a => a.id === state.currentAgent)) {
            state.currentAgent = state.agents[0] ? state.agents[0].id : "hanako";
        }
        renderAgents();
    }

    async function init() {
        initChrome();
        initInput();
        initDesk();
        try {
            await loadAgents();
            await loadSessions();
        } catch (e) { toast("加载失败: " + e.message, "error"); }
        connectWs();
    }

    document.addEventListener("DOMContentLoaded", init);
})();
