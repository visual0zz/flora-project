# 设计：音频驱动的单文件 Canvas 音乐短片管线

> 归类：本文是对 `absent/otherprojects/letMeGo.html`（第三方项目）的**逆向提炼**，归入 `addition/reverse-engineer/`，并非原创方案设计。
> 性质：以直接阅读源码得到的**确证**内容为主（见 §15 参考实现索引的逐行对照），而非推测。
> 用途：把参考实现的做法提炼成可复用的通用管线方法，供后续独立复刻新片。

日期：2026-09-19
状态：方法论文档（供后续复用）
参考实现：`absent/otherprojects/letMeGo.html`（3987 行，3.8MB，2 分 05 秒成片）

## 0. 文档用途

本文记录"把一首歌做成代码驱动动画短片"的**可复用管线**：数据怎么存、声画怎么对齐、
每一步用什么工具、验收怎么测。目标是照本文能独立复刻一部新片，而不必再逆向参考实现。

适用范围与前提：

- 载体是**单个 HTML 文件**，双击即播，无服务器、无外部资源、无构建产物依赖。
- 画面全部由 **Canvas 2D 立即模式代码**绘制，不使用 AE / 剪辑软件 / 逐帧手绘素材。
- 声画对齐需人工打轴（歌词时间戳），因此适合**歌词驱动**的作品（MV、歌词视频、
  叙事歌曲）。纯氛围片可省略打轴阶段，但建议保留段落表。

不适用：需要精细角色骨骼动画、实拍剪辑、多轨音频后期混音的场景。

---

## 1. 成品形态与体积预算

单文件内部由四块数据构成，其中两块是内联的大数据：

```html
<script id="audio-b64" type="text/plain">     <!-- ① 音频 base64 -->
<script id="env-json"  type="application/json"><!-- ② 音频特征包络 -->
<script>                                       <!-- ③ 歌词分镜表 + ④ 场景代码 -->
```

体积预算（以 2 分钟片为基准，实测值）：

| 部分 | 内容 | 体积 | 占比 |
|---|---|---|---|
| ① 音频 | M4A/AAC 约 170kbps，base64 后 | 2.6 MB | 94% |
| ② 包络 | 5 通道 × 60fps × 时长，base64 后 | ~50 KB | 2% |
| ③④ 代码 + 标记 | 场景函数 40 个 + 样式 + UI | ~150 KB | 4% |

结论：**体积几乎完全由音频决定**。想压缩就降码率（128kbps 仍可接受）或缩短片长；
包络与代码怎么膨胀都不会成为瓶颈，不要为省几十 KB 牺牲代码可读性。

---

## 2. 架构总览

### 2.1 数据流

```
   原始音频 ──┐
              │
              ├─[阶段 B 离线分析]─→ env-json（BPM/相位/5 通道包络）──┐
              │                                                      │
   歌词文本 ──┴─[阶段 C 人工打轴]─→ RAW 表（毫秒级段落）──┐          │
                                                          │          │
                        [阶段 D 编译期] ←─────────────────┘          │
                                │                                    │
                                ├─→ SEGS（段 + 渲染窗口归一）         │
                                └─→ MOODS（时间→色调色带）            │
                                                                     │
                        [阶段 E 场景函数 × N] ←──────────────────────┤
                                │                                    │
                        [阶段 F 内联打包] ←── 音频 ─────────────────┘
                                │
                                ▼
                    dist/index.html（单文件成品）
                                │
                        [阶段 H 导出模式] ─→ PNG 序列 ─→ ffmpeg ─→ MP4
```

### 2.2 三个核心不变量

这三条是整个管线能成立的根本，任何改写都不能破坏：

**不变量 1：时间基准唯一。**
全片只有一个时间源：`t = audio.currentTime - offset`。
`offset` 是暴露给用户的全局微调（±10ms / ±100ms 按钮），仅用于兜底。
绝不允许出现第二个时钟（如 `performance.now()` 累加、帧计数器）。

**不变量 2：画面是 `t` 的纯函数。**
任何时刻 `paint(t)` 的输出只依赖 `t` 与静态数据，不依赖"上一帧画了什么"。
由此自动获得三个性质，无需额外代码：
- 暂停/继续后画面必然一致；
- 任意 seek、拖动进度条后画面必然一致；
- 离线导出与交互播放逐像素一致。

代价：不能用帧间增量累积（如 `x += v`）、不能依赖 `Math.random()`。
需要用噪声时改用**确定性哈希**：`hash(floor(t * 60))`（见 §7.3）。

**不变量 3：对齐真值只有一处。**
歌词时间戳只写在 RAW 表里。场景函数内部出现的硬编码时间点（如"11.50 秒时闪回"）
属于**编排细节**，不是对齐真值，允许存在但必须能追溯到 RAW 或音频特征。

---

## 3. 建议的项目布局

单文件是**产物**，不是开发形态。开发期按下面拆分，最后合并：

```
myvideo/
├── assets/audio.m4a           # 原始音频（唯一无法省略的二进制）
├── data/lyrics.js             # RAW 表（阶段 C 产出，人工维护）
├── data/env.json              # 阶段 B 产出（机器生成，不手改）
├── src/
│   ├── core/math.js           # clamp/lerp/sstep/缓动/hash
│   ├── core/color.js          # hx/rgb/rgba/mixc + 调色板常量 C
│   ├── core/canvas.js         # px/glow/disc/ring/bar/rrect/line/bez/txt/layout
│   ├── core/lyrics.js         # wordSched + LY 通用歌词渲染器
│   ├── scenes/*.js            # 每个场景一个文件，注册进 S 表
│   ├── layers.js              # 跨场景的全局母题层
│   └── main.js                # state / buildState / cameraTransform / paint / loop
├── tools/
│   ├── analyze.py             # 阶段 B：音频 → env.json
│   ├── build.py               # 阶段 F：内联打包 → dist/index.html
│   └── export.js              # 阶段 H：无头逐帧导出
└── dist/index.html            # 产物
```

