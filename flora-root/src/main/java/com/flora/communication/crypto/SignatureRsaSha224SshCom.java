package com.flora.communication.crypto;

/** ssh-rsa-sha224@ssh.com 签名适配 */
public class SignatureRsaSha224SshCom extends FloraSignatureRsa {
  @Override
  String getName() {
    return "ssh-rsa-sha224@ssh.com";
  }
}
