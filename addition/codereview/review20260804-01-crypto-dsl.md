# 代码审查：crypto 包 DSL 抽象重构（commit 1d4986f）

审查范围：`flora-root` 模块 `com.flora.crypto.core` 下的 DSL 重构。
结论：**既有 32 个单元测试全部通过，重构未破坏已覆盖功能；但静态审查 + 临时验证发现 5 处缺陷，其中 2 处会导致真实崩溃或静默错误。**

## 验证方式

- 编译并运行 `CryptoAbstractionTest` / `CryptoRolesTest`：32 个用例全部通过。
- 额外用临时测试验证了以下边界行为，验证后已删除临时文件（工作树干净）。
- 关键类型关系确认：`ExtendedDigest extends Digest`；`BlockCipher` / `Mac` / `Digest` 均为独立的 `extends AlgorithmFamily`，互不相关。`JdkDigest implements ExtendedDigest`。

---

## Bug 1（高危）：组合算法裸名查询崩溃为 ArrayIndexOutOfBoundsException

**位置**：`CryptoProvider.java` 的 `resolveName` / `findEntry` + 各组合工厂。

组合算法通过 DSL 名字注册，工厂依赖参数，例如：

```java
register(Mac.class, "HMac", new Class[]{ExtendedDigest.class},
        args -> new HMac((ExtendedDigest) args[0]));
```

但 `resolveName` 在匹配到组合工厂时，**无论调用方是否带参**，都用同一个 `args` 数组调用工厂（`CryptoProvider.java:203` 与 `:207`）。当调用方以**裸名**（无参）查询时，`args` 是 `new Object[0]`（`CryptoProvider.java:189`），工厂访问 `args[0]` 即 `ArrayIndexOutOfBoundsException`。

实测：

- `CryptoProvider.mac("HMac")` → `java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`
- `CryptoProvider.blockCipher("HMac")` → 同上（经跨角色搜索命中 Mac 角色的 HMac 工厂，见 Bug 2）

期望行为：应清晰报错（如 `IllegalArgumentException: algorithm 'HMac' requires parameters` 或 `Unregistered algorithm`），而非抛出低层、令人困惑的越界异常。

---

## Bug 2（高危）：类型化查询跨角色搜索破坏类型安全，且与文档契约不符

**位置**：`CryptoProvider.java:206` `resolveName` 的 `findAcrossRoles(name)`。

类型化查询（如 `digest()`、`blockCipher()`、`mac()`）在 `hintRole` 未命中后，**跨所有角色搜索同名算法**，完全忽略返回类型是否兼容 `hintRole`。

- 类 Javadoc 声称（`:55-57`）："类型化查询优先在本角色查找，未命中则回退 JDK 适配器"。实际代码是"未命中则跨所有角色搜索"，与文档不符。
- 后果：若某名字在另一角色存在（尤其组合工厂），会用错误类型/错误参数构造，导致崩溃或 `ClassCastException`。示例：`blockCipher("HMac")` 在 Mac 角色命中 HMac 工厂并以空参数调用（归并到 Bug 1 的崩溃路径）。

类型化查询本应只在本 `role` 内 + JDK 回退，不该跨角色捡无关类型。

---

## Bug 3（中危）：类型化查询方法用 try-catch 吞掉所有异常，静默返回占位符

**位置**：`derivationFunction`（`CryptoProvider.java:331-337`）、`xof`、`kem`、`entropySource`、`asymmetricStreamCipher`、`hmacDrbg`。

这些方法以 `try { ... } catch (Exception e) { return <Placeholder/默认实现>; }` 吞掉**任何**异常。实测：

- `CryptoProvider.derivationFunction("PBKDF2")`（裸名、缺参）→ 内部 AIOOBE 被静默捕获 → 返回 `PlaceholderDerivationFunction@...`，无任何报错。

问题：拼写错误、缺参、类型不匹配等配置错误都会被静默替换成占位实现，调用方无法区分"真注册了算法"与"配置错误被兜底"，后续在错误形态上运行而非快速失败，极难排查。这与 `rejectsTransformationStrings` 等测试中"错误输入应抛异常"的设计意图相悖。

建议：占位回退只应覆盖"算法确实未注册"这一明确情况（抛 `IllegalArgumentException` 后再回退），而不应掩盖构造期/参数期的崩溃。

---

## Bug 4（中危）：DslParser 不校验括号配对，缺右括号被静默截断

**位置**：`DslParser.java:42-47` `findTopLevelParen` 与 `parse` 的截取逻辑。

`findTopLevelParen` 仅返回首个 `(` 位置，不做括号配对；`parse` 直接以 `expr.length()-1` 作为右边界（`DslParser.java:32`）。当表达式缺少右括号时，最后一个字符被静默截掉，得到错误结果而非清晰报错。

实测预期：`CryptoProvider.resolve("GCM(AES")` 应抛 `IllegalArgumentException`，但当前会被解析为 `GCM(AE)` 类错误结构（取决于名字是否被注册，可能进一步触发 Bug 1 的崩溃）。

建议：`parse` 应先定位与首个 `(` 配对的 `)`，若无匹配右括号则抛 `IllegalArgumentException("unbalanced parentheses")`。

---

## Bug 5（低危）：`paramTypes` 声明"运行时类型校验"但从未使用，文档误导

**位置**：`CryptoProvider.java:130-135`（`register` 的 Javadoc 与签名）、`:148-155`。

`register` 接收 `paramTypes` 并在 Javadoc 中写明"用于运行时类型校验"，但 `resolveArgs` / `resolveName` 从未用它校验实参类型。传入错误类型参数时，得到的是工厂内部强转的 `ClassCastException`，而非友好的参数类型错误。

建议：要么在 `resolveName` 调用工厂前按 `paramTypes` 校验 `args` 元素类型并抛清晰错误；要么删除 `paramTypes` 参数与对应文档，避免误导后续维护者。

---

## 修复方向（供参考，未实施）

1. 解析层区分"裸名查询"与"带参调用"：组合工厂必须带参；裸名命中组合工厂应视为错误（清晰 `IllegalArgumentException`）。
2. `findAcrossRoles` 仅在 `resolve(String)`（无 hintRole）场景下使用；类型化查询 `resolveByRole` 不应跨角色搜索不兼容类型。
3. 收窄占位回退的兜底范围：仅当"算法名确实未注册"时回退，构造/参数异常应原样抛出。
4. `DslParser.parse` 增加括号配对校验。
5. 落实或移除 `paramTypes` 的运行时校验。
