package com.flora.comm.ssh.crypto;

/** hmac-sha2-512-etm MAC 适配 */
public class HmacSha512Etm extends FloraMac {
  public HmacSha512Etm() {
    super("hmac-sha2-512-etm@openssh.com", 64, 64, "HmacSHA512", true);
  }
}
