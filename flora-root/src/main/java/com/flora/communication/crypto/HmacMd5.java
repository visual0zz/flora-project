package com.flora.communication.crypto;

/** hmac-md5 MAC 适配 */
public class HmacMd5 extends FloraMac {
  public HmacMd5() {
    super("hmac-md5", 16, 16, "HmacMD5", false);
  }
}
