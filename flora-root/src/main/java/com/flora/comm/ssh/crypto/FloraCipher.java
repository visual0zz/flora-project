package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.Cipher;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.ParametersWithIV;
import com.flora.crypto.core.engine.JdkBlockCipher;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.mode.CBCBlockCipher;
import com.flora.crypto.core.mode.SICBlockCipher;

/**
 * CBC/CTR 分组密码适配基类。
 * <p>把 flora 的 {@link JdkBlockCipher} 裸引擎 + 自研模式层（{@link CBCBlockCipher} /
 * {@link SICBlockCipher}）接入 JSch 的 {@code Cipher} 契约。SSH 数据流在 {@code Session}
 * 中保证块对齐（padding 后整包对齐，接收侧有 {@code need % bsize == 0} 检查），
 * 因此按块调用 {@code processBlock} 即等价于 JDK 变换。</p>
 */
abstract class FloraCipher implements Cipher {

  private final String rawAlgorithm;
  private final String modeName;
  private final int keySize;
  private final int ivSize;
  private final int blockSize;
  private final int engineBlockSize;
  private final boolean cbc;

  private BlockCipher engine;

  FloraCipher(String rawAlgorithm, String modeName, int keySize, int ivSize, int blockSize,
      int engineBlockSize, boolean cbc) {
    this.rawAlgorithm = rawAlgorithm;
    this.modeName = modeName;
    this.keySize = keySize;
    this.ivSize = ivSize;
    this.blockSize = blockSize;
    this.engineBlockSize = engineBlockSize;
    this.cbc = cbc;
  }

  @Override
  public int getIVSize() {
    return ivSize;
  }

  @Override
  public int getBlockSize() {
    return blockSize;
  }

  @Override
  public void init(int mode, byte[] key, byte[] iv) throws Exception {
    byte[] k = truncate(key, keySize);
    byte[] v = truncate(iv, ivSize);
    BlockCipher raw = JdkBlockCipher.of(rawAlgorithm);
    engine = modeName.equals("CBC") ? new CBCBlockCipher(raw) : new SICBlockCipher(raw);
    engine.init(mode == Cipher.ENCRYPT_MODE,
        new ParametersWithIV(new KeyParameter(k), v));
  }

  @Override
  public void update(byte[] foo, int s1, int len, byte[] bar, int s2) throws Exception {
    // Session 保证 len 为引擎块大小整数倍
    for (int off = 0; off < len; off += engineBlockSize) {
      engine.processBlock(foo, s1 + off, bar, s2 + off);
    }
  }

  @Override
  public boolean isCBC() {
    return cbc;
  }

  private static byte[] truncate(byte[] in, int max) {
    if (in.length <= max) {
      return in;
    }
    byte[] tmp = new byte[max];
    System.arraycopy(in, 0, tmp, 0, max);
    return tmp;
  }
}
