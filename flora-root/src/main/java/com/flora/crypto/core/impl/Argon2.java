package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.core.interfaces.material.param.DerivationParameter;
import com.flora.crypto.core.param.Argon2Parameters;
import com.flora.java.CheckUtil;

import java.util.Set;

/**
 * Argon2 口令派生函数（RFC 9106），支持 Argon2d / Argon2i / Argon2id。
 * <p>结构：H0 由参数域经 BLAKE2b-512 派生；内存按 lane/slice 填充，块间经压缩函数
 * {@code G}（BLAKE2b 轮 + trunc 乘法）混合；引用索引按类型（数据无关/相关/混合）寻址；
 * 最终对末列 XOR 再 BLAKE2b 出标签。</p>
 */
public final class Argon2 implements DerivationFunction {

  private static final int BLOCK_SIZE = 1024;
  private static final int SYNC_POINTS = 4;

  private Argon2Parameters params;

  public Argon2() {
    // 无状态，init 后使用
  }

  @Override
  public String getAlgorithmName() {
    int type = params == null ? Argon2Parameters.ARGON2id : params.getType();
    return switch (type) {
      case Argon2Parameters.ARGON2d -> "Argon2d";
      case Argon2Parameters.ARGON2i -> "Argon2i";
      default -> "Argon2id";
    };
  }

  @Override
  public void init(DerivationParameter params) {
    CheckUtil.notNull(params, "参数不能为空");
    if (!(params instanceof Argon2Parameters)) {
      throw new IllegalArgumentException("Argon2 需要 Argon2Parameters");
    }
    this.params = (Argon2Parameters) params;
  }

  @Override
  public void update(byte[] in, int inOff, int len) {
    throw new UnsupportedOperationException("Argon2 为非增量 KDF，不支持增量输入");
  }

  @Override
  public int generateBytes(byte[] out, int outOff, int len) {
    byte[] tag = derive(params, len);
    System.arraycopy(tag, 0, out, outOff, len);
    return len;
  }

  private static byte[] derive(Argon2Parameters p, int tagLen) {
    int lanes = p.getParallelism();
    int mPrime = 4 * lanes * (p.getMemoryKib() / (4 * lanes)); // 块数（1 KiB/块）
    int laneLength = mPrime / lanes;
    int segmentLength = laneLength / SYNC_POINTS;

    byte[] h0 = initialHash(p, tagLen);
    byte[][] blocks = new byte[mPrime][];
    for (int i = 0; i < lanes; i++) {
      blocks[i * laneLength] = variableHash(concat(h0, le32(0), le32(i)), BLOCK_SIZE);
      blocks[i * laneLength + 1] = variableHash(concat(h0, le32(1), le32(i)), BLOCK_SIZE);
    }

    for (int pass = 0; pass < p.getIterations(); pass++) {
      for (int slice = 0; slice < SYNC_POINTS; slice++) {
        for (int lane = 0; lane < lanes; lane++) {
          int start = slice * segmentLength;
          int end = (slice + 1) * segmentLength;
          if (pass == 0 && slice == 0) {
            start = 2;
          }
          for (int j = start; j < end; j++) {
            int cur = lane * laneLength + j;
            // 块在 segment 内的相对索引（pass0 slice0 从 2 起，其余从 0 起）
            int within = j - slice * segmentLength;

            // prev 块：lane 第一块（j=0）时环绕到 lane 末尾
            int prevOffset = (cur % laneLength == 0) ? cur + laneLength - 1 : cur - 1;

            long j1;
            long j2;
            if (useDataIndependent(p.getType(), pass, slice)) {
              long value = independentValue(p, pass, lane, slice, mPrime, within);
              j1 = value & 0xffffffffL;
              j2 = value >>> 32;
            } else {
              long prev = readLong(blocks[prevOffset], 0);
              j1 = prev & 0xffffffffL;
              j2 = (prev >>> 32) & 0xffffffffL;
            }

            int refLane = (int) (j2 % lanes);
            if (pass == 0 && slice == 0) {
              refLane = lane;
            }
            int refIndex = indexAlpha(p, pass, slice, lane, refLane, j1, within,
                segmentLength, laneLength);
            // pass>0 时按 Argon2 v1.3 需与旧块 XOR
            blocks[cur] = fillBlock(blocks[prevOffset], blocks[refLane * laneLength + refIndex],
                blocks[cur], pass != 0);
          }
        }
      }
    }

    byte[] c = new byte[BLOCK_SIZE];
    for (int lane = 0; lane < lanes; lane++) {
      byte[] last = blocks[lane * laneLength + laneLength - 1];
      for (int i = 0; i < BLOCK_SIZE; i++) {
        c[i] ^= last[i];
      }
    }
    // 参考实现 finalize 用 H'（blake2b_long），非直接 H
    return variableHash(c, tagLen);
  }

