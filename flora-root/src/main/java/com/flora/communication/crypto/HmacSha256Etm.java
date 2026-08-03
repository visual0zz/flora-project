package com.flora.communication.crypto;

/** hmac-sha2-256-etm MAC 适配 */
public class HmacSha256Etm extends FloraMac {
  public HmacSha256Etm() {
    super("hmac-sha2-256-etm@openssh.com", 32, 32, "HmacSHA256", true);
  }
}
