# config 系统：RemoteConfigSource 设计决策

## 背景

flora-root 的 config 系统重构进行中（见 todo.md 中「config 系统设计」：链式调用构造、动态加载热替换、远程加载）。
本次为「远程加载」落地：新增 `RemoteConfigSource`，组合 `RemoteKVSource`（`com.flora.common`）与 `ConfigSchema` 提供配置信息。

## 决策

| 日期 | 决策/问题 | 可选方案 | 选择与理由 | 影响 |
|------|----------|----------|-----------|------|
| 2026-08-08 | ConfigSchema 描述内容 | 仅 key 集合 / key+类型 / key+类型+默认值 | **仅 key 集合**（用户确认）。RemoteKVSource 返回 String，暂不做类型转换，保持最简；后续可按需扩展。 | ConfigSchema 值均为 String 或 null |
| 2026-08-08 | schema key 与远端键的映射 | 点号路径直接对应 / 扁平 key+前缀映射 | **点号路径直接对应**（用户确认）：schema key 即远端扁平键，加载后展开为嵌套 Map，Config.get("db.host") 可访问。 | 远端键约定为点号路径 |
| 2026-08-08 | schema 声明 key 在远端缺失时 | 填默认值 / 抛异常 / 一律填 null | **一律填 null**（用户确认）：缺失 key 仍出现在结果中，值为 null，与默认值无关。 | 结果 Map 可能含 null 值 |
| 2026-08-08 | ConfigSchema 形式与位置 | 接口+静态工厂（interfaces 包）/ final class（config 顶层） | **final class，放 config 顶层包**。对齐 JsonSchema/ConfigUtil/ConfigException 风格；interfaces 包只放技术接口。 | ConfigSchema 位于 `com.flora.runtime.config` |
| 2026-08-08 | ConfigSchema 构造期校验 | 不校验 / 校验并定义覆盖规则 / 校验并拒绝 | **校验并拒绝**：拒绝 null/空串/空路径段/前缀冲突（"a.b" 与 "a.b.c"）。否则嵌套展开会静默丢弃已声明 key 的值，违背「声明即出现」语义。 | 非法 key 抛 ConfigException |
| 2026-08-08 | 共享 Config 包装实现 | 提取公共 MapConfig 复用 / 各 source 内嵌私有实现 | **各 source 内嵌私有实现**（用户确认「不要多余的提取公共类」）。FileConfigSource 与 RemoteConfigSource 各自内嵌 MapConfig，避免过早抽象；后续如出现第三个来源再评估提取。 | 两处约 75 行相似逻辑，暂可接受 |
| 2026-08-08 | RemoteConfigSource.location() 来源标识 | 引入可选接口 RemoteKVIdentity / 用 kv 类名+keys | **已移除**（用户 2026-08-08 主动从 ConfigSource 接口删除 location()）。ConfigSource 现仅保留 load()/describe()；实现类与测试中的 location() 同步清理。 | ConfigSource 不再承载去重/循环检测位置标识 |
| 2026-08-08 | 点号键展开算法位置 | 共享工具 / MapConfig 静态方法 / RemoteConfigSource 私有 | **RemoteConfigSource 私有静态方法**。仅此一处使用，无需抽象。 | expand(flat) 内嵌于 source |
| 2026-08-08 | key 顺序稳定性 | HashSet（无序）/ LinkedHashSet（插入序） | **LinkedHashSet，插入序稳定**，使 keys()/toMapTree() 输出确定，便于调试。 | keys() 不可变视图 |

## 文件清单

- 新增 `flora-root/src/main/java/com/flora/runtime/config/ConfigSchema.java`
- 新增 `flora-root/src/main/java/com/flora/runtime/config/source/RemoteConfigSource.java`
- 新增测试 `ConfigSchemaTest`、`RemoteConfigSourceTest`
- 不改 `module-info.java`（config/impl/source/interfaces/common 均已导出）
