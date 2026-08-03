package com.flora.communication.crypto;

/** hmac-sha384@ssh.com MAC 适配 */
public class HmacSha384SshCom extends FloraMac {
  public HmacSha384SshCom() {
    super("hmac-sha384@ssh.com", 48, 48, "HmacSHA384", false);
  }
}
