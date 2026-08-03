package com.flora.communication.crypto;

/** hmac-md5-etm MAC 适配 */
public class HmacMd5Etm extends FloraMac {
  public HmacMd5Etm() {
    super("hmac-md5-etm@openssh.com", 16, 16, "HmacMD5", true);
  }
}
