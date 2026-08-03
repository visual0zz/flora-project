package com.flora.comm.ssh.crypto;

/** hmac-md5-96 MAC 适配 */
public class HmacMd596 extends FloraMac {
  public HmacMd596() {
    super("hmac-md5-96", 16, 12, "HmacMD5", false);
  }
}