打包脚本只需做一件事：把 `data/env.json` 的文本、`assets/audio.m4a` 的 base64、
以及所有 JS 片段按顺序塞进一个 HTML 模板。**不要引入 webpack/vite**——
这套东西没有依赖、没有 tree-shaking 需求，字符串拼接就够，且更容易审计产物。

---

## 4. 阶段 A：素材准备

1. 产出音频文件。若是 AI 生成的人声，注意**人声与伴奏要在同一条轨上导出**，
   否则分离轨延迟差异会让打轴永远对不准。
2. 转码为 M4A/AAC，单轨、立体声、44.1kHz。
3. 记录**精确时长**（毫秒级，如 `125.376`）。这个数字后面要写进三处：
   `DURATION`、`env.json.dur`、以及 RAW 表末段的 `end` 兜底值。
   三处不一致会导致自检的"终点对齐"项失败。

---

## 5. 阶段 B：离线音频分析 → env-json

### 5.1 为什么必须离线

运行时绝不做 FFT。理由有三：
- 每帧 FFT 在低端设备上直接吃掉一半帧预算；
- 实时 FFT 的窗口位置与帧对齐耦合，seek 后结果不可复现（违反不变量 2）；
- 离线可用更好的算法（多帧平滑、perceptual 加权、百分位归一），质量更高。

代价是包络与音频**强绑定**：换一版音频就必须重跑阶段 B。这个代价可以接受。

### 5.2 字段规格

```jsonc
{
  "fps":   60.0,        // 包络采样率，建议 60，与渲染帧率一致
  "n":     7524,        // 每通道采样点数，需 >= ceil(dur*fps)+1
  "dur":   125.376,     // 音频时长（秒），与 DURATION 一致
  "bpm":   128.5,       // 全局 BPM
  "beat":  0.466926,    // 一拍秒数，必须 = 60/bpm（自检项）
  "phase": 0.1058,      // 第 0 拍的时刻（秒），可超出 [0,beat)
  "rms":   "base64…",   // 以下 5 个通道：每帧 1 字节（0-255）的 Uint8 序列
  "bass":  "base64…",   // 20-250 Hz
  "mid":   "base64…",   // 250-2000 Hz
  "high":  "base64…",   // 2000-8000 Hz
  "onset": "base64…"    // 起始检测强度（差分半波整流）
}
```

读取端约定（对应参考实现 `letMeGo.html:288-293`）：

```js
const FPS = ENVRAW.fps;
const E = { rms: b64u8(ENVRAW.rms), /* ... */ };
function ef(a, t) {                       // sample a channel at time t
  if (t <= 0) return 0;
  const i = Math.round(t * FPS);
  return a[i >= a.length ? a.length - 1 : (i < 0 ? 0 : i)] / 255;   // index clamped
}
const BPM = ENVRAW.bpm, BEAT = ENVRAW.beat, BEATPHASE = ENVRAW.phase;
function bi(t) { const x = (t - BEATPHASE) / BEAT; const i = Math.floor(x); return { i, p: x - i }; }
function hit(t, d) { return Math.exp(-bi(t).p * (d || 5.5)); }   // beat-decay impulse, 0..1
```

要点：
- **包络用字节量化**，`/255` 即得 0..1。比存浮点 JSON 小 5 倍以上。
- **读取端对索引做钳制**，所以 `n` 多一两个帧无害；但少一帧会在片尾静音区报 `undefined`。
- `BEAT` 必须严格等于 `60/BPM`，不要独立存两个精度不同的值。
- `phase` 取"第 0 拍的时刻"，允许为负或大于一拍，`bi()` 的 `floor` 会正常取整。

### 5.3 参考实现（Python + librosa）

```python
# tools/analyze.py -- offline audio analysis -> data/env.json
# Requires: librosa, numpy, soundfile, ffmpeg on PATH
import base64, json, sys
import numpy as np
import librosa

SR      = 22050          # analysis sample rate (mono)
FPS     = 60             # envelope frame rate
AUDIO   = "assets/audio.m4a"
OUT     = "data/env.json"

y, sr = librosa.load(AUDIO, sr=SR, mono=True)
dur = float(len(y) / sr)
n   = int(np.floor(dur * FPS)) + 2

# ---- 1. tempo + beat phase -------------------------------------------------
tempo, beats = librosa.beat.beat_track(y=y, sr=sr, units="time")
bpm   = float(np.atleast_1d(tempo)[0])
beat  = 60.0 / bpm
# Robust phase: least-squares fit of beats to a constant-period grid,
# then take the fitted time of beat index 0.
k = np.arange(len(beats))
phase = float(np.median(beats - k * beat)) if len(beats) else 0.0

# ---- 2. frame grid --------------------------------------------------------
hop  = int(round(sr / FPS))
# frame i covers samples [i*hop, (i+1)*hop)
def to_grid(x):
    """Reduce a per-sample or per-STFT-frame signal onto the FPS grid."""
    out = np.zeros(n, dtype=np.float32)
    m = min(n, len(x))
    out[:m] = x[:m]
    return out

# ---- 3. loudness ----------------------------------------------------------
rms_s = librosa.feature.rms(y=y, frame_length=hop * 2, hop_length=hop)[0]

# ---- 4. band energies -----------------------------------------------------
S = np.abs(librosa.stft(y, n_fft=2048, hop_length=hop))
freqs = librosa.fft_frequencies(sr=sr, n_fft=2048)
def band(lo, hi):
    sel = (freqs >= lo) & (freqs < hi)
    return S[sel].mean(axis=0)
bass_s, mid_s, high_s = band(20, 250), band(250, 2000), band(2000, 8000)

# ---- 5. onset strength ----------------------------------------------------
onset_s = librosa.onset.onset_strength(y=y, sr=sr, hop_length=hop)

def quantize(x):
    """Percentile normalize -> gamma -> uint8. Tune gamma per channel to taste."""
    x = np.asarray(x, dtype=np.float32)
    x = np.maximum(x, 0.0)
    lo, hi = np.percentile(x, 2.0), np.percentile(x, 98.0)
    if hi - lo < 1e-9:
        hi = lo + 1e-9
    x = np.clip((x - lo) / (hi - lo), 0.0, 1.0) ** 0.85   # gamma shapes the punch
    return (x * 255.0).round().astype(np.uint8)

env = {
    "fps": FPS, "n": n, "dur": round(dur, 3),
    "bpm": round(bpm, 3), "beat": round(beat, 6), "phase": round(phase, 4),
}
for name, sig in (("rms", rms_s), ("bass", bass_s), ("mid", mid_s),
                  ("high", high_s), ("onset", onset_s)):
    u8 = quantize(to_grid(sig))
    env[name] = base64.b64encode(u8.tobytes()).decode("ascii")

with open(OUT, "w", encoding="utf-8") as f:
    json.dump(env, f, ensure_ascii=True, separators=(",", ":"))
print("bpm=%.3f beat=%.6f phase=%.4f dur=%.3f → %s" % (bpm, beat, phase, dur, OUT))
```

