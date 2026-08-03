package com.flora.comm.ssh.crypto;

/** AES-192-CTR 密文适配 */
public class Aes192Ctr extends FloraCipher {
  public Aes192Ctr() {
    super("AES", "CTR", 24, 16, 16, 16, false);
  }
}
