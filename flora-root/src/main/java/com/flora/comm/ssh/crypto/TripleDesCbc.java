package com.flora.comm.ssh.crypto;

/** 3DES-CBC 密文适配 */
public class TripleDesCbc extends FloraCipher {
  public TripleDesCbc() {
    super("DESede", "CBC", 24, 8, 8, 8, true);
  }
}
