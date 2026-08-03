package com.flora.comm.ssh.crypto;

/** hmac-sha1-etm MAC 适配 */
public class HmacSha1Etm extends FloraMac {
  public HmacSha1Etm() {
    super("hmac-sha1-etm@openssh.com", 20, 20, "HmacSHA1", true);
  }
}
