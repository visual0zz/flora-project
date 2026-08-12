package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.bridge.JdkDigest;
import com.flora.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.core.interfaces.material.param.DerivationParameter;
import com.flora.crypto.core.param.Pbkdf2Parameters;
import com.flora.crypto.core.param.ScryptParameters;
import com.flora.java.CheckUtil;

import java.util.Arrays;
import java.util.Set;

/**
 * scrypt 口令派生函数（RFC 7914）。
 * <p>结构：{@code B = PBKDF2(P, S, 1, p*128*r)}，对每个 r 块组做 {@code N} 次
 * {@code BlockMix}（Salsa20/8 核心）的 ROMix，再 {@code DK = PBKDF2(P, B', 1, dkLen)}。
 * PBKDF2 复用 newcore {@link Pbkdf2DerivationFunction}（HmacSHA256 原语）。</p>
 */
public final class Scrypt implements DerivationFunction {

  private ScryptParameters params;

  public Scrypt() {
    // 无状态，init 后使用
  }

  @Override
  public String getAlgorithmName() {
    return "scrypt";
  }

  @Override
  public void init(DerivationParameter params) {
    CheckUtil.notNull(params, "参数不能为空");
    if (!(params instanceof ScryptParameters)) {
      throw new IllegalArgumentException("scrypt 需要 ScryptParameters");
    }
    this.params = (ScryptParameters) params;
  }

  @Override
  public void update(byte[] in, int inOff, int len) {
    throw new UnsupportedOperationException("scrypt 为非增量 KDF，不支持增量输入");
  }

  @Override
  public int generateBytes(byte[] out, int outOff, int len) {
    ScryptParameters p = params;
    int n = p.getN();
    int r = p.getR();
    int pp = p.getP();

    byte[] b = pbkdf2(p.getPassword(), p.getSalt(), 1, pp * 128 * r);
    for (int i = 0; i < pp; i++) {
      byte[] block = Arrays.copyOfRange(b, i * 128 * r, (i + 1) * 128 * r);
      block = romix(block, n, r);
      System.arraycopy(block, 0, b, i * 128 * r, block.length);
    }

    byte[] dk = pbkdf2(p.getPassword(), b, 1, len);
    System.arraycopy(dk, 0, out, outOff, len);
    return len;
  }

  private static byte[] pbkdf2(byte[] pass, byte[] salt, int iter, int dkLen) {
    Pbkdf2DerivationFunction gen = new Pbkdf2DerivationFunction(
            new HMac(JdkDigest.of("SHA-256")));
    gen.init(new Pbkdf2Parameters(pass, salt, iter));
    byte[] dk = new byte[dkLen];
    gen.generateBytes(dk, 0, dkLen);
    return dk;
  }

  /** RFC 7914 §4.3 ROMix：生成 V 表，再按 Integerify 索引混合。 */
  private static byte[] romix(byte[] b, int n, int r) {
    byte[] x = b.clone();
    byte[][] v = new byte[n][];
    for (int i = 0; i < n; i++) {
      v[i] = x.clone();
      x = blockMix(x, r);
    }
    for (int i = 0; i < n; i++) {
      int j = integerify(x) & (n - 1); // N 为 2 的幂，模运算等价于掩码
      byte[] t = new byte[x.length];
      for (int k = 0; k < x.length; k++) {
        t[k] = (byte) (x[k] ^ v[j][k]);
      }
      x = blockMix(t, r);
    }
    return x;
  }

  /** RFC 7914 §4.2 BlockMix：Salsa20/8 核心混合后偶数块在前、奇数块在后。 */
  private static byte[] blockMix(byte[] b, int r) {
    byte[] x = Arrays.copyOfRange(b, b.length - 64, b.length);
    byte[] y = new byte[b.length];
    for (int i = 0; i < 2 * r; i++) {
      for (int k = 0; k < 64; k++) {
        x[k] ^= b[i * 64 + k];
      }
      x = salsa208(x);
      System.arraycopy(x, 0, y, i * 64, 64);
    }
    byte[] out = new byte[b.length];
    int off = 0;
    for (int i = 0; i < r; i++) {
      System.arraycopy(y, i * 2 * 64, out, off, 64);
      off += 64;
    }
    for (int i = 0; i < r; i++) {
      System.arraycopy(y, (i * 2 + 1) * 64, out, off, 64);
      off += 64;
    }
    return out;
  }

  /** RFC 7914 §4.3：取最后一个 64 字节块的前 4 字节小端整数。 */
  private static int integerify(byte[] b) {
    int off = b.length - 64;
    return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16)
        | ((b[off + 3] & 0xff) << 24);
  }

  /** Salsa20/8 核心：8 轮（4 次双轮），输出 = 输入 + 轮后状态。 */
  private static byte[] salsa208(byte[] in) {
    int[] x = new int[16];
    for (int i = 0; i < 16; i++) {
      x[i] = readWord(in, i * 4);
    }
    int[] original = x.clone();
    for (int i = 0; i < 4; i++) {
      // column round
      qr(x, 0, 4, 8, 12);
      qr(x, 5, 9, 13, 1);
      qr(x, 10, 14, 2, 6);
      qr(x, 15, 3, 7, 11);
      // row round
      qr(x, 0, 1, 2, 3);
      qr(x, 5, 6, 7, 4);
      qr(x, 10, 11, 8, 9);
      qr(x, 15, 12, 13, 14);
    }
    byte[] out = new byte[64];
    for (int i = 0; i < 16; i++) {
      writeWord(x[i] + original[i], out, i * 4);
    }
    return out;
  }

  private static void qr(int[] x, int a, int b, int c, int d) {
    x[b] ^= rol(x[a] + x[d], 7);
    x[c] ^= rol(x[b] + x[a], 9);
    x[d] ^= rol(x[c] + x[b], 13);
    x[a] ^= rol(x[d] + x[c], 18);
  }

  private static int rol(int x, int n) {
    return (x << n) | (x >>> (32 - n));
  }

  private static int readWord(byte[] b, int off) {
    return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16)
        | ((b[off + 3] & 0xff) << 24);
  }

  private static void writeWord(int w, byte[] b, int off) {
    b[off] = (byte) w;
    b[off + 1] = (byte) (w >>> 8);
    b[off + 2] = (byte) (w >>> 16);
    b[off + 3] = (byte) (w >>> 24);
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
      return Set.of("scrypt", "Scrypt");
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
      return new Scrypt();
    }
  };
}
