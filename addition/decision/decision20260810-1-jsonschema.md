# 决策：json/jsonschema 包结构调整

**日期**：2026-08-10
**模块**：flora-root
**类型**：代码结构重构

## 背景

`com.flora.codec.json`（18 个文件）与 `com.flora.codec.jsonschema`（9 个文件）
混放公开 API 与内部实现类，包内散碎类过多，难以区分对外契约与实现细节。

## 决策

遵循 AGENTS.md 包划分规范（父包只保留公开 API，内部实现移入 `impl` 子包，
`impl` 子包不导出），将内部实现类下沉：

### com.flora.codec.json（保留 12 个公开 API 文件）
- 值模型：JsonValue / JsonString / JsonNull / JsonBool / JsonNumber / JsonArray / JsonObject
- 门面：JsonParser / JsonBuilder / JsonPath
- 注解：JsonIgnore；包文档：package-info

### com.flora.codec.json.impl（新增，不导出）
- JsonConversions（原生值↔JsonValue 桥接，改为 public 供父包跨包调用）
- JSONPath 引擎内部：JsonPathTokenizer / JsonPathParser / JsonPathEvaluator /
  Token / TokenType / Selector（含 7 个选择器 record）/ JsonPathTypes（过滤器 AST）
- 原 JsonPathToken.java 按"public 类型与文件同名"规范拆分为 Token.java / TokenType.java

### com.flora.codec.jsonschema（保留 4 个公开 API 文件）
- JsonSchema（门面）/ ValidationResult / ValidationError / JsonTypes（被 mock 子系统复用）

### com.flora.codec.jsonschema.impl（新增，不导出）
- 编译与求值引擎：SchemaRegistry / CompiledSchema / ValidationContext /
  EvaluationState / SchemaNumbers

### 保持不变
- validator 子包（11 个关键字校验器）、format 子包（FormatValidators）已按语义分包，不动
- mock/jsonschema 及其 impl 子包已符合规范，不动
- jsonl 仅 2 个类，无需再分

## 影响

- `module-info.java` 无需新增 exports（impl 子包本就不导出）
- 引用方（JsonSchema、validator 子包、mock/GeneratorCompiler）统一改 import 到 impl 包
- 新增 jsonschema 主包 package-info，说明公开 API 与内部实现的边界
- 全量 Maven 测试通过
