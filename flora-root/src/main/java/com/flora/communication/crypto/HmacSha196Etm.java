package com.flora.communication.crypto;

/** hmac-sha1-96-etm MAC 适配 */
public class HmacSha196Etm extends FloraMac {
  public HmacSha196Etm() {
    super("hmac-sha1-96-etm@openssh.com", 20, 12, "HmacSHA1", true);
  }
}
