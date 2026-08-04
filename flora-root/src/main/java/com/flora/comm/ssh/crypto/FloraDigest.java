package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.HASH;
import com.flora.crypto.core.bridge.JdkDigest;

/**
 * 摘要（HASH）适配基类。
 * <p>把 flora {@link JdkDigest} 接入 JSch 的 {@code HASH} 契约。子类给出 JDK 算法名、
 * 摘要长度（{@code getBlockSize()} 返回）与自述名。</p>
 */
abstract class FloraDigest implements HASH {

  private final String algorithm;
  private final int digestSize;
  private final String name;

  private JdkDigest digest;

  FloraDigest(String algorithm, int digestSize, String name) {
    this.algorithm = algorithm;
    this.digestSize = digestSize;
    this.name = name;
  }

  @Override
  public int getBlockSize() {
    return digestSize;
  }

  @Override
  public void init() throws Exception {
    digest = JdkDigest.of(algorithm);
  }

  @Override
  public void update(byte[] foo, int start, int len) throws Exception {
    digest.update(foo, start, len);
  }

  @Override
  public byte[] digest() throws Exception {
    byte[] out = new byte[digestSize];
    digest.doFinal(out, 0);
    return out;
  }

  @Override
  public String name() {
    return name;
  }
}
