# Swing BoxLayout 交叉轴对齐机制剖析：设计初衷、实测陷阱与现代 UI 系统对比

> 起因：flora-sanctum 设置页右栏的「保存」按钮始终无法占满整行，且左边缘与其他行不齐。
> 本文记录根因、从 JDK 26 源码反推的设计意图，以及现代 UI 系统的对应解法。
> 文中源码均引自本机 `openjdk 26.0.1` 的 `lib/src.zip`。

## 0. TL;DR

- BoxLayout 在**交叉轴**上用的不是「贴左 + 按最大宽度拉伸」，而是一套**基线式（baseline）分配**算法。
- 该算法会把**所有子组件的 `alignmentX` 聚合成一个总对齐值**，再据此裁剪每个子件。
- 于是 `setAlignmentX(LEFT_ALIGNMENT)`（0.0）的组件**反而会被右推、占不满行**；只有 `alignment` ≥ 总对齐值且 `maximum` 足够大的组件才能占满。
- 常见触发条件：面板里混入了默认 `alignmentX=0.5` 的组件（`JTextField` 就是），把总对齐值带偏。
- 现代 UI 系统（Flexbox / Flutter / SwiftUI / Compose）统一采用**容器级对齐 + 逐项可选覆盖**，并把「拉伸」与「对齐」拆成正交的两个属性，从根上消除了这类耦合。

## 1. 现象与复现

设置右栏 `settingsEditPanel` 为 `BoxLayout.Y_AXIS`，带 `EmptyBorder(8,10,8,10)`。
「保存」按钮按惯用写法配置：

```java
b.setHorizontalAlignment(SwingConstants.LEFT);
b.setAlignmentX(Component.LEFT_ALIGNMENT);   // 0.0f
b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
```

在一个真实窗口（面板宽 396，扣除左右各 10 内边距，可用区 `x=10, width=376`）中实测各子件边界：

| 子件 | 配置 | 修复前 bounds | 修复后 bounds |
|---|---|---|---|
| `JLabel` 标题 | 默认 `alignmentX=0.0` | `x=32, w=78` | `x=10, w=78` |
| `JTextField` 控件 | 默认 `alignmentX=0.5`，`max=MAX` | `x=10, w=376` | `x=10, w=376` |
| `JButton`「保存」 | `alignmentX=0.0`，`max=MAX` | `x=32, w=354` | `x=10, w=376` |

两个反直觉之处：

1. 按钮设了 `maximum.width = Integer.MAX_VALUE`，却只拿到 354 而非 376。
2. `alignmentX=0.0`（左对齐）的按钮与标签，左边缘是 `x=32`；而 `alignmentX=0.5` 的输入框反而贴到了最左 `x=10`。

## 2. 机制剖析

### 2.1 SizeRequirements：三段尺寸 + 一个对齐值

`javax.swing.SizeRequirements` 用四个字段描述一个组件在某个轴上的诉求：

- `minimum` / `preferred` / `maximum`：三段尺寸约束
- `alignment`：X 或 Y 方向的对齐值（0.0 ~ 1.0）

类注释明确给出两种布局算法（`SizeRequirements.java:36-49`）：

- **tiled**：首尾相接平铺，用于**主轴**
- **aligned**：按各组件的 X/Y 对齐值对齐，用于**交叉轴**

### 2.2 BoxLayout 的双轴分工

`BoxLayout.java:503-507`：

```java
// LINE_AXIS（横向）
xTotal = SizeRequirements.getTiledSizeRequirements(xChildren);
yTotal = SizeRequirements.getAlignedSizeRequirements(yChildren);
// PAGE_AXIS / Y_AXIS（纵向）
xTotal = SizeRequirements.getAlignedSizeRequirements(xChildren);
yTotal = SizeRequirements.getTiledSizeRequirements(yChildren);
```

即：**纵向 Box 的主轴（Y）平铺、交叉轴（X）对齐**。

- 主轴 `calculateTiledPositions`：富余空间按各子件 `maximum` 分发。
  这解释了另一个常见现象——未限制 `maximum.height` 的 `JTextField` 会被纵向拉伸变形（本项目图标名称输入框"变高"即此因）。
- 交叉轴 `calculateAlignedPositions`（`BoxLayout.java:440`）：即本文主角。

