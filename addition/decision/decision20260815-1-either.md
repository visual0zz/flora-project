# 决策：com.flora.root.container.Variant 的设计

- 日期：2026-08-15
- 模块：flora-root
- 主题：N 元 Sum Type（任意多个类型任取其一）容器实现

## 背景

需要一个值容器：**任意多个类型任取其一**（N 元 sum type，对应 C++ `std::variant` / Union Type）。最初尝试了二元 `Either`（sealed interface + Left/Right record），不符合"任意多个类型"与"容器"的诉求，废弃改为 `Variant`。

## 决策

### 1. 形态：final class 容器（非接口）

`Variant` 是 `final` 类，字段 `Class<?>[] types`（替代类型表）+ `int index` + `Object value`。

- 理由：Java 泛型无法表达变长类型参数列表，接口方案只能覆盖固定数量的分支；单容器 + 运行时类型表才能表达"任意多个类型"。
- `@ReadOnly` 标注，不可变：`set`/`clear` 返回新实例，原实例不受影响。

### 2. 语义对应 std::variant

| std::variant | Variant |
|---|---|
| 构造 | `of(Class<?>... types)`（无值）/ `of(value, types)`（带值） |
| 赋值 | `set(Object)` 自动匹配 / `set(int, Object)` / `set(Class, Object)` |
| `valueless_by_exception()` | `isValueless()`（索引 -1） |
| `index()` | `index()`（无值为 -1） |
| `holds_alternative<T>()` | `holds(Class)` / `holdsIndex(int)` |
| `get<T>()` / `get_if<T>()` | `getValue(Class)` / `get(Class)`（Optional）/ `getOrElse` |
| `visit(visitor)` | `visit(Function)` 单访问器 / `visit(Function...)` 按索引分派 |

### 3. 类型安全策略

编译期无法枚举 N 个类型参数，退而保证运行时强约束：声明时校验类型表非空、非 null、不重复；`set` 时校验值类型 `isInstance` 于声明类型，不符抛 `IllegalArgumentException`。自动匹配按类型表声明顺序取第一个命中者。替代类型用包装类（`Integer.class` 等）。

### 4. null 策略

值允许 null，但 null 无法自动匹配类型：`set(null)` 抛异常，须用 `set(Class, null)` 或 `set(int, null)` 显式指定。`get(Class)` 用 `Optional.ofNullable` 感知。

### 5. JDK 接口系统兼容

- 函数式参数全部使用 `java.util.function`（`Function`）；`visit` 即模式匹配入口。
- `Optional` 集成：`get`/`getOrElse` 返回/消费 `Optional`。
- `Stream` 集成：`stream()` 产出当前值单元素流（无值为空流）。
- 实现 `Serializable`，`equals`/`hashCode`/`toString` 基于类型表 + 索引 + 值。

## 影响

- 删除初版 `Either`（sealed interface）实现；`Variant` 位于 `com.flora.root.container` 顶层包（原定的 `container.either` 子包废弃，空目录），与既有 `container.tuple` 并列。
- `module-info.java` 导出 `com.flora.root.container`。
- 新增 `VariantTest`（18 用例，置于 `com.flora.root.container.either` 测试包）覆盖工厂校验、状态查询、set/clear、null 处理、读取、visit、stream、值语义，全部通过。