调参经验（这是"画面跟不跟得上歌"的唯一旋钮）：

| 通道 | gamma | 用途 |
|---|---|---|
| `rms` | 0.8~0.9 | 整体呼吸、氛围光强度 |
| `bass` | 0.7~0.8 | 低频冲击、镜头推拉、地面震动 |
| `mid` | 0.9~1.0 | 人声段能量，驱动中性元素 |
| `high` | 1.0~1.1 | 高频细节、闪烁、粒子 |
| `onset` | 0.6~0.7 | 瞬时打击（**最需要"尖锐"**，gamma 最低） |

### 5.4 阶段 B 验收

- `abs(beat - 60/bpm) < 1e-6`
- `len(base64.b64decode(env['rms'])) == n`
- 把 `phase` 与 `beat` 叠加画在波形上，拍点必须落在重拍上（人工看一次，
  `beat_track` 会出错半拍或倍速，出错就用 `librosa.beat.plp` 或手工指定 phase）
- 片尾静音段各通道应接近 0

---

## 6. 阶段 C：歌词打轴 → RAW 表

### 6.1 表结构

每行一句，七列，**时间单位秒、精度毫秒**：

```js
/* S = [start, end, english, chinese, scene, pole, pos] */
const RAW = [
  [ 0.700,  4.016, 'let me go~',            '让我溜',           'birth', 'dream', 'b'],
  [ 7.366, 10.216, 'let me write the JSON,', '来帮我写那个JSON', 'cage',  'work',  'c'],
  [87.650, 94.683, '[间奏]',                 'INSTRUMENTAL BREAK','brk1', 'mixed', 'c'],
  // ...
];
```

| 列 | 名称 | 语义 |
|---|---|---|
| 0 | `start` | 该句人声起始（**注意区分"人声起"与"该句画面起"**，见下） |
| 1 | `end` | 该句人声结束。允许比实际唱词短，渲染端会给最小可读时长兜底 |
| 2 | `english` | 主歌词原文。写 `[间奏]` 标记器乐段（用固定子串检测） |
| 3 | `chinese` | 译句或副标题，可留空 |
| 4 | `scene` | **场景函数名**，必须与 `S` 注册表 key 一一对应 |
| 5 | `pole` | 情绪极：`dream` / `work` / `mixed`。用于相机幅度、HUD 配色 |
| 6 | `pos` | 画面重心：`b`(bottom) / `c`(center)。仅为编写场景时的备忘，可选 |

### 6.2 打轴流程

1. **ASR 粗对齐**：用 Whisper / FunASR 带时间戳模式跑一遍，得到句子级时间戳。
2. **人工精修**：在 DAW 或 Audacity 里对着波形逐句修 `start`。
   精修口诀：**`start` 对准辅音爆破点，`end` 留到人声完全衰减之后 80~120ms**。
3. **补间奏**：所有 > 1.5 秒的无歌词区间都要显式建一段 `[间奏]`，
   给它独立场景。见 §7.4（为什么这是硬要求）。
4. **填末段兜底**：最后加一条 `[123.400, 999, '', '', 'coda', 'dream', 'c']`，
   `end` 写 `999` 之类的哨兵值，让编译期用真实时长兜底。

### 6.3 打轴的三个常见坑

- **句尾截断**：SRT 风格的 `end` 常比实际唱词短（急促的拟声词尤其明显）。
  不要靠改 `end` 解决，渲染端按音节数给最小可读时长（见 §8.3）。
- **画面起早于声音**：想让画面先于歌声出现（预演）时，**应改 `start` 之后用
  单独的编排时间点控制画面，而不是把 `start` 提前**——否则歌词也会跟着提前。
- **呼吸/换气段归属**：一句唱完到下一句起之间的气口，归给**上一句的渲染窗口**
  （见 §7.1 的窗口归一），不要新建段落。

---

## 7. 阶段 D：编译期派生

运行时启动时（不是构建时）由 RAW 派生两个结构。放在运行时是为了让 RAW 保持
可读、可直接改，改完刷新页面即见效，无需重新构建。

### 7.1 SEGS：段落 + 渲染窗口归一

这是整个对齐机制的枢纽。原始 RAW 的 `[start, end]` 只描述了**人声区间**，
中间必然有气口、间奏、尾奏。如果画面只在人声区间内工作，就会出现"空洞帧"。

做法：给每段补一个 `renderEnd` = **下一段的 `start`**。

