package com.flora.communication.crypto;

/** hmac-sha224@ssh.com MAC 适配 */
public class HmacSha224SshCom extends FloraMac {
  public HmacSha224SshCom() {
    super("hmac-sha224@ssh.com", 28, 28, "HmacSHA224", false);
  }
}
