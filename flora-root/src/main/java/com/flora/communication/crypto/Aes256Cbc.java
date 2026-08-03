package com.flora.communication.crypto;

/** AES-256-CBC 密文适配 */
public class Aes256Cbc extends FloraCipher {
  public Aes256Cbc() {
    super("AES", "CBC", 32, 16, 16, 16, true);
  }
}
