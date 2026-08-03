package com.flora.communication.crypto;

/** ssh-rsa-sha256@ssh.com 签名适配 */
public class SignatureRsaSha256SshCom extends FloraSignatureRsa {
  @Override
  String getName() {
    return "ssh-rsa-sha256@ssh.com";
  }
}