### 2.3 calculateAlignedPositions：基线式分配

`SizeRequirements.java:457-477`（JDK 26）：

```java
float totalAlignment = normal ? total.alignment : 1.0f - total.alignment;
int totalAscent  = (int)(allocated * totalAlignment);
int totalDescent = allocated - totalAscent;
for (int i = 0; i < children.length; i++) {
    SizeRequirements req = children[i];
    float alignment = normal ? req.alignment : 1.0f - req.alignment;
    int maxAscent  = (int)(req.maximum * alignment);
    int maxDescent = req.maximum - maxAscent;
    int ascent  = Math.min(totalAscent,  maxAscent);
    int descent = Math.min(totalDescent, maxDescent);

    offsets[i] = totalAscent - ascent;
    spans[i]   = (int) Math.min((long) ascent + (long) descent, Integer.MAX_VALUE);
}
```

读法：

- 每个子件的 **`maximum`** 被 `alignment` 切成 `maxAscent`（"基线上方"）与 `maxDescent`（"基线下方"）。
- 容器有一条**公共基线**，位置由总对齐值决定：`totalAscent`。
- 子件被**裁剪**到这条基线两侧：`ascent = min(totalAscent, maxAscent)`，`descent = min(totalDescent, maxDescent)`。
- `offset = totalAscent - ascent`：基线上方"够不到"多少，就右推多少。
- `span = ascent + descent`。

### 2.4 总对齐值如何聚合：用的是 minimum

`getAlignedSizeRequirements`（`SizeRequirements.java:206-236`）：

```java
ascent  = (int) (req.alignment * req.minimum);
descent = req.minimum - ascent;
totalAscent.minimum  = Math.max(ascent,  totalAscent.minimum);
totalDescent.minimum = Math.max(descent, totalDescent.minimum);
...
float alignment = (min > 0) ? (float) totalAscent.minimum / min : 0.0f;
```

注意三点：

1. 聚合用的是 **`minimum`**，不是 `preferred` 或 `maximum`。
2. 取的是各子件的 **max**，不是求和。
3. 结果是一个**全局共享**的 `total.alignment`。

### 2.5 反直觉结论的推导

设 `totalAscent = T`，可用宽 `A`。

**`alignment = 0.0` 的组件**：
`maxAscent = maximum × 0 = 0` → `ascent = 0` → `offset = T - 0 = T`，`span = 0 + min(maximum, A - T) = A - T`。
即：**左对齐的组件被右推 `T`，且只能拿到 `A - T` 的宽度**。
——这正是"保存按钮"的表现（本例 `T ≈ 22`：`x = 10 + 22 = 32`，`w = 376 - 22 = 354`）。

**`alignment = 0.5` 且 `maximum` 很大的组件**：
`maxAscent = maxDescent = maximum/2`，均远大于 `T` 与 `A - T`
→ `ascent = T`，`descent = A - T` → `offset = 0`，`span = A`。
即：**占满整行且贴左**。
——这解释了为什么默认 `0.5` 的 `JTextField` 表现"正常"，而显式设成左对齐的按钮反而出问题。

推论：要让交叉轴组件稳定占满，需要 `alignment` 不小于总对齐值，且 `maximum` 足够大。
当面板内所有子件 `alignment` 一致时（例如全为 0.0），`T = 0`，此时 `alignment=0.0` 的组件也能占满且贴左 —— 这就是本次修复的依据。

### 2.6 附带陷阱：getMaximumSize 先问 UI delegate

`JComponent.java:1768-1777`：

```java
public Dimension getMaximumSize() {
    if (isMaximumSizeSet()) {
        return super.getMaximumSize();
    }
    Dimension size = null;
    if (ui != null) {
        size = ui.getMaximumSize(this);   // 先问 L&F
    }
    return (size != null) ? size : super.getMaximumSize();
}
```

实测（macOS L&F）：未显式设 `maximumSize` 的 `JButton`，`getMaximumSize()` 返回 **(58, 25)**，等于其 `preferredSize`。
也就是说不要假定未设置的组件 `maximum` 是无穷大 —— 它取决于 L&F 的实现。

顺带记录各组件默认 `alignmentX`（本机实测）：