```js
const SEGS = [];
const DURATION = 125.376;                       // must equal env.dur

(function buildSegs() {
  // 0. prelude before the first lyric line
  SEGS.push({ t0: 0, t1: 0.700, en: '', zh: '', sc: 'prelude',
              pole: 'dream', pos: 'c', inst: true });
  for (let i = 0; i < RAW.length; i++) {
    const r = RAW[i];
    const next = (i + 1 < RAW.length) ? RAW[i + 1][0] : DURATION;
    SEGS.push({ t0: r[0], t1: Math.max(r[1], r[0] + 0.2), winEnd: next,
                en: r[2], zh: r[3], sc: r[4], pole: r[5], pos: r[6],
                inst: r[2].indexOf('间奏') >= 0 });
  }
  // 1. extend each render window to the next line's start
  for (let i = 0; i < SEGS.length; i++) {
    SEGS[i].renderEnd = (i + 1 < SEGS.length) ? SEGS[i + 1].t0 : DURATION;
    SEGS[i].T = SEGS[i].renderEnd - SEGS[i].t0;
  }
})();
```

结果：`renderEnd` 序列首尾相接、无缝、末端精确落在 `DURATION`。
这条性质由自检强制（见 §11.1 第 1 项），是本管线最有效的一个正确性护栏。

段落定位用二分（段落数虽少，但要支持逐帧调用）：

```js
function segIndexAt(t) {
  if (t <= 0) return 0;
  let lo = 0, hi = SEGS.length - 1, ans = 0;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (SEGS[mid].t0 <= t) { ans = mid; lo = mid + 1; } else hi = mid - 1;
  }
  return ans;
}
```

### 7.2 MOODS：时间 → 色调

把情绪做成时间轴上的色带，而不是每个场景各自选色。这样场景切换时颜色是
**连续渐变**的，不会"跳色"，整片观感统一。

每条记录 `[t, 背景上色, 背景下色, 强调色]`，相邻记录之间用
`smoothstep` 插值（不要线性，线性会让色调过渡显得生硬）：

```js
const MOODS = [
  [ 0.0,  '#04060c', '#0d0a16', '#ffb35c'],
  [ 4.0,  '#050810', '#08101f', '#5ce1ff'],
  [11.6,  '#070812', '#150c1e', '#ff5ec4'],
  // ... 约 20 个关键点覆盖全曲
  [125.4, '#030407', '#05070c', '#ffb35c'],
];
function moodAt(t) { /* find bracketing pair, sstep-interpolate three colors */ }
```

设计要点：
- 色带关键点应落在**段落边界**（即 RAW 的 `start`）附近，与场景切换同步。
- 每个情绪极固定一个强调色域：`dream`→暖紫粉、`work`→冷青蓝、`mixed`→金。
  全片只用这几个色域，靠明度与饱和度变化区分段落。
- 首尾都用同一组色，形成收束感。

### 7.3 确定性噪声

替代 `Math.random()` 的唯一手段：

```js
function hash(n) {                       // 0..1, deterministic
  n = n | 0; n = (n ^ 61) ^ (n >>> 16); n = (n + (n << 3)) | 0;
  n = n ^ (n >>> 4); n = Math.imul(n, 0x27d4eb2d); n = n ^ (n >>> 15);
  return (n >>> 0) / 4294967295;
}
const hash2 = (i, j) => hash(Math.imul(i | 0, 374761393) + Math.imul(j | 0, 668265263));
```

用法：把时间量化成帧号再取噪。**不同用途必须用不同的盐（`+3`、`+11`、`+17`）**，
否则所有抖动会同步，看起来像全屏抖动：

```js
const sx = (hash(Math.floor(t * 60) + 3)  - 0.5) * 2 * amp;   // camera jitter X
const sy = (hash(Math.floor(t * 60) + 11) - 0.5) * 2 * amp;   // camera jitter Y
const tilt = (hash(Math.floor(t * 30) + 17) - 0.5) * 0.012;   // camera tilt (half rate)
```

例外：**资源构建期**（生成噪声纹理、粒子初始分布）可以用真的 `Math.random()`，
因为结果是一次性固定下来的。参考实现里 `buildGrain()` 用 `Math.random()`
生成颗粒贴图，但每帧的偏移量用 `hash(f)`（`letMeGo.html:358-382`）。

### 7.4 覆盖性硬约束

自检要求：**任意时刻 `t ∈ [0, DURATION]` 都必须落在某个段落的渲染窗口内。**

因此：
- 每个 `[间奏]` 必须建成独立段落并配独立场景（不能跳过，否则间奏期间画面无人负责）；
- 每段的 `scene` 必须在 `S` 注册表里存在；
- 段落之间不能留缝（`SEGS[i].renderEnd === SEGS[i+1].t0`）。

---

## 8. 阶段 E：场景函数编写规范

### 8.1 注册方式与命名

```js
const S = {};                       // scene registry
S.birth = (st) => { /* draw */ };
S.cage  = (st) => { /* draw */ };
```

`S` 的 key 必须与 RAW 第 4 列逐字相同。名字用短英文小写，能唤起画面意象
（`cage` / `skyline` / `shatter` / `scatter`），不要用 `scene01` 这类无意义编号——
后期调整顺序时编号会全乱。

### 8.2 场景能拿到的全部输入

场景函数只有一个参数 `st`（state），内容在进入场景前由 `buildState(t)` 一次性算好：

```js
function buildState(t) {
  const si = segIndexAt(t);
  const sg = SEGS[si];
  state.t = t;  state.segIndex = si;  state.seg = sg;
  state.lt = t - sg.t0;                                 // 本句内已过时间
  state.T  = sg.T;                                      // 本句渲染窗口长度
  state.p  = clamp(state.lt / Math.max(0.001, sg.T), 0, 1);
  state.e    = ef(E.rms,   t);                          // 以下 5 个为 0..1
  state.bass = ef(E.bass,  t);
  state.mid  = ef(E.mid,   t);
  state.high = ef(E.high,  t);
  state.o    = ef(E.onset, t);
  state.mood = moodAt(t);                               // {top, bot, acc} 三个 RGB 数组
  state.pole = sg.pole;
  return state;
}
```