  private static boolean useDataIndependent(int type, int pass, int slice) {
    return type == Argon2Parameters.ARGON2i
        || (type == Argon2Parameters.ARGON2id && pass == 0 && slice < 2);
  }

  // ===== H0 / H' =====

  private static byte[] initialHash(Argon2Parameters p, int tagLen) {
    Blake2bDigest d = Blake2bDigest.of512();
    d.update(le32(p.getParallelism()), 0, 4);
    d.update(le32(tagLen), 0, 4);
    d.update(le32(p.getMemoryKib()), 0, 4);
    d.update(le32(p.getIterations()), 0, 4);
    d.update(le32(Argon2Parameters.VERSION), 0, 4);
    d.update(le32(p.getType()), 0, 4);
    byte[] pass = p.getPassword();
    d.update(le32(pass.length), 0, 4);
    d.update(pass, 0, pass.length);
    byte[] salt = p.getSalt();
    d.update(le32(salt.length), 0, 4);
    d.update(salt, 0, salt.length);
    byte[] sec = p.getSecret();
    d.update(le32(sec.length), 0, 4);
    d.update(sec, 0, sec.length);
    byte[] ad = p.getAdditional();
    d.update(le32(ad.length), 0, 4);
    d.update(ad, 0, ad.length);
    byte[] out = new byte[64];
    d.doFinal(out, 0);
    return out;
  }

  /** RFC 9106 §3.3 变长哈希 H'^T(A)。 */
  private static byte[] variableHash(byte[] a, int outLen) {
    if (outLen <= 64) {
      Blake2bDigest d = new Blake2bDigest(outLen);
      d.update(le32(outLen), 0, 4);
      d.update(a, 0, a.length);
      byte[] out = new byte[outLen];
      d.doFinal(out, 0);
      return out;
    }
    int r = (outLen + 31) / 32 - 2;
    byte[] out = new byte[outLen];
    Blake2bDigest d = new Blake2bDigest(64);
    d.update(le32(outLen), 0, 4);
    d.update(a, 0, a.length);
    byte[] v = new byte[64];
    d.doFinal(v, 0);
    System.arraycopy(v, 0, out, 0, 32);
    for (int i = 2; i <= r; i++) {
      d = new Blake2bDigest(64);
      d.update(v, 0, 64);
      d.doFinal(v, 0);
      System.arraycopy(v, 0, out, (i - 1) * 32, 32);
    }
    int remaining = outLen - 32 * r;
    d = new Blake2bDigest(remaining);
    d.update(v, 0, 64);
    byte[] last = new byte[remaining];
    d.doFinal(last, 0);
    System.arraycopy(last, 0, out, r * 32, remaining);
    return out;
  }

  // ===== 压缩函数 G =====

