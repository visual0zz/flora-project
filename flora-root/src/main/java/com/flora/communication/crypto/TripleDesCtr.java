package com.flora.communication.crypto;

/** 3DES-CTR 密文适配 */
public class TripleDesCtr extends FloraCipher {
  public TripleDesCtr() {
    super("DESede", "CTR", 24, 8, 8, 8, false);
  }
}
