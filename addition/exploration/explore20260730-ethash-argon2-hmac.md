# Ethash DAG、Argon2 与 HMAC-SHA512 的详细流程剖析

## 一、HMAC-SHA512

### 定义

HMAC（Hash-based Message Authentication Code）是 RFC 2104 定义的带密钥哈希算法。HMAC-SHA512 = 以 SHA-512 为底层哈希的 HMAC。

### 流程

```
输入：密钥 K（任意长度）、消息 M
输出：MAC 标签 T（64 字节）
```

```
                     K 超过 128 字节 → SHA-512(K) 截断
                     K 不足 128 字节 → 末尾补 0x00
                              │
                              ↓
                         K'（恰好 128 字节）
                        /             \
                       /               \
              ipad = K' ⊕ 0x3636...     opad = K' ⊕ 0x5C5C...
                       \               /
                        \             /
                     ┌───────────────┐
                     │  SHA-512       │
                     │ (ipad || M)   │
                     └───────┬───────┘
                             │
                             ↓
                          inner hash（64 字节）
                             │
                     ┌───────────────┐
                     │  SHA-512       │
                     │ (opad || inner)│
                     └───────┬───────┘
                             │
                             ↓
                         T（64 字节 MAC 标签）
```

### 伪代码

```python
def hmac_sha512(key: bytes, message: bytes) -> bytes:
    if len(key) > 128:
        key = sha512(key)         # 长密钥先哈希
    key = key + b'\x00' * (128 - len(key))  # 补到 128 字节

    ipad = bytes(a ^ 0x36 for a in key)
    opad = bytes(a ^ 0x5c for a in key)

    inner = sha512(ipad + message)
    tag   = sha512(opad + inner)
    return tag
```

### 安全边界

- SHA-512 输出 512 位（64 字节），安全强度 **256 位**
- HMAC 的安全性不依赖底层哈希的抗碰撞性，只依赖其 PRF 性质
- 对 HMAC-SHA512 的最佳攻击是暴力穷举密钥，强度 = min(密钥熵, 256)

---

## 二、Ethash DAG（以太坊 PoW 算法）

### 整体架构

```
区块号 N
    │
    │ 每 30000 块（约 5.2 天）重置一次，称为一个 epoch
    │ epoch_index = N // 30000
    ↓
seedhash = keccak256(seedhash)   ← 迭代 epoch_index 次，初始 = 全零 32 字节
    │
    │ seedhash 仅 32 字节，只用于生成 cache
    ↓
cache = keccak512 顺序填充 16MB  ← 唯一从 seedhash 派生，与区块数成正比
    │
    │ DAG 大小 = cache 大小 × 64
    ↓
DAG   = keccak256 逐个元素填充    ← 每个 DAG 元素 64 字节
        每元素需从 cache 中随机
        读取 ~64 次
```

### 第一步：seedhash

```python
def get_seedhash(epoch_index: int) -> bytes:
    seed = bytes(32)  # 全零
    for _ in range(epoch_index):
        seed = keccak256(seed)
    return seed
# 源码：EIP-7 中 ethash.py 的 get_seedhash()
```

### 第二步：cache（约 16MB）

```python
CACHE_SIZE = 16 * 1024 * 1024  # 约 16MB，实际向下修正为 64 字节整数倍
CACHE_ROUNDS = 3

def make_cache(seed: bytes) -> list[bytes]:
    # 1. 初始化：keccak512(seed) 作为第一个元素
    cache = []
    cache.append(keccak512(seed))

    # 2. 顺序填充：每个元素 = keccak512(前一个元素)
    for i in range(1, CACHE_SIZE // 64):
        cache.append(keccak512(cache[-1]))

    # 3. 后处理：RandMemoHash（3 轮随机读写）
    for _ in range(CACHE_ROUNDS):
        for i in range(len(cache)):
            v = cache[(i - 1 + len(cache)) % len(cache)]
            # 取前 32 字节作为索引 j
            j = int.from_bytes(v[:4], 'little') % len(cache)
            cache[i] = keccak512(cache[i] ^ cache[j])
    return cache
```

