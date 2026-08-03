package com.flora.communication.crypto;

/** SHA-512 摘要适配 */
public class Sha512 extends FloraDigest {
  public Sha512() {
    super("SHA-512", 64, "SHA512");
  }
}
