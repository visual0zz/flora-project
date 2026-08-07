# decision20260807-01-osmetes: 检查项配置键前缀由引擎剥离

## 决策

osmetes 检查引擎向各检查项下发配置时，按检查项的 `FileCheck#name()` 作为命名空间前缀，
从通用配置表中筛出属于该检查项、且**已剥离前缀**的子集，再调用 `configure(Map)` 下发。
检查实现类只认自己的裸子键（如 `allowed`、`minLength`），不感知顶层前缀，也看不到其它
检查项的配置。

用户侧配置键保持不变，仍是带前缀的完整形式（`encoding.allowed`、`secret.minLength`、
`secret.minClasses`、`secret.minEntropy`、`secret.minEntropyDensity`）——剥离前缀是引擎
内部行为，对用户透明。

`configure(Map<String, String>)` 的方法签名未变，保持 SPI 二进制兼容；仅语义从"整表广播"
变为"已剥离前缀的子集"。

## 理由

- 原设计中 `EncodingCheck` 直接持有 `encoding.allowed`、`SecretCheck` 直接读取
  `secret.minLength` 等全局键，每个检查实现类被迫知道顶层命名空间，且隐含了解其它检查项
  的存在。这违反单一职责：实现类只应关心自己的配置子键。
- 顶层前缀是"用户侧命名空间"，属于使用方（引擎）的职责，应在下发前完成路由与剥离，
  使检查实现类彼此解耦、可独立演进。

## 影响

- SPI 契约语义变更：既有第三方检查项若依赖"收到整表、自行取带前缀键"的旧行为，需改为
  读取裸子键。当前仓库无第三方 SPI 检查项（`flora-garden` 为占位模块），影响面为零。
- 涉及文件：`Osmetes`（新增 `configFor` 分发）、`FileCheck`（契约文档）、`EncodingCheck`、
  `SecretCheck`（改为裸键）、`OsmetesMojo`（用户侧文档表述）。
