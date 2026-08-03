package com.flora.comm.ssh.crypto;

/** hmac-sha1 MAC 适配 */
public class HmacSha1 extends FloraMac {
  public HmacSha1() {
    super("hmac-sha1", 20, 20, "HmacSHA1", false);
  }
}
