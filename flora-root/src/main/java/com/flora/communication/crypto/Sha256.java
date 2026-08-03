package com.flora.communication.crypto;

/** SHA-256 摘要适配 */
public class Sha256 extends FloraDigest {
  public Sha256() {
    super("SHA-256", 32, "SHA256");
  }
}
