package com.flora.communication.crypto;

/** ssh-rsa-sha512@ssh.com 签名适配 */
public class SignatureRsaSha512SshCom extends FloraSignatureRsa {
  @Override
  String getName() {
    return "ssh-rsa-sha512@ssh.com";
  }
}
