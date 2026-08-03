package com.flora.comm.ssh.crypto;

/** Blowfish-CTR 密文适配 */
public class BlowfishCtr extends FloraCipher {
  public BlowfishCtr() {
    super("Blowfish", "CTR", 16, 8, 16, 8, false);
  }
}