| 字段 | 类型 | 典型用法 |
|---|---|---|
| `st.t` | 秒 | 全局编排、与音频特征配合 |
| `st.lt` | 秒 | **句内局部时间**，几乎所有入场动画都用它 |
| `st.p` | 0..1 | 本句进度，用于整句级别的渐变 |
| `st.e` | 0..1 | 整体响度 → 呼吸、氛围光 |
| `st.bass` | 0..1 | 低频 → 冲击、推镜、地面 |
| `st.mid` `st.high` | 0..1 | 中高频 → 中性元素、细节闪烁 |
| `st.o` | 0..1 | 瞬时起始 → 打击点 |
| `st.mood` | RGB×3 | 必须用它取色，不要写死颜色 |
| `st.pole` | 枚举 | 调节力度（如 `dream` 段相机抖幅 ×0.45） |

**注意 `st` 是共享可变对象**，`paint()` 每帧复用它。场景函数**不得持有 `st` 引用
跨帧使用**，需要存值就存数值快照。

### 8.3 通用歌词渲染器 LY

不要让每个场景自己画歌词，否则风格会散。提供唯一入口，场景只传版面参数：

```js
LY(st, { y: 0.86, size: 34, mode: 'rise', color: C.paper, hot: C.gold,
         stagger: 0.026, inDur: 0.36, zhSize: 17 });
```

`LY` 内部依次处理（对应 `letMeGo.html:512`）：

1. **最小可读时长兜底**——这是最关键的一步。SRT 式 `end` 常比实际唱词短，
   若直接用会把字挤成一团：

   ```js
   const nSyl  = (sc.en.match(/[aeiouy]+/gi) || []).length || 4;
   const minDur = Math.min(2.2, 0.13 * nSyl + 0.30);
   const s1 = Math.min(sc.renderEnd - 0.04, Math.max(sc.t1, s0 + minDur));
   ```

   即：显示时长取 max(标注时长, 音节估算时长)，再钳制不超过渲染窗口。

2. **词级调度** `wordSched(str, t0, t1, lead, tail)`——把句子时长按
   **元音组数加权**分配到每个词（元音组数 ≈ 音节数）。这是启发式近似，
   不是强制对齐，但对歌唱文本足够用：

   ```js
   function wordSched(str, t0, t1, lead, tail) {
     const ws  = str.split(' ');
     const wts = ws.map(w => Math.max(2, (w.match(/[aeiouy]+/gi) || []).length));
     const sum = wts.reduce((a, b) => a + b, 0) || 1;
     let acc = t0 + (lead == null ? 0.08 : lead);
     const span = Math.max(0.1, (t1 - (tail == null ? 0.12 : tail)) - acc);
     return ws.map((w, i) => {
       const d = span * wts[i] / sum, o = { s: acc, e: acc + d };
       acc += d; return o;
     });
   }
   ```

3. **逐字入场**：每字符延迟 `i * stagger`，`inState(mode, u)` 给出位移/缩放/旋转。
   预置 9 种入场模式：`rise` `fall` `type` `pop` `elastic` `zoom` `spin` `side` `wipe`
   （外加 `slam` `scatter` 等特化）。场景只需选模式，不写动画曲线。

4. **当前词高亮**：把词的时间窗映射到颜色，正在唱的词用 `hot`，其余用 `base`。

5. **中文译句**：延迟 0.26s 淡入、结束前淡出，与主句错开，避免同时入场。

6. **版面安全区**：`cy = clamp(y*H, H*0.09, H*0.79)`，防止字幕撞上底部控制条。

间奏段特殊处理：只显示 `— 中文 —` 一行提示，**2.6 秒后自动消失**，
不占画面（`letMeGo.html:532-540`）。

### 8.4 画布工具层

所有绘制走一层薄封装，屏蔽 DPR 与分辨率差异：

```js
const px = v => v * U;      // U = min(W/1440, H/810)  —— 设计稿基准 1440×810
```

`U` 的设计意图：所有坐标用**设计稿像素**书写，缩放由 `U` 统一完成。
导出 1920×1080 与窗口内播放共用同一份场景代码，靠 `U` 与 `DPR` 抹平差异
（`letMeGo.html:343-353`）。

必备工具函数：`glow(c,b,a)` / `noglow()`（发光开关）、`disc` / `ring` / `bar`
/ `rrect` / `line` / `bez` / `txt`（带字距） / `layout`（返回逐字 token 与总宽）。

### 8.5 场景编写硬性约束

- **禁止 `Math.random()`**（除非在构建期生成静态资源）。
- **禁止帧间状态**：不写 `let x = 0; x += v`。要动就在函数里由 `st.lt` 算出位置。
- **禁止读取 DOM / 测量布局**：`W`/`H` 由全局维护，场景只读。
- **颜色一律取自 `st.mood`**，禁止写死十六进制（色调连贯性靠这一条保证）。
- **必须用 `ctx.save()/restore()` 包住所有变换**，否则会污染后续场景。
- **允许抛异常**：主循环会捕获并继续（`letMeGo.html:3629-3634`），
  单个场景出错不会导致整片黑屏，错误被收集到 `paint.errs` 供自检读取。

### 8.6 全局母题层

除了逐句场景，还需要一层**跨场景的持续元素**——贯穿全片的意象
（参考实现是一片光鲸、开场星尘、唤醒波纹、网格、光条）。

关键性质：母题层**不属于任何段落**，靠自己的时间窗口开关；
且它排在场景函数**之前**绘制（作为背景层），另一个"收尾层"排在场景之后。

一个值得复用的技巧：同一个母题函数可以**二次调用并覆盖时间窗口**，
用来做闪回。参考实现在牢笼炸开瞬间（11.50s）让光鲸闪回一次：

```js
whaleCloud(st);                                   // normal pass
whaleCloud(st, { t0: 11.50, inDur: 0.42, hold: 12.05, outDur: 1.75,
                 Sscale: 1.14, alphaMul: 1.45, edgeOnly: true });   // flashback
```

