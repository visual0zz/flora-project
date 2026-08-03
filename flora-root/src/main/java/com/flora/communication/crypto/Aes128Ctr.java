package com.flora.communication.crypto;

/** AES-128-CTR 密文适配 */
public class Aes128Ctr extends FloraCipher {
  public Aes128Ctr() {
    super("AES", "CTR", 16, 16, 16, 16, false);
  }
}
