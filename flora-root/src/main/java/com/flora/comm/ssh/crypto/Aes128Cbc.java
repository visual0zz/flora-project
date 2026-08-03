package com.flora.comm.ssh.crypto;

/** AES-128-CBC 密文适配 */
public class Aes128Cbc extends FloraCipher {
  public Aes128Cbc() {
    super("AES", "CBC", 16, 16, 16, 16, true);
  }
}
