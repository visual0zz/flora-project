package com.flora.comm.ssh.crypto;

/** hmac-sha2-512 MAC 适配 */
public class HmacSha512 extends FloraMac {
  public HmacSha512() {
    super("hmac-sha2-512", 64, 64, "HmacSHA512", false);
  }
}
