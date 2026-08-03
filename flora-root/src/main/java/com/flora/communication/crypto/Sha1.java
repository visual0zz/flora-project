package com.flora.communication.crypto;

/** SHA-1 摘要适配 */
public class Sha1 extends FloraDigest {
  public Sha1() {
    super("SHA-1", 20, "SHA1");
  }
}
