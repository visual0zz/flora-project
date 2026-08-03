package com.flora.comm.ssh.crypto;

/** hmac-sha256-2@ssh.com MAC 适配 */
public class HmacSha2562SshCom extends FloraMac {
  public HmacSha2562SshCom() {
    super("hmac-sha256-2@ssh.com", 32, 32, "HmacSHA256", false);
  }
}
