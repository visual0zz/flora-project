package com.flora.comm.ssh.crypto;

/** hmac-sha2-256 MAC 适配 */
public class HmacSha256 extends FloraMac {
  public HmacSha256() {
    super("hmac-sha2-256", 32, 32, "HmacSHA256", false);
  }
}
