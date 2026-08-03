package com.flora.communication.crypto;

/** MD5 摘要适配 */
public class Md5 extends FloraDigest {
  public Md5() {
    super("MD5", 16, "MD5");
  }
}