  /** RFC 9106 §3.5 压缩函数；{@code withXor} 时按 Argon2 v1.3 pass>0 语义与旧块 XOR。 */
  private static byte[] fillBlock(byte[] prev, byte[] ref, byte[] next, boolean withXor) {
    long[] v = new long[128]; // R = prev ⊕ ref
    long[] tmp = new long[128];
    for (int i = 0; i < 128; i++) {
      v[i] = readLong(prev, i * 8) ^ readLong(ref, i * 8);
      tmp[i] = v[i];
    }
    if (withXor) {
      for (int i = 0; i < 128; i++) {
        tmp[i] ^= readLong(next, i * 8);
      }
    }
    for (int i = 0; i < 8; i++) {
      blake2Round(v, i * 16);
    }
    long[] col = new long[16];
    for (int i = 0; i < 8; i++) {
      for (int row = 0; row < 8; row++) {
        col[row * 2] = v[row * 16 + 2 * i];
        col[row * 2 + 1] = v[row * 16 + 2 * i + 1];
      }
      blake2Round(col, 0);
      for (int row = 0; row < 8; row++) {
        v[row * 16 + 2 * i] = col[row * 2];
        v[row * 16 + 2 * i + 1] = col[row * 2 + 1];
      }
    }
    byte[] out = new byte[BLOCK_SIZE];
    for (int i = 0; i < 128; i++) {
      writeLong(tmp[i] ^ v[i], out, i * 8);
    }
    return out;
  }

  private static void blake2Round(long[] x, int base) {
    g(x, base + 0, base + 4, base + 8, base + 12);
    g(x, base + 1, base + 5, base + 9, base + 13);
    g(x, base + 2, base + 6, base + 10, base + 14);
    g(x, base + 3, base + 7, base + 11, base + 15);
    g(x, base + 0, base + 5, base + 10, base + 15);
    g(x, base + 1, base + 6, base + 11, base + 12);
    g(x, base + 2, base + 7, base + 8, base + 13);
    g(x, base + 3, base + 4, base + 9, base + 14);
  }

  /** Argon2 的 G 函数：BLAKE2b 轮 + trunc 乘法项（RFC 9106 §3.6）。 */
  private static void g(long[] v, int a, int b, int c, int d) {
    // trunc(x) = 低 32 位无符号扩展；乘积按 64 位无符号运算（long 溢出即 mod 2^64）
    long ta = Integer.toUnsignedLong((int) v[a]);
    long tb = Integer.toUnsignedLong((int) v[b]);
    long tc = Integer.toUnsignedLong((int) v[c]);
    long td = Integer.toUnsignedLong((int) v[d]);
    v[a] = v[a] + v[b] + 2L * ta * tb;
    v[d] = Long.rotateRight(v[d] ^ v[a], 32);
    tc = Integer.toUnsignedLong((int) v[c]);
    td = Integer.toUnsignedLong((int) v[d]);
    v[c] = v[c] + v[d] + 2L * tc * td;
    v[b] = Long.rotateRight(v[b] ^ v[c], 24);
    ta = Integer.toUnsignedLong((int) v[a]);
    tb = Integer.toUnsignedLong((int) v[b]);
    v[a] = v[a] + v[b] + 2L * ta * tb;
    v[d] = Long.rotateRight(v[d] ^ v[a], 16);
    tc = Integer.toUnsignedLong((int) v[c]);
    td = Integer.toUnsignedLong((int) v[d]);
    v[c] = v[c] + v[d] + 2L * tc * td;
    v[b] = Long.rotateRight(v[b] ^ v[c], 63);
  }

  // ===== 索引寻址 =====

  /** Argon2i：数据无关索引，{@code G(0, G(0, Z‖LE64(counter)‖0))} 生成地址块逐对取值。 */
  private static long independentValue(Argon2Parameters p, int pass, int lane, int slice,
      int mPrime, int within) {
    byte[] input = new byte[BLOCK_SIZE];
    writeLong(pass, input, 0);
    writeLong(lane, input, 8);
    writeLong(slice, input, 16);
    writeLong(mPrime, input, 24);
    writeLong(p.getIterations(), input, 32);
    writeLong(p.getType(), input, 40);
    writeLong(within / 128 + 1, input, 48); // ref.c 每个 segment 首个地址块计数器从 1 起
    byte[] address = fillBlock(zero(), input, zero(), false);
    address = fillBlock(zero(), address, zero(), false);
    return readLong(address, (within % 128) * 8);
  }