核心特性：
- cache 的每个元素是 64 字节（keccak512 的输出长度）
- RandMemoHash 阶段引入了随机读写——使小内存设备缓慢
- cache 的 size 由 epoch 决定，约从 16MB 起步缓慢增长

### 第三步：DAG（约 1-4GB）

```python
DAG_SIZE = CACHE_SIZE * 64  # DAG 约 1-4GB

def make_dag(dataset: list[bytes], cache: list[bytes]):
    for i in range(DAG_SIZE // 64):
        dataset[i] = calc_dag_item(i, cache)

def calc_dag_item(i: int, cache: list[bytes]) -> bytes:
    n = len(cache)
    # 用 keccak512 初始化
    mix = keccak512(i.to_bytes(32, 'big') ^ cache[i % n])

    # 64 轮随机访问 cache
    for j in range(64):
        v = mix[j % 16]
        p = int.from_bytes(v[:4], 'little') % n
        mix_parent_bytes = cache[p]
        mix_combined = bytes(mix[x] ^ mix_parent_bytes[x] for x in range(64))
        mix = keccak512(mix_combined)
    return keccak256(mix)  # 最终输出 32 字节，但 DAG 元素定义为 64 字节
```

注意：DAG 每个元素实际为 64 字节（32 字节 × 2），这里 `calc_dag_item` 输出需重复调用或按双字组合。

_DAG 生成在 home（CPU）上只需约 1 分钟，在 GPU 上约 5 秒。_

### 第四步：挖矿（Light / Heavy）

```python
def hashimoto(dag: list[bytes], header: bytes, nonce: int) -> bytes:
    # 1. 头部加上 nonce 做初始哈希
    seed_mix = keccak512(header + nonce.to_bytes(8, 'little'))

    # 2. 循环 64 轮，每轮从 DAG 读一个 128 字节扇区
    mix = [0] * 16  # 16 × 4 字节 = 64 字节
    for i in range(64):
        p = int.from_bytes(seed_mix[i % 16][:4], 'little') % len(dag)
        dag_sector = read_128_bytes(dag, p)
        mix[i % 16] = mix[i % 16] ^ dag_sector

    # 3. 压缩 → 32 字节摘要
    compressed = keccak256(seed_mix[:32] + bytes(mix) + seed_mix[-32:])
    return compressed
```

**重客户端（矿工）**：持完整 DAG，直接 `read_128_bytes(dag, p)`。
**轻客户端**：仅持 cache（16MB），每次 `calc_dag_item(p, cache)` → 慢约 1000 倍。

```python
def verify(header: bytes, nonce: int, difficulty: int) -> bool:
    # 轻客户端用 cache 在线计算 DAG 元素
    mix_hash = hashimoto_light(cache, header, nonce)  # 每次 calc_dag_item
    return int.from_bytes(mix_hash, 'big') * difficulty < 2^256
```

### 内存增长

| Epoch | 区块范围 | DAG 大小 | cache 大小 |
|-------|---------|---------|-----------|
| 0 | 0 - 29999 | ~1 GB | ~16 MB |
| 100 | 3M - 3.03M | ~2.5 GB | ~20 MB |
| 200 | 6M - 6.03M | ~3.8 GB | ~24 MB |
| 300 | 9M - 9.03M | ~4.2 GB | ~28 MB |

---

## 三、Argon2

### 定义

Argon2 是 Password Hashing Competition（PHC）2015 年冠军，标准为 RFC 9106。三个变体：

- **Argon2d**：数据依赖访问（抵抗 GPU），用于加密货币
- **Argon2i**：数据独立访问（抵抗侧信道），用于密码哈希
- **Argon2id**：混合模式（推荐），前半段 Argon2i + 后半段 Argon2d

### 参数

```
输入：密码 P、盐 S、关联数据 X、秘密值 K
参数：时间成本 t、内存成本 m、并行度 p、输出长度 tag_len
输出：tag（tag_len 字节）
```

### 整体流程

```
┌──────────────────────────────────────────────────┐
│                   Argon2                          │
│                                                    │
│  1. 初始化：Blake2b(密码 || 盐 || 参数) 生成块 0,1  │
│  2. 构建内存矩阵：rows[1..m-1] 由前一行的随机访问生成 │
│  3. 迭代 t 轮，每轮全遍历矩阵（随机读）               │
│  4. 收尾：Blake2b(矩阵最后一块) → tag               │
└──────────────────────────────────────────────────┘
```

