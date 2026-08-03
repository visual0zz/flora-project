package com.flora.crypto.core.engine;

/**
 * ChaCha20 流密码（RFC 8439 §2）。
 * <p>32 字节密钥 + 12 字节 nonce + 32 位计数器，10 轮双轮结构生成 64 字节密钥流。
 * 提供流式加解密原语，供 {@link ChaCha20Poly1305} 等组合层使用。</p>
 */
public final class ChaCha20Engine {

  private static final int[] CONSTANTS = {0x61707865, 0x3320646e, 0x79622d32, 0x6b206574};

  private final int[] state = new int[16];
  private final int[] working = new int[16];
  private final byte[] keyStream = new byte[64];
  private int keyStreamPos = 64;

  public ChaCha20Engine() {
    // 无状态构造，init 后使用
  }

  /** 初始化：密钥、12 字节 nonce、起始块计数器。 */
  public void init(byte[] key, byte[] nonce, long counter) {
    if (key == null || key.length != 32) {
      throw new IllegalArgumentException("ChaCha20 密钥必须为 32 字节");
    }
    if (nonce == null || nonce.length != 12) {
      throw new IllegalArgumentException("ChaCha20 nonce 必须为 12 字节");
    }
    System.arraycopy(CONSTANTS, 0, state, 0, 4);
    for (int i = 0; i < 8; i++) {
      state[4 + i] = readWord(key, i * 4);
    }
    // RFC 8439 §2.3 布局：counter 占 1 字（state[12]），nonce 占 3 字（state[13..15]）
    state[12] = (int) counter;
    state[13] = readWord(nonce, 0);
    state[14] = readWord(nonce, 4);
    state[15] = readWord(nonce, 8);
    keyStreamPos = 64;
  }

  /**
   * 流式加解密（XOR 密钥流）。
   *
   * @return 处理字节数（恒等于 {@code len}）
   */
  public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
    for (int i = 0; i < len; i++) {
      if (keyStreamPos == 64) {
        generateBlock();
        keyStreamPos = 0;
      }
      out[outOff + i] = (byte) (in[inOff + i] ^ keyStream[keyStreamPos++]);
    }
    return len;
  }

  private void generateBlock() {
    System.arraycopy(state, 0, working, 0, 16);
    for (int i = 0; i < 10; i++) {
      quarterRound(working, 0, 4, 8, 12);
      quarterRound(working, 1, 5, 9, 13);
      quarterRound(working, 2, 6, 10, 14);
      quarterRound(working, 3, 7, 11, 15);
      quarterRound(working, 0, 5, 10, 15);
      quarterRound(working, 1, 6, 11, 12);
      quarterRound(working, 2, 7, 8, 13);
      quarterRound(working, 3, 4, 9, 14);
    }
    for (int i = 0; i < 16; i++) {
      writeWord(state[i] + working[i], keyStream, i * 4);
    }
    state[12]++; // 32 位块计数器递增
  }

  private static void quarterRound(int[] x, int a, int b, int c, int d) {
    x[a] += x[b];
    x[d] = rotl(x[d] ^ x[a], 16);
    x[c] += x[d];
    x[b] = rotl(x[b] ^ x[c], 12);
    x[a] += x[b];
    x[d] = rotl(x[d] ^ x[a], 8);
    x[c] += x[d];
    x[b] = rotl(x[b] ^ x[c], 7);
  }

  private static int rotl(int x, int n) {
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
}