| 组件 | 默认 alignmentX |
|---|---|
| `JButton` | 0.0 |
| `JLabel` | 0.0 |
| `JTextField` | **0.5** |

默认值的这种不统一，是混合内容面板踩坑的直接来源。

## 3. 设计初衷

### 3.1 文本排版的基线隐喻

算法中 `ascent` / `descent` 的命名与结构，直接借自**西文排版**：把每个组件想象成一个字形，其最大尺寸被"基线"分成上下两部分，对齐即**让所有组件的基线落在同一条线上**。

在这个模型下，"我想让这一列组件都居中"或"都左对齐"就变成给每个组件标一个 0~1 的浮点数，无需任何约束对象。这是 1998 年（Swing 1.2，作者 Timothy Prinzing）在**没有约束式布局**的前提下，用极小的数据量（`minimum/preferred/maximum/alignment` 四个字段）表达复杂排布诉求的一次尝试。

### 3.2 与 GridBagLayout 的取舍

当时的另一极是 `GridBagLayout`：表达力强但需要一个 `GridBagConstraints` 对象、十几个字段，被普遍认为难用。
`BoxLayout` 的卖点是：**无约束对象、声明式、够用**。它面向的典型场景是工具栏、按钮条这类**同类组件**的堆叠 —— 同类组件 `alignmentX` 天然一致，基线模型工作良好，且能靠 `Box.createGlue()` / `createVerticalStrut()` / `RigidArea` 做弹性间隔。

### 3.3 副作用：局部属性被全局化

问题出在**异构混合内容**上：

- 每个子件的 `alignment` 既是"自己的对齐意图"，又参与**全局基线**的计算；
- 于是**改一个子件会影响所有兄弟**；
- 且"对齐"与"拉伸"被压缩进同一个浮点数，无法独立表达"我要贴左 **并且** 占满"。

这类设计在当时可接受（组件种类少、默认值统一），但放到今天混合了输入框、下拉、按钮、多行文本的复杂表单里，就成了反直觉行为的来源。

## 4. 现代 UI 系统如何处理

### 4.1 共同范式

现代声明式 UI 基本收敛到同一个范式：

1. **对齐是容器级属性**（一个枚举，作用于全部子件），个别子件可用 `align-self` / `Modifier.align` / `alignmentGuide` 之类**覆盖**；
2. **覆盖不影响兄弟**——不存在"聚合"；
3. **拉伸与对齐正交**：`stretch` 是一个独立取值，或由 `flex` / `weight` / `fillMaxWidth` / `Expanded` 单独表达；
4. 尺寸约束（`min/max-width`）独立存在，不参与对齐计算。

### 4.2 CSS Flexbox / Grid

- 交叉轴：`align-items`（容器级：`stretch` / `flex-start` / `center` / `baseline`），子件用 `align-self` 覆盖。
- 主轴增长：`flex-grow`（与对齐无关）。
- 尺寸钳制：`min-width` / `max-width`。
- 关键点：`align-items: flex-start` **不会**导致子件被右推——"贴左"与"占满"通过 `align-items` + `width:100%`（或 `flex:1`）两个独立属性表达。
- Grid 更进一步：显式轨道 + `justify-items` / `align-items`，位置完全由轨道决定。

### 4.3 Flutter Row / Column

- `crossAxisAlignment` 是**父级单一枚举**：`start` / `end` / `center` / `stretch` / `baseline`。
- 主轴弹性：`Flexible` / `Expanded`（`flex` 因子）。
- 没有"子件各自声明对齐再聚合"的机制；需要特例时包一层 `Align` widget。
- 文档明确提示 `CrossAxisAlignment.baseline` 需配合 `textBaseline` 使用 —— 把"基线对齐"降级为一个**显式可选项**，而非默认算法。

### 4.4 SwiftUI HStack / VStack

- `HStack(alignment: .top) { ... }`：容器级 `VerticalAlignment`。
- 逐项微调：`alignmentGuide(_:computeValue:)`，只影响自身。
- 拉伸：`frame(maxWidth: .infinity)` —— 与对齐**完全无关**的属性。
- 结果与 Flutter 一致：默认"居中或居中偏上"，要贴左+占满就同时设 `alignment` 与 `maxWidth:.infinity`，二者互不干扰。

### 4.5 Jetpack Compose Row / Column