### 第一步：初始化

```python
def initialize(password: bytes, salt: bytes, m: int, p: int) -> list[list[Block]]:
    # 用 Blake2b 压缩所有参数为 64 字节的初始块 H0
    H0 = blake2b(
        parameter_length ||
        parallelism  ||  tag_length  ||  memory_size  ||
        iterations   ||  version     ||  type          ||
        password_len ||  password    ||  salt_len      ||
        salt         ||  secret_len  ||  secret         ||
        associated_len || associated
    )

    # 生成每行的前两个块（lanes × 2）
    matrix = [[None] * m for _ in range(p)]
    for lane in range(p):
        matrix[lane][0] = blake2b(H0 || lane || 0)
        matrix[lane][1] = blake2b(H0 || lane || 1)

    return matrix
```

### 第二步：填充矩阵

每一列 segment 为 `m / 16p` 个块。算法遍历所有 segment，对每个块：

```
for segment in 1..16:
    for lane in 0..p-1:
        for block in 0..blocks_per_segment:
            i = get_current_index(lane, segment, block)

            # 取前一块
            prev = matrix[lane][i - 1]

            # 确定"参考块"位置 j
            j = get_reference_index(matrix, lane, i, type)  # type 决定 Argon2d/i/id

            # 压缩
            matrix[lane][i] = blake2b(prev ^ matrix[j])
```

`get_reference_index` 是如何实现 memory-hard 的关键：

```python
def get_reference_index(matrix, current_lane, i, type):
    # 取前一块的后 64 位作为伪随机种子
    pseudo_random = int.from_bytes(matrix[current_lane][i-1][-8:], 'little')
    # 计算参考位置（不同 lane，确保跨越性访问）
    ref_lane = (pseudo_random >> 32) % parallelism if i > segment_boundary else current_lane
    ref_area = min(i, slice_length)
    ref_index = (pseudo_random & 0xFFFFFFFF) % ref_area
    return ref_index
```

### 第三步：迭代

重复第 2 步 `t` 次（时间成本）。每次迭代的起始数据是上一次迭代的最后一块。

### 第四步：收尾

```python
def finalize(matrix):
    # 所有 lane 的最后一块做 XOR
    final_block = matrix[0][-1]
    for lane in range(1, p):
        final_block ^= matrix[lane][-1]

    # 输出 tag
    tag = blake2b(final_block, output_length=tag_len)
    return tag
```

---

## 四、三者对比一览

| 维度 | HMAC-SHA512 | Ethash DAG | Argon2 |
|------|------------|-----------|--------|
| **目的** | 消息认证 | PoW 挖矿 | 密钥派生 |
| **类型** | 消息认证码 | 内存硬 PoW | 内存硬 KDF |
| **底层原语** | SHA-512 | Keccak-256/-512 | Blake2b |
| **内存量** | 固定（无） | 1-4 GB | 可配置（默认 64MB） |
| **迭代** | 1 轮 | 64 轮随机读 + 多次 Keccak | t 轮全遍历 |
| **对称性** | 对称 | 不对称（light/heavy） | 对称 |
| **密钥输入** | 密钥 K | 无（仅区块头+nonce） | 密码 P |
| **输出** | 64 字节 | 32 字节 | 可配置 |

### 核心区别图

```
HMAC-SHA512:
  K ─→ ⊕ipad ─→ SHA512  ─┐
  K ─→ ⊕opad ─→ SHA512 ←─┘  ← 无内存需求，纯计算

Ethash DAG:
  seed ─→ Keccak → 16MB cache ─→ Keccak → 4GB DAG ─→ 随机读 → hash
                                                            ↑
                                                    依赖大内存，不可压缩

Argon2:
  password ─→ Blake2b → m×p 矩阵 ─→ t 轮随机读写 ─→ Blake2b → tag
                                 ↑
                          依赖大内存，不可并行
```

HMAC-SHA512 是纯计算（无内存需求），Ethash DAG 与 Argon2 都是 memory-hard 结构——但前者是 PoW 专用（验证不对称），后者是通用 KDF（内存大小可配置、有安全证明）。
