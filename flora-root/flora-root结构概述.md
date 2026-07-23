# flora-root 项目概述

**flora-root** 是零依赖工具库，包 `com.flora.root`（JPMS 模块），被所有其他 flora 模块使用。

## 核心包

- **`com.flora.fast`** — **代码生成**的原始类型专用 HashMap 实现（64 个 `*FastHashMap` 类，如 `Int2IntFastHashMap`、`Long2ObjectFastHashMap`），由 flora-ramet 从模板生成。
- **`com.flora.classfile`** — 完整的 Java `.class` 文件解析器和字节码重写工具集（`ClassModel`、`ConstantPool`、`Bytecode`）。
- **`com.flora.algebra`** — 数论工具（素数判定、素数计数）。
- **`com.flora.entropy`** — 哈希函数集合（MurmurHash3、SHA 系列、MD5、Koloboke mix）。
- **`com.flora.json`** — 符合 RFC 8259 的零依赖 JSON 解析器。
- **`com.flora.binary`** — 字节/原始类型转换工具。
- **`com.flora.crypto`** — AES-GCM 加密封装（`@WorkInProgress`）。
- **`com.flora.tag`** — 自定义注解（`@ReadOnly`、`@SlowFunction`、`@ThreadFragile`、`@WorkInProgress`）。