### 8.7 paint() 的绘制顺序

这是全片观感的骨架，顺序错了效果会互相压制（对应 `letMeGo.html:3601-3672`）：

```
1. 背景渐变（st.mood 双色）
2. 氛围光（disc + globalCompositeOperation='lighter'，随 st.e / st.bass 呼吸）
3. ctx.save() → cameraTransform(st)          ← 全场共用一个相机变换
4. 全局母题层：星尘 → 光鲸 → 唤醒波纹 → 网格
5. 场景函数 S[st.seg.sc](st)  （内部调 LY 画歌词）
6. 收尾层：光条
7. 开场收尾：14s 处的全屏泛光 + 收束光环
8. ctx.restore()  恢复相机
9. 暗角 vignette() → 颗粒 grain(t) → 边角标记
```

---

## 9. 阶段 F：四层对齐模型

声画对齐是四个粒度叠加的结果，各层互相独立、可单独调试：

| 层级 | 数据来源 | 时间粒度 | 驱动什么 | 出错时的症状 |
|---|---|---|---|---|
| **帧级** | `env-json` 5 通道 | 1/60 s | 发光、呼吸、抖动幅度 | 画面与音乐"无关"，像贴图 |
| **节拍级** | `bpm` / `beat` / `phase` | 1 拍 | 镜头 punch、闪白、色块跳动 | 踩点偏半拍，动作像在"打空拍" |
| **句级** | RAW `[start, end]` + SEGS 窗口 | 1 句 | 切场景、切歌词、切色调 | 场景切换早于/晚于人声 |
| **词级** | `wordSched` 元音加权 | 1 词 | 逐字高亮、逐字入场 | 字亮得比唱得早/晚 |

### 9.1 节拍级的标准写法

```js
const BEAT = ENVRAW.beat, BEATPHASE = ENVRAW.phase, BAR = BEAT * 4;
function bi(t) { const x = (t - BEATPHASE) / BEAT; const i = Math.floor(x); return { i, p: x - i }; }
function hit(t, d)  { return Math.exp(-bi(t).p * (d || 5.5)); }  // decay over the beat
function kill(t, d) { return Math.exp(-bi(t).p * (d || 9));   }  // faster decay, for punch
```

`p` 是拍内相位 0..1，`exp(-p*d)` 给出一个"每拍归零再衰减"的脉冲。
`d` 越大越"脆"（快速衰减），越小越"绵"（衰减慢）。同一条公式配不同 `d`
就能覆盖从鼓点到弦乐铺垫的全谱需求，不必引入包络跟随器。

### 9.2 全局偏移兜底

始终暴露一个用户可调的 `offset`，并在 UI 上给 ±10ms / ±100ms / 归零 按钮。
作用不是给用户玩，而是**给不同播放设备兜底**：蓝牙耳机有 100~200ms 延迟，
`audio.currentTime` 与听感之间会系统性错位，这是管线无法预知的，只能交给最终用户。

---

## 10. 阶段 G：内联打包与加载

### 10.1 音频必须是 Blob，不能是 data URI

```js
audioB64 = document.getElementById('audio-b64').textContent.replace(/\s+/g, '');
try {
  const bin = atob(audioB64);
  const u8 = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
  audio.src = URL.createObjectURL(new Blob([u8], { type: 'audio/mp4' }));
  srcStage = 1;                                   // blob: random access, instant seek
} catch (e) {
  audio.src = 'data:audio/mp4;base64,' + audioB64;
  srcStage = 2;                                   // data URI: re-parsed on every seek
}
```

**为什么**：`data:` URI 每次 seek 都要重新解析整段 base64，拖动进度条会卡死；
`blob:` URL 支持随机访问，seek 即时。这个差异在 2MB 以上的音频上非常显著。

同时准备**降级链**：`blob → data URI → 外部同名文件`，并在 `error` 事件里逐级下退
（`letMeGo.html:3543-3547`）。外部文件兜底能让"用户直接改音频调试"变得可行。

### 10.2 打包脚本要做的三件事

1. 读 `assets/audio.m4a` → base64 → 注入 `<script id="audio-b64" type="text/plain">`；
   **注意 HTML 转义**：base64 字符集是 `A-Za-z0-9+/=`，不含 `<>&`，安全。
2. 读 `data/env.json` → 注入 `<script id="env-json" type="application/json">`；
   JSON 里的 `<` 必须转义，稳妥做法是直接 `json.dumps` 且断言无 `<`。
3. 按依赖顺序拼接所有 JS 片段到单个 `<script>`。顺序：
   math → color → canvas → lyrics → layers → scenes → main。

打包后必须跑一次 `__selftest()`（§11）。

---

## 11. 阶段 H：离线导出视频

### 11.1 导出模式

用 URL 参数切换，与交互播放共用同一份 `paint()`：

```js
const EXPORT_MODE = /[?&]export=1/.test(location.search);
const EXPORT_W = 1920, EXPORT_H = 1080;

function resize() {
  DPR = EXPORT_MODE ? 1 : Math.min(window.devicePixelRatio || 1, 2);   // fixed DPR
  W   = EXPORT_MODE ? EXPORT_W : window.innerWidth;
  H   = EXPORT_MODE ? EXPORT_H : window.innerHeight;
  // ...
  if (EXPORT_MODE) Q = 1;      // never let adaptive quality degrade the export
}

if (EXPORT_MODE) {
  document.body.classList.add('export');       // CSS hides all DOM UI
  window.__EXPORT = {
    W: EXPORT_W, H: EXPORT_H, FPS: 60, DURATION: DURATION,
    renderAt: t => { paint(t); return true; },
    quality: () => Q,
  };
} else {
  requestAnimationFrame(loop);                 // interactive path only
}
```

三个要点：

- **导出模式不启动 `requestAnimationFrame`**。必须的——否则自动播放的渲染循环
  会和抓帧驱动争抢 CPU，帧时间抖动，导出速度掉一半以上。
