package com.flora.communication.crypto;

/** hmac-sha512@ssh.com MAC 适配 */
public class HmacSha512SshCom extends FloraMac {
  public HmacSha512SshCom() {
    super("hmac-sha512@ssh.com", 64, 64, "HmacSHA512", false);
  }
}
