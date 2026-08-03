package com.flora.communication.crypto;

/** hmac-md5-96-etm MAC 适配 */
public class HmacMd596Etm extends FloraMac {
  public HmacMd596Etm() {
    super("hmac-md5-96-etm@openssh.com", 16, 12, "HmacMD5", true);
  }
}