- **`Q` 强制为 1**。自适应降质逻辑在导出时必须禁用，否则前半段满配、
  后半段降质，成片会看到画质跳变。
- **DOM UI 用 CSS 隐藏**（`body.export #ui, #hud, #veil { display: none !important }`）。
  Canvas 上的效果（暗角、颗粒、边角标记）是 `paint()` 画的，导出时必须保留。

### 11.2 逐帧抓取脚本（Node + Puppeteer）

```js
// tools/export.js -- dump a PNG sequence by driving __EXPORT.renderAt(t)
// Usage: node tools/export.js <html-path> <outdir> [fps]
const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const [, , htmlPath, outDir, fpsArg] = process.argv;
const FPS = Number(fpsArg || 60);

(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await puppeteer.launch({
    args: ['--allow-file-access-from-files', '--force-device-scale-factor=1',
           '--disable-lcd-text', '--font-render-hinting=none'],   // deterministic text
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });

  const url = 'file://' + path.resolve(htmlPath) + '?export=1';
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForFunction('window.__EXPORT && window.__EXPORT.DURATION > 0');

  const { DURATION, W, H } = await page.evaluate(() => ({
    DURATION: window.__EXPORT.DURATION,
    W: window.__EXPORT.W, H: window.__EXPORT.H,
  }));

  const total = Math.ceil(DURATION * FPS);
  for (let i = 0; i < total; i++) {
    const t = i / FPS;
    const b64 = await page.evaluate(tt => {
      window.__EXPORT.renderAt(tt);
      return document.getElementById('stage').toDataURL('image/png').split(',')[1];
    }, t);
    const name = 'frame_' + String(i).padStart(6, '0') + '.png';
    fs.writeFileSync(path.join(outDir, name), Buffer.from(b64, 'base64'));
    if (i % 120 === 0) process.stdout.write(`\r${i}/${total} (${t.toFixed(1)}s)`);
  }
  const q = await page.evaluate(() => window.__EXPORT.quality());
  console.log('\nquality at exit =', q, '(must be 1)');
  await browser.close();
})();
```

抓帧方式的取舍：

| 方式 | 优点 | 缺点 |
|---|---|---|
| `canvas.toDataURL('image/png')` | 拿到的是 canvas 原始像素，无浏览器 UI 污染；与交互播放严格一致 | PNG 编码较慢 |
| `page.screenshot()` | 借用 Chrome 截图管线，略快 | 包含页面其他层，需确保 DOM UI 已隐藏 |
| `canvas.convertToBlob()` / `captureStream` | 更快（不经 base64） | 需要 Transferable stream，脚本复杂度上升 |

2 分钟片在 1920×1080@60 下约 7500 帧。这个量级下 PNG 编码是主要瓶颈，
若嫌慢可改成 JPEG（`toDataURL('image/jpeg', 0.95)`），画质损失在最终 H.264 下不可见。

### 11.3 合成

```bash
ffmpeg -y -framerate 60 -i frames/frame_%06d.png \
       -i assets/audio.m4a \
       -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p \
       -c:a aac -b:a 192k -movflags +faststart -shortest \
       out/letMeGo.mp4
```

- `-crf 16` 偏高质，因为源是合成图（有大量渐变与颗粒），低 CRF 才不出现色带。
- 颗粒效果会显著拉高码率，这是预期内的；要压体积就在场景里把 `grain` 的 alpha 调低。
- `-movflags +faststart` 便于网页直接播放。

---

## 12. 阶段 I：自检与验收

把自检做成页面内的一个函数（`window.__selftest()`），任何改动后手动跑一次，
也可以接进 CI（用 Puppeteer 打开页面调它，读 `window.__report`）。

### 12.1 自检项规格

| # | 检查项 | 判定 |
|---|---|---|
| 1 | **时间轴无缝** | 遍历 SEGS，`SEGS[i].t0 <= cursor`；且 `abs(cursor - DURATION) < 0.05` |
| 2 | **每句有场景** | 遍历 SEGS，`S[seg.sc]` 必须存在，列出所有缺失 |
| 3 | **覆盖统计** | `coverage = 已覆盖时长 / DURATION`，应 = 1.0 |
| 4 | **跳转一致性** | 取约 30 个时间点（含边界：`0`、`DURATION-0.05`、每段切换点 ±1ms、以及 hash 随机点），同一 `t` 渲染两次，对中心区域做**像素级比对**，必须完全相同 |
| 5 | **资源就绪** | `audio.duration` 有限且 > 0 |
| 6 | **无未捕获错误** | 收集 `window.onerror` / `unhandledrejection`，数组必须为空 |
| 7 | **无场景异常** | 收集 `paint.errs`，必须为空 |
| 8 | **节拍网格自洽** | `abs(BEAT - 60/BPM) < 1e-6` |

第 4 项是**整个管线最强的正确性护栏**：它把"画面是 `t` 的纯函数"这条不变量
变成了可自动验证的断言。任何时候不小心引入了帧间状态或 `Math.random()`，
它都会立刻报出具体时间点。

### 12.2 性能自适应

交互播放时按实测帧率自动降质（导出模式禁用）。降质对象要选**最贵的**，
并且是分级、可逆的：

```js
let Q = 1;                     // quality factor, 1 = full
// glow(c, b, a) 内部把 blur 半径乘 Q；grain 在 Q < 0.9 时整体跳过
if (state.fps < 46) lowN++;  else if (state.fps > 57) highN++;  else lowN = highN = 0;
if (lowN >= 3 && Q > 0.45) Q = 0.45;
else if (lowN >= 6 && Q > 0.32) Q = 0.30;
if (highN >= 6 && Q < 1) Q = 1;
```

三个细节：
- `shadowBlur` 是 Canvas 2D 最贵的操作，优先降它；
- 降质要有**迟滞**（3 次低于阈值才降、6 次高于才升），否则会在阈值附近反复横跳；
- 降质要有用户可见提示（toast），不然会被当成 bug 报。

