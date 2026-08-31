# Decision 20260831-01: KDBX / 多格式读取模块化拆分

## 决策

将 KeePass/KeePassXC 的 KDBX 解密与读取从 `flora-sanctum-core` 拆分为独立子模块
`flora-sanctum-kdbx`；后续以类似思路新建 `flora-sanctum-vault-formats` 提供 1Password
`.1pux`、`.opvault`、Bitwarden `.json`、KeePass1 `.kdb` 的**只读**兼容。

具体约束（与用户确认）：

1. **crypto 下沉到 `flora-root`**：把自研的 Argon2 / BLAKE2b / Salsa20 以及 KDBX 用的
   `Argon2Kdf` 封装迁到 `com.flora.root.crypto` 并导出；新模块与 core 均只依赖
   `flora-root`，不互相依赖、不引入 BouncyCastle。
2. **core 不直接碰日志/存储**：延续既有约定——core 是复用库，读取模块也不许直接创建
   Logger 或感知 XDG 等存储细节。诊断通过**结构化异常**携带（阶段/版本/cipher/KDF uuid），
   **不引入日志、不引入诊断回调**。
3. **职责切分**：新模块只负责「解密 + 读取」，输出通用模型 `KdbxDocument`；把读取结果
   映射进 Sanctum 模型的工作留在 core（`KdbxMapper`），app 负责导入编排。
4. **通用化校验点**：`flora-sanctum-kdbx` 的 `module-info` 不得 `requires` core、不得
   引用任何 Sanctum 类型，仅依赖 `java.xml` 与 `com.flora.root`。

## 为何

- core 作为可复用库，若把「外部保险库格式的加解密」与 Sanctum 专属逻辑混在一起，会
  拖慢复用、也违背 core 不应知道日志/存储位置的约定。
- 多格式兼容（1Password/Bitwarden/KeePass1）天然是同一类「只读解密读取」职责，独立成
  库可统一复用 `flora-root.crypto` 与结构化异常风格。

## 如何应用

- 新增外部保险库格式支持时，优先放进 `flora-sanctum-vault-formats`（或类似独立模块），
  不要往 core 塞格式专属的加解密代码。
- 任何读取失败统一抛结构化异常（携带阶段与非敏感标识），禁止在库代码中打日志或吞异常。
- 需要新密码学原语时，优先下沉到 `flora-root.crypto` 以保持「零第三方依赖 + 通用复用」。
