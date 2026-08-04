package com.flora.crypto.core.impl;

import com.flora.crypto.core.param.KeyParameter;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.interfaces.provider.ExtendedDigest;
import com.flora.crypto.core.interfaces.provider.Mac;
import java.util.Arrays;

/**
 * HMAC（RFC 2104）自研实现，以 {@link ExtendedDigest} 为哈希原语。
 * <p>与委托 JDK 的 {@link JdkMac} 互补：JDK 的 {@code javax.crypto.Mac} 拒绝空密钥，
 * 本实现支持任意长度密钥（含 0 字节），满足 PBKDF2/scrypt 的空口令等标准场景。</p>
 */
public final class HMac implements Mac {

  private final ExtendedDigest digest;
  private final int blockSize;

  private final byte[] ipad;
  private final byte[] opad;

  public HMac(ExtendedDigest digest) {
    this.digest = digest;
    this.blockSize = digest.getByteLength();
    this.ipad = new byte[blockSize];
    this.opad = new byte[blockSize];
  }

  @Override
  public String getAlgorithmName() {
    return digest.getAlgorithmName() + "/HMAC";
  }

  @Override
  public int getMacSize() {
    return digest.getDigestSize();
  }

  @Override
  public void init(CipherParameters params) {
    if (!(params instanceof KeyParameter)) {
      throw new IllegalArgumentException("HMAC 需要 KeyParameter");
    }
    byte[] key = ((KeyParameter) params).getKey();
    byte[] kPad;
    if (key.length > blockSize) {
      digest.reset();
      digest.update(key, 0, key.length);
      byte[] hashed = new byte[digest.getDigestSize()];
      digest.doFinal(hashed, 0);
      kPad = new byte[blockSize];
      System.arraycopy(hashed, 0, kPad, 0, hashed.length);
      Arrays.fill(hashed, (byte) 0);
    } else {
      kPad = new byte[blockSize];
      System.arraycopy(key, 0, kPad, 0, key.length);
    }
    for (int i = 0; i < blockSize; i++) {
      ipad[i] = (byte) (kPad[i] ^ 0x36);
      opad[i] = (byte) (kPad[i] ^ 0x5c);
    }
    Arrays.fill(kPad, (byte) 0);
    reset();
  }

  @Override
  public void update(byte in) {
    digest.update(in);
  }

  @Override
  public void update(byte[] in, int inOff, int len) {
    digest.update(in, inOff, len);
  }

  @Override
  public int doFinal(byte[] out, int outOff) {
    byte[] innerHash = new byte[digest.getDigestSize()];
    digest.doFinal(innerHash, 0); // digest 当前含 ipad‖msg

    digest.reset();
    digest.update(opad, 0, blockSize);
    digest.update(innerHash, 0, innerHash.length);
    Arrays.fill(innerHash, (byte) 0);
    int macSize = digest.doFinal(out, outOff);

    reset();
    return macSize;
  }

  @Override
  public void reset() {
    digest.reset();
    digest.update(ipad, 0, blockSize);
  }
}