  private static byte[] zeroBlock;

  private static byte[] zero() {
    if (zeroBlock == null) {
      zeroBlock = new byte[BLOCK_SIZE];
    }
    return zeroBlock;
  }

  /** 从引用域 W 计算引用位置（ref.c index_alpha 语义）。 */
  private static int indexAlpha(Argon2Parameters p, int pass, int slice, int lane, int refLane,
      long j1, int within, int segmentLength, int laneLength) {
    boolean sameLane = refLane == lane;
    long referenceAreaSize;
    if (pass == 0) {
      if (slice == 0) {
        referenceAreaSize = within - 1;
      } else if (sameLane) {
        referenceAreaSize = slice * segmentLength + within - 1;
      } else {
        referenceAreaSize = slice * segmentLength + (within == 0 ? -1 : 0);
      }
    } else if (sameLane) {
      referenceAreaSize = laneLength - segmentLength + within - 1;
    } else {
      referenceAreaSize = laneLength - segmentLength + (within == 0 ? -1 : 0);
    }
    if (referenceAreaSize < 0) {
      referenceAreaSize = 0;
    }
    long x = j1 & 0xffffffffL;
    x = (x * x) >>> 32;
    long y = (referenceAreaSize * x) >>> 32;
    long relative = referenceAreaSize - 1 - y;
    long start = 0;
    if (pass != 0) {
      start = (slice + 1) * segmentLength;
      if (start >= laneLength) {
        start = 0;
      }
    }
    return (int) ((start + relative) % laneLength);
  }

  // ===== 工具 =====

  private static byte[] concat(byte[] a, byte[] b, byte[] c) {
    byte[] out = new byte[a.length + b.length + c.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    System.arraycopy(c, 0, out, a.length + b.length, c.length);
    return out;
  }

  private static byte[] le32(int v) {
    return new byte[] {(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
  }

  private static long readLong(byte[] b, int off) {
    return (b[off] & 0xffL) | (b[off + 1] & 0xffL) << 8 | (b[off + 2] & 0xffL) << 16
        | (b[off + 3] & 0xffL) << 24 | (b[off + 4] & 0xffL) << 32 | (b[off + 5] & 0xffL) << 40
        | (b[off + 6] & 0xffL) << 48 | (b[off + 7] & 0xffL) << 56;
  }

  private static void writeLong(long v, byte[] b, int off) {
    b[off] = (byte) v;
    b[off + 1] = (byte) (v >>> 8);
    b[off + 2] = (byte) (v >>> 16);
    b[off + 3] = (byte) (v >>> 24);
    b[off + 4] = (byte) (v >>> 32);
    b[off + 5] = (byte) (v >>> 40);
    b[off + 6] = (byte) (v >>> 48);
    b[off + 7] = (byte) (v >>> 56);
  }

  @Override
  public AlgorithmFactory<? extends DerivationFunction> factory() {
    return FACTORY;
  }

  public static final AlgorithmFactory<DerivationFunction> FACTORY = new AlgorithmFactory<>() {
    @Override
    public Class<? extends AlgorithmFactoryRegister> registerTo() {
      return CryptoAlgorithmFactoryRegister.class;
    }

    @Override
    public Set<String> supportedAlgorithms() {
      return Set.of("Argon2", "Argon2d", "Argon2i", "Argon2id");
    }

    @Override
    public int priority() {
      return 0;
    }

    @Override
    public Class<AlgorithmComponent>[] componentTypes() {
      return new Class[0];
    }

    @Override
    public DerivationFunction construct(String algorithmName, AlgorithmComponent... components) {
      CheckUtil.notNull(algorithmName, "算法名不能为空");
      return new Argon2();
    }
  };
}
