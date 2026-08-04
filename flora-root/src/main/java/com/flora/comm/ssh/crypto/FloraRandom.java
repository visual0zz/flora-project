package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.Random;
import com.flora.crypto.core.bridge.SecureRandomEntropySource;

/**
 * 随机数适配类。
 * <p>随机字节生成委托 flora {@link SecureRandomEntropySource}（JDK {@code SecureRandom}）。</p>
 */
public class FloraRandom implements Random {

  private final SecureRandomEntropySource random = new SecureRandomEntropySource();

  @Override
  public void fill(byte[] foo, int start, int len) {
    byte[] tmp = random.getEntropy(len * 8);
    System.arraycopy(tmp, 0, foo, start, len);
  }
}
