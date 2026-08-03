package com.flora.comm.ssh.crypto;

/** AES-192-CBC 密文适配 */
public class Aes192Cbc extends FloraCipher {
  public Aes192Cbc() {
    super("AES", "CBC", 24, 16, 16, 16, true);
  }
}
