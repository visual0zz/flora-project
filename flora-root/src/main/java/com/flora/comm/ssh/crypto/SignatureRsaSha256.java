package com.flora.comm.ssh.crypto;

/** rsa-sha2-256 签名适配 */
public class SignatureRsaSha256 extends FloraSignatureRsa {
  @Override
  String getName() {
    return "rsa-sha2-256";
  }
}
