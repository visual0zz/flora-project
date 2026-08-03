package com.flora.crypto.core.engine;

import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.KeyParameter;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Poly1305 消息认证码（RFC 8439 §2.5）。
 * <p>在模 {@code 2^130-5} 素域上做多项式累加：{@code a = ((a + 块‖0x01) * r) mod P}，
 * 末尾 {@code a = (a + s) mod 2^128}。密钥 32 字节 = r(16) ‖ s(16)，r 需 clamp。
 * 用 {@link BigInteger} 实现，正确性优先（仅认证短数据，性能非关键）。</p>
 */
public final class Poly1305Mac implements Mac {

  private static final BigInteger P = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5));
  private static final BigInteger MOD_2_128 = BigInteger.ONE.shiftLeft(128);

  private BigInteger r;
  private BigInteger s;
  private BigInteger acc = BigInteger.ZERO;

  private final byte[] buf = new byte[16];
  private int bufOff;

  public Poly1305Mac() {
    reset();
  }

  @Override
  public String getAlgorithmName() {
    return "Poly1305";
  }

  @Override
  public int getMacSize() {
    return 16;
  }

  @Override
  public void init(CipherParameters params) {
    if (!(params instanceof KeyParameter)) {
      throw new IllegalArgumentException("Poly1305 需要 KeyParameter（32 字节密钥）");
    }
    byte[] key = ((KeyParameter) params).getKey();
    if (key.length != 32) {
      throw new IllegalArgumentException("Poly1305 密钥必须为 32 字节");
    }
    byte[] rbytes = Arrays.copyOfRange(key, 0, 16);
    clamp(rbytes);
    r = readLittleEndian(rbytes, 0, 16);
    s = readLittleEndian(key, 16, 16);
    reset();
  }

  @Override
  public void update(byte in) {
    buf[bufOff++] = in;
    if (bufOff == 16) {
      processBlock(buf, 0, 16);
      bufOff = 0;
    }
  }

  @Override
  public void update(byte[] in, int inOff, int len) {
    while (bufOff != 0 && len > 0) {
      update(in[inOff]);
      inOff++;
      len--;
    }
    while (len >= 16) {
      processBlock(in, inOff, 16);
      inOff += 16;
      len -= 16;
    }
    while (len > 0) {
      update(in[inOff]);
      inOff++;
      len--;
    }
  }

  @Override
  public int doFinal(byte[] out, int outOff) {
    if (bufOff != 0) {
      processBlock(buf, 0, bufOff);
      bufOff = 0;
    }
    BigInteger a = acc.add(s).mod(MOD_2_128);
    // 小端写 16 字节：out[0] 为最低字节
    byte[] be = a.toByteArray();
    Arrays.fill(out, outOff, outOff + 16, (byte) 0);
    for (int i = 0; i < be.length && i < 16; i++) {
      out[outOff + i] = be[be.length - 1 - i];
    }
    reset();
    return 16;
  }

  @Override
  public void reset() {
    acc = BigInteger.ZERO;
    Arrays.fill(buf, (byte) 0);
    bufOff = 0;
  }

  // ===== 内部 =====

  private void processBlock(byte[] in, int inOff, int len) {
    // n = 块作为 128 位小端整数 + 2^(8*len)
    BigInteger n = readLittleEndian(in, inOff, len).add(BigInteger.ONE.shiftLeft(8 * len));
    acc = acc.add(n).multiply(r).mod(P);
  }

  /** RFC 8439 §2.5 clamp：清字节 3/7/11/15 高 4 位、字节 4/8/12 低 2 位。 */
  private static void clamp(byte[] r) {
    r[3] &= 0x0f;
    r[7] &= 0x0f;
    r[11] &= 0x0f;
    r[15] &= 0x0f;
    r[4] &= 0xfc;
    r[8] &= 0xfc;
    r[12] &= 0xfc;
  }

  /** 小端读 {@code len} 字节为无符号 BigInteger。 */
  private static BigInteger readLittleEndian(byte[] b, int off, int len) {
    byte[] be = new byte[len + 1]; // 前置 0 保证正数
    for (int i = 0; i < len; i++) {
      be[1 + i] = b[off + len - 1 - i];
    }
    return new BigInteger(be);
  }
}
