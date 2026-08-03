package com.flora.comm.ssh.crypto;

/** AES-256-CTR 密文适配 */
public class Aes256Ctr extends FloraCipher {
  public Aes256Ctr() {
    super("AES", "CTR", 32, 16, 16, 16, false);
  }
}
