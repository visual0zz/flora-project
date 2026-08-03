package com.flora.comm.ssh.crypto;

/** hmac-sha256@ssh.com MAC 适配（短密钥 16 字节） */
public class HmacSha256SshCom extends FloraMac {
  public HmacSha256SshCom() {
    super("hmac-sha256@ssh.com", 16, 32, "HmacSHA256", false);
  }
}
