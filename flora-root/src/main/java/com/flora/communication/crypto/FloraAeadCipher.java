package com.flora.communication.crypto;

import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.ParametersWithIV;
import com.flora.crypto.core.engine.JdkBlockCipher;
import com.flora.crypto.core.mode.GCMBlockCipher;
import javax.crypto.AEADBadTagException;

/**
 * AEAD（AES-GCM）密文适配基类。
 * <p>把 flora 自研 {@link GCMBlockCipher}（包裹 {@link JdkBlockCipher.of("AES")}）接入 JSch
 * 的 {@code Cipher} 契约。SSH 对 GCM 的用法：每次数据包先 {@code updateAAD} 喂 4 字节长度，
 * 再 {@code doFinal} 处理余下数据并附带/校验 16 字节认证标签；每包结束后将 12 字节 IV 中
 * 偏移 4 处的 64 位计数器 +1 并重新初始化（对应 RFC 5647 隐式 IV 方案）。</p>
 */
abstract class FloraAeadCipher implements com.flora.communication.Cipher {

  private static final int ivsize = 16;
  private static final int tagsize = 16;

  private final int keySize;

  private GCMBlockCipher gcm;
  private byte[] keyBytes;
  private byte[] ivBytes;
  private boolean encrypting;
  private long initCounter;

  FloraAeadCipher(int keySize) {
    this.keySize = keySize;
  }

  @Override
  public int getIVSize() {
    return ivsize;
  }

  @Override
  public int getBlockSize() {
    return keySize;
  }

  @Override
  public int getTagSize() {
    return tagsize;
  }

  @Override
  public void init(int mode, byte[] key, byte[] iv) throws Exception {
    if (iv.length > 12) {
      byte[] tmp = new byte[12];
      System.arraycopy(iv, 0, tmp, 0, tmp.length);
      iv = tmp;
    }
    if (key.length > keySize) {
      byte[] tmp = new byte[keySize];
      System.arraycopy(key, 0, tmp, 0, tmp.length);
      key = tmp;
    }
    this.encrypting = mode == com.flora.communication.Cipher.ENCRYPT_MODE;
    // 防御性克隆：doFinal 内部递增 IV 计数器，不得修改调用方数组
    this.keyBytes = key.clone();
    this.ivBytes = iv.clone();
    this.initCounter = readLong(iv, 4);
    initGcm();
  }

  private void initGcm() {
    gcm = new GCMBlockCipher(JdkBlockCipher.of("AES"), tagsize * 8);
    gcm.init(encrypting, new ParametersWithIV(new KeyParameter(keyBytes), ivBytes));
  }

  @Override
  public void update(byte[] foo, int s1, int len, byte[] bar, int s2) throws Exception {
    gcm.processBytes(foo, s1, len, bar, s2);
  }

  @Override
  public void updateAAD(byte[] foo, int s1, int len) throws Exception {
    gcm.processAADBytes(foo, s1, len);
  }

  @Override
  public void doFinal(byte[] foo, int s1, int len, byte[] bar, int s2) throws Exception {
    gcm.processBytes(foo, s1, len, bar, s2);
    try {
      gcm.doFinal(bar, s2);
    } catch (IllegalStateException e) {
      throw new AEADBadTagException(e.getMessage());
    }
    long newCounter = readLong(ivBytes, 4) + 1;
    if (newCounter == initCounter) {
      throw new IllegalStateException("GCM IV would be reused");
    }
    writeLong(ivBytes, 4, newCounter);
    initGcm();
  }

  @Override
  public boolean isCBC() {
    return false;
  }

  @Override
  public boolean isAEAD() {
    return true;
  }

  private static long readLong(byte[] b, int off) {
    return ((long) (b[off] & 0xff) << 56) | ((long) (b[off + 1] & 0xff) << 48)
        | ((long) (b[off + 2] & 0xff) << 40) | ((long) (b[off + 3] & 0xff) << 32)
        | ((long) (b[off + 4] & 0xff) << 24) | ((long) (b[off + 5] & 0xff) << 16)
        | ((long) (b[off + 6] & 0xff) << 8) | (b[off + 7] & 0xff);
  }

  private static void writeLong(byte[] b, int off, long v) {
    b[off] = (byte) (v >>> 56);
    b[off + 1] = (byte) (v >>> 48);
    b[off + 2] = (byte) (v >>> 40);
    b[off + 3] = (byte) (v >>> 32);
    b[off + 4] = (byte) (v >>> 24);
    b[off + 5] = (byte) (v >>> 16);
    b[off + 6] = (byte) (v >>> 8);
    b[off + 7] = (byte) v;
  }
}
