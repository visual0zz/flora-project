package com.flora.comm.ssh.crypto;

/** ssh-rsa 签名适配 */
public class SignatureRsa extends FloraSignatureRsa {
  @Override
  String getName() {
    return "ssh-rsa";
  }
}
