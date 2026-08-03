# Decision: osmetes 检查项改名与通用配置通道

日期：2026-08-03
模块：flora-osmetes / flora-osmetes-plugin

## 背景

1. `TrailingWhitespaceCheck` 的检查名 `trailing-whitespace` 过长且带连字符，与
   `tab`/`secret` 等短名风格不一致，用户要求改名。
2. `EncodingCheck` 仅校验 UTF-8，用户要求支持可配置编码（如允许 GBK），并明确要求
   "每个检查项有自己的配置项这件事要做成通用路径，不要特殊处理"——即不能给某个
   检查写专属的参数与分支逻辑。

## 决策

1. **改名 `trailing-whitespace` → `whitetail`。**
   检查类 `TrailingWhitespaceCheck` 重命名为 `WhitetailCheck`，`name()` 返回 `"whitetail"`，
   注释同步更新。与 `tab`/`secret` 等短 token 命名风格统一，无连字符。

2. **提供一条通用的检查项配置通道，而非逐检查特殊处理。**
   - 在 `FileCheck` 接口新增默认方法 `configure(Map<String, String> properties)`
     （默认空实现，SPI 既有实现无需改动即可接入，向后兼容）。
   - 引擎在扫描开始前，把同一份通用配置表统一下发给每个检查项
     （`Osmetes.run(..., Map<String,String> checkConfig)` 内对每项调用 `configure`），
     **引擎不解析、不关心键的含义**。
   - 各检查项自行约定键名并读取：`EncodingCheck` 读取 `encoding.allowed`
     （默认 `UTF-8`，可配置为 `UTF-8;GBK` 等，分隔符 `,;|&` 取并集）。
   - 编码校验语义改为：文件能被清单中**任一**允许编码无错完整解码即通过；
     采用首个成功解码的编码文本继续做 C1 控制符扫描；全部失败则报错并列出允许清单。
   - Mojo 新增通用 `checkConfig`（`Map<String,String>`）参数原样下发，不特判任何键。
     与既有的 `ignorePatterns`（范围）、`disabledChecks`（关检查项）并列，三者职责分离：
     范围 / 关项 / 检查项行为配置。

## 影响

- `FileCheck` 接口新增默认方法，所有 SPI 检查项零改动兼容。
- `EncodingCheck` 默认行为不变（仍仅 UTF-8），配置后才放宽；未知编码名被忽略并回退 UTF-8。
- `Osmetes.run` 增加接受 `checkConfig` 的重载，其余重载委派（保持 2~4 参数旧签名可用）。
- Mojo `checkConfig` 示例：`<checkConfig><encoding.allowed>UTF-8;GBK</encoding.allowed></checkConfig>`。
- 新增 `EncodingCheckTest`（7 例）覆盖默认/可配置编码、GBK、C1 控制符、未知编码回退；
  修正 `OsmetesDisabledChecksTest` 的分隔符断言。全模块 64 测试通过。
