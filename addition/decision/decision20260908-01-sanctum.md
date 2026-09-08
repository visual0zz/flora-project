# 决策：节点 uuid 不引入查重重试，统一走混合熵生成入口

日期：2026-09-08
模块：flora-sanctum-core（crypto / model）

## 背景

讨论「新建节点生成 uuid 时是否应校验与已有 uuid 重复、重复则重试」时，确认两项结论：

1. **不增加查重机制**；
2. **uuid 生成统一收口到混合熵入口**（此前新建节点的 uuid 直接调用 `UUID.randomUUID()`，
   散落 7+ 处；而 wiki 02「熵混合」已声明"UUID 生成等统一经混合熵入口"，代码与设计不一致）。

## 决策

### 1. 不加「生成后查重 + 重试」

- UUID v4 有 122 位随机熵。按生日悖论，碰撞概率 ≈ n²/(2·2¹²²)：10⁵ 个对象约 10⁻²⁷，
  10⁹ 个约 10⁻¹⁹。密码库实际规模（十万块以内）下碰撞概率远低于磁盘损坏、内存翻转、
  自身 bug 的故障率，为此引入全库查重与重试是负收益。
- 查重存在竞态：git 同步/多端并发写入下，"读后生成"的校验无法保证两端不产生同一 uuid，
  提供不了强保证，只制造虚假安全感。
- 碰撞安全不取决于查重，而取决于随机源质量：源退化时"不重复但可预测"照样发生，
  查重救不了。

### 2. uuid 生成统一走混合熵入口（本次落地）

- 给 `crypto.impl.SecureRandomSource` 增加 `nextUuid()`：RFC 4122 version 4，
  16 字节经混合熵（主源 CSPRNG + HKDF 叠加进程内抖动）生成后设置 version/variant 位。
- 将 main 源码中全部 `UUID.randomUUID()` 新建节点调用替换为经 `random()` 拿到的
  `nextUuid()`，共 10 处：
  - `ObjectTree.createGroup` / `createEntry`（组/条目）
  - `EntryNode.writePresetField` / `writeCustomField`（字段块）
  - `IconTree.createIcon` / `SshKeyTree.createSshKey` / `RemoteTree.addRemote`
  - `LibraryConfig.setConfig`（config 节点）
  - `ExternalKeyService.createExternalKey`
  - `ManifestStore.write`（manifest 随机 uuid）
- 各调用点均经既有 `random()`（TreeContext.random / Vault.random）访问混合熵源，
  不新增旁路。main 源码已无残留 `UUID.randomUUID()`。

## 影响

- core crypto：`SecureRandomSource.nextUuid()` 新增。
- core model：上述 10 处 uuid 生成路径的行为变化（v4 语义不变，熵源增强为混合熵）。
- 测试：新增 `SecureRandomSourceTest`（version/variant、无碰撞、分片桶均匀）；
  全量 core 测试通过。
- 无格式变更：uuid 仍是 32-hex v4，旧库无需迁移。
