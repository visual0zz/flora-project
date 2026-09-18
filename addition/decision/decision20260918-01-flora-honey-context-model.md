# 决策：flora-honey 的预测模型升级为阶-N n-gram 上下文窗口

日期：2026-09-18
模块：flora-honey

## 决策

把 `cultivating/flora-honey` 中作为「LLM 预测」占位的 `CharTokenModel`（order-0，无上下文）
**替换**为 `NgramTokenModel`（阶-N n-gram 上下文窗口，默认 N=3，N=0 退化为自适应 unigram 基线）。
并采取**最小接口扩展**：`TokenModel` 只新增 `observe(int id)`，由编码器在编完每个 token 后、
解码器在解出每个 token 后调用，用于推进上下文窗口。

## 背景与原因

- 原型此前用 order-0 静态频率模型，`freq(id)` 与前文无关，压缩率≈满词表均匀熵，
  与「LLM 预测下一个 token」的真实形态不符，无法体现上下文对压缩的作用。
- 用户明确要求「带上下文窗口的 TokenModel」，并选择阶-N n-gram 形式、直接替换 order-0。

## 关键设计取舍

1. **对称推进保证蜜糖加密不变量**：编码端用真实 token、解码端用已解 token 推进同一窗口。
   正确密钥下两端序列一致 → 状态同步 → 精确往返；错误密钥下窗口分叉但仍是自洽诱饵，
   且 `RangeCoder` 末尾补 0 / 二分夹取保证**永不抛异常**，不引入预言机。
2. **分布用「先验 + 上下文估计」的归一化插值**（Dirichlet 平滑），而非裸计数：
   `freq(id) = base[id] + BOOST * baseTotal * count(ctx,id) / observed(ctx)`。
   - 原因一：裸计数 `base[id] + BOOST*count` 会被覆盖全部 65536 个符号的平坦先验
     （约 164000 质量）淹没，实测导致**高阶反而比 order-0 更差**；归一化后上下文权重只由
     `BOOST` 决定，实测在重复文本上 order-0 3695 字节 → order-2 204 字节。
   - 原因二：归一化后**总频率有界**（约 `baseTotal*(1+BOOST)`），不随文本长度增长，
     避免算术编码 `range*total` 溢出，无需逐符号重缩放。
3. **有界化**：上下文表条目超过上限时做**确定性清空**（只依赖已观察 token 序列，
   编/解码在正确密钥下同时触发），避免内存无界增长。
4. **保留全 BMP 词表且任意 id 频率恒 ≥ 1**：错误密钥解出的任意诱饵 token 都可能出现。
5. **编码器每符号重建累积表**（O(V)，V=65536）：实现最简、正确性最易保证。代价是超长文本
   （>1e5 符号）偏慢；升级路径为 Fenwick 树（接口再加 `cumBefore`/`symbolForCum`）实现 O(log V)。

## 影响面

- `TokenModel`：新增 `observe(int)`；`freq`/`seed` 语义扩展为「含上下文」。
- 新增 `NgramTokenModel`；删除 `CharTokenModel`。
- `RangeCoder`：每符号按当前模型重建累积表并 `observe`；保留 32 位掩码与末尾补 0。
- `HoneyCipher` 无逻辑改动（仍在编/解码前 `seed`）。
- `HoneyDemo` 增加阶-0 与阶-3 的编码体积对比；`package-info` 同步更新。
- 测试新增「上下文提升压缩率」「各阶精确往返」「错误密钥诱饵非空且确定」等用例。```