---

## 13. 复用检查清单

新建一部片时按顺序过一遍：

**数据**
- [ ] `DURATION` / `env.dur` / 音频真实时长 三处一致（毫秒级）
- [ ] `env.beat == 60/env.bpm`（精度 1e-6）
- [ ] 包络长度 `>= ceil(dur*fps)+1`
- [ ] RAW 每句的 `scene` 名在 `S` 表里存在
- [ ] 所有 > 1.5s 的无人声区间都建了 `[间奏]` 段并配了场景
- [ ] 末段 `end` 用哨兵值，由编译期兜底到 `DURATION`
- [ ] MOODS 首尾颜色呼应，关键点对齐段落边界

**代码**
- [ ] 全片无 `Math.random()`（构建期静态资源除外）
- [ ] 全片无帧间状态（无 `x += v` 形式）
- [ ] 所有场景用 `save()/restore()` 包住变换
- [ ] 所有颜色取自 `st.mood`，无写死色值
- [ ] 每个场景的歌词都走 `LY()`，无自绘歌词
- [ ] 相机抖动/噪声用了不同盐值，避免全屏同步抖动

**验收**
- [ ] `__selftest()` 全绿（8 项）
- [ ] 导出模式 `quality()` 退出时为 1
- [ ] 拖动进度条任意跳转，画面与音频始终对应
- [ ] 蓝牙耳机下用 `offset` 能校准到同步
- [ ] 低端设备（或 Chrome 降速 4x）下自动降质生效且不闪

**产物**
- [ ] 单 HTML 无外部依赖，`file://` 直接双击可播
- [ ] 有 `blob → data URI → 外部文件` 三级降级

---

## 14. 反模式

以下做法会破坏不变量，**不要**在复用中引入：

| 反模式 | 后果 |
|---|---|
| 用 `performance.now()` 或帧计数当时间源 | 暂停/seek 后画面与音频彻底脱钩，无法修复 |
| 场景里用 `Math.random()` | seek 后画面跳变，自检第 4 项报错 |
| 场景里做 `x += v` 累积 | 同上；且暂停再播放会得到不同画面 |
| 运行时实时 FFT | 低端设备掉帧、seek 不可复现、包络质量差 |
| 让每个场景自己画歌词/选颜色 | 风格散、色调跳变、改一处要改 40 处 |
| 直接把 `[start,end]` 当渲染窗口用 | 气口/间奏/尾奏出现空洞帧，自检第 1 项报错 |
| 用 `data:` URI 播放大音频 | 每次 seek 重新解析，进度条拖动卡死 |
| 导出时不关 `requestAnimationFrame` | 抓帧与自播放抢 CPU，导出速度腰斩 |
| 导出时允许自适应降质 | 成片前段满配后段降质，画质跳变 |
| 把硬编码时间点当成对齐真值 | 改一句歌词要满世界找时间常量；编排点必须可追溯到 RAW |
| 用 `60/bpm` 之外的方式独立算 BEAT | 节拍网格与自检不一致，踩点累积漂移 |

---

## 15. 参考实现索引

`absent/otherprojects/letMeGo.html` 中与本管线各阶段对应的位置：

| 内容 | 位置 |
|---|---|
| 音频 base64 内联 | `177` |
| env-json 包络内联 | `178` |
| 数学/色彩工具 | `189-217` |
| RAW 歌词分镜表 | `221-261` |
| SEGS 编译与窗口归一 | `265-279` |
| 包络采样 `ef` / 节拍 `bi` `hit` `kill` | `282-293` |
| MOODS 色带与插值 | `296-333` |
| resize / `U` / DPR / 导出画布 | `340-354` |
| 确定性噪声颗粒 | `357-386` |
| 入场动画模式表 `inState` | `489-507` |
| `wordSched` 词级调度 | `476-487` |
| `LY` 通用歌词渲染器 | `512-620` |
| 全局母题层（光鲸/星尘/波纹/网格/光条） | `726-1030` |
| `S` 场景注册表（40 个） | `1036-3518` |
| 音频加载与三级降级 | `3523-3549` |
| `segIndexAt` / `buildState` | `3556-3576` |
| `cameraTransform` 相机 | `3579-3598` |
| `paint()` 绘制顺序 | `3601-3672` |
| 主循环与性能自适应 | `3674-3706` |
| `__selftest` 自检 | `3893-3945` |
| 导出模式 | `3972-3984` |

---

## 16. 移植说明

本文描述的是**方法**，不是某个技术栈。若换实现载体，核心不变量（§2.2）与
四层对齐模型（§9）不变，只有 API 名称变：

| 本管线 | Java / Processing | WebGL / 着色器 | Unity / Godot |
|---|---|---|---|
| Canvas 2D 立即模式 | `PApplet` 绘制调用 | 全屏 quad + fragment shader | `_Draw()` / `_process()` |
| `paint(t)` 纯函数 | `draw()` 内由 `t` 算全部状态 | `uTime` uniform，无 CPU 状态 | 由 `t` 驱动节点属性 |
| `audio.currentTime` | `AudioPlayer.position()` | 同左 | `AudioStreamPlayer.get_playback_position()` |
| `env-json` 查表 | 同左（`byte[]` + 索引） | 打包成 1D 纹理，shader 内采样 | 打包成 `Texture1D` |
| `hash(floor(t*fps))` | 同左 | 内置 `hash()` / `noise()` | 同左 |

移植时最容易丢失的是**不变量 2**：GPU 与引擎天然鼓励"状态累积"写法
（粒子上限、缓动插值、物理）。一旦引入 CPU 侧帧间状态，seek 一致性就没了。
若确实需要粒子系统，正确做法是**由 `t` 反推粒子状态**（用确定性哈希生成
粒子在第 k 帧的解析位置），而不是逐帧积分。
