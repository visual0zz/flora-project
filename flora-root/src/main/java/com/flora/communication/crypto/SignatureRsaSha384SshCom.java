package com.flora.communication.crypto;

/** ssh-rsa-sha384@ssh.com 签名适配 */
public class SignatureRsaSha384SshCom extends FloraSignatureRsa {
  @Override
  String getName() {
    return "ssh-rsa-sha384@ssh.com";
  }
}
