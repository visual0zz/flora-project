package com.flora.comm.ssh.crypto;

/** hmac-sha1-96 MAC 适配 */
public class HmacSha196 extends FloraMac {
  public HmacSha196() {
    super("hmac-sha1-96", 20, 12, "HmacSHA1", false);
  }
}
