package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.MAC;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.engine.JdkMac;

/**
 * HMAC MAC 适配基类。
 * <p>把 flora {@link JdkMac} 接入 JSch 的 {@code MAC} 契约。子类给出线名 {@code name}、
 * 密钥截断长度 {@code keySize}、输出长度 {@code outputSize}（{@code getBlockSize()} 返回，
 * 96 截断 / ssh.com 变体与密钥长度不同）、JDK 算法名与 EtM 标记。</p>
 */
abstract class FloraMac implements MAC {

  private final String name;
  private final int keySize;
  private final int outputSize;
  private final String algorithm;
  private final boolean etm;

  private final byte[] tmp = new byte[4];
  private JdkMac mac;

  FloraMac(String name, int keySize, int outputSize, String algorithm, boolean etm) {
    this.name = name;
    this.keySize = keySize;
    this.outputSize = outputSize;
    this.algorithm = algorithm;
    this.etm = etm;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getBlockSize() {
    return outputSize;
  }

  @Override
  public boolean isEtM() {
    return etm;
  }

  @Override
  public void init(byte[] key) throws Exception {
    if (key.length > keySize) {
      byte[] tmp = new byte[keySize];
      System.arraycopy(key, 0, tmp, 0, keySize);
      key = tmp;
    }
    mac = JdkMac.of(algorithm);
    mac.init(new KeyParameter(key));
  }

  @Override
  public void update(int i) {
    tmp[0] = (byte) (i >>> 24);
    tmp[1] = (byte) (i >>> 16);
    tmp[2] = (byte) (i >>> 8);
    tmp[3] = (byte) i;
    update(tmp, 0, 4);
  }

  @Override
  public void update(byte[] foo, int s, int l) {
    mac.update(foo, s, l);
  }

  @Override
  public void doFinal(byte[] buf, int offset) {
    int full = mac.getMacSize();
    if (outputSize == full) {
      mac.doFinal(buf, offset);
      return;
    }
    byte[] tmp = new byte[full];
    mac.doFinal(tmp, 0);
    System.arraycopy(tmp, 0, buf, offset, outputSize);
  }
}