- `Row(verticalAlignment = Alignment.CenterVertically)`：容器级。
- 逐项覆盖：`Modifier.align(Alignment.Top)`（仅在 `RowScope` 内可用）。
- 主轴权重：`Modifier.weight(1f)`。
- 填充：`Modifier.fillMaxWidth()`。
- 同样把"对齐"与"填充"拆成两个独立 Modifier。

### 4.6 Android View LinearLayout

- 交叉轴：`android:gravity`（容器）/ `android:layout_gravity`（逐项）。
- 主轴：`layout_weight`。
- 填充：`match_parent`。
- 与上述范式一致，只是表述为 gravity + weight 两级。

### 4.7 对比表

| 维度 | Swing BoxLayout | Flexbox | Flutter | SwiftUI | Compose |
|---|---|---|---|---|---|
| 交叉轴对齐作用域 | 逐子件，参与**全局聚合** | 容器级 + 逐项覆盖 | 父级单一枚举 | 容器参数 + guide 覆盖 | 容器参数 + Modifier 覆盖 |
| 改一个子件是否影响兄弟 | **是** | 否 | 否 | 否 | 否 |
| 拉伸的表达 | 与对齐共用一个 float（`maximum` 参与对齐计算） | `flex-grow` / `width` | `Expanded` / `Flexible` | `frame(maxWidth:)` | `weight` / `fillMaxWidth` |
| 尺寸约束是否参与对齐计算 | **是**（聚合用 minimum，裁剪用 maximum） | 否 | 否 | 否 | 否 |
| 基线对齐 | 默认算法 | `align-items: baseline` | 显式枚举值 | 显式 `alignmentGuide` | 显式 `FirstBaseline` |

## 5. 本项目中的规避实践

已采用（提交见 `sanctum-app: 设置右栏统一 alignmentX`）：在 `showSettingsSelection()` 渲染完成后、重绘前，把 `settingsEditPanel` 的所有直接子件统一设为左对齐：

```java
for (Component c : settingsEditPanel.getComponents()) {
    if (c instanceof JComponent jc) {
        jc.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
```

原理：所有子件 `alignment` 一致 → `total.alignment = 0` → `totalAscent = 0` → 全部 `offset = 0` 贴左，且 `maximum` 足够大的子件 `span = allocated` 真正占满。

可选的其它方案（按侵入性递增）：

1. **对齐值取 0.5 + 大 `maximum`**：能稳定占满且贴左，但与"左对齐"的语义相反，可读性差，且依赖"总对齐值 ≤ 0.5"。
2. **每个子件包一层全宽容器**：`JPanel(new BorderLayout())` 设 `maximum.width = MAX`、`alignmentX = LEFT`，内部组件放 `CENTER`。隔离了子件自身默认值的影响，但要改所有添加点。
3. **换用 GridBagLayout**：表达力最强、行为可预测（`fill=HORIZONTAL, weightx=1` 即占满且贴左），但需为每个添加点提供 `GridBagConstraints`，改动面最大。

日常编码建议：

- 往 `BoxLayout` 面板里加组件时，**显式设置 `alignmentX`**，不要依赖默认值（默认值在 `JTextField` 上是 0.5，在 `JButton`/`JLabel` 上是 0.0）。
- 需要"占满"时，同时满足：`alignmentX` 与兄弟一致、`maximum` 足够大。
- 需要限制尺寸时，**必须设 `maximumSize`**，否则富余空间会被该组件吃掉（主轴平铺所致）。

## 6. 参考源码位置

基于本机 `openjdk 26.0.1`（`/opt/homebrew/Cellar/openjdk/26.0.1`），`lib/src.zip`：

- `java.desktop/javax/swing/SizeRequirements.java`
  - 类注释（两种布局：tiled / aligned）：36-49 行
  - `getAlignedSizeRequirements`（总对齐值聚合）：206-236 行
  - `calculateAlignedPositions`（交叉轴基线分配）：457-477 行
- `java.desktop/javax/swing/BoxLayout.java`
  - 双轴 SizeRequirements 选取：503-507 行
  - 交叉轴 `calculateAlignedPositions` 调用：440 行
- `java.desktop/javax/swing/JComponent.java`
  - `getMaximumSize` 先询问 UI delegate：1768-1777 行
