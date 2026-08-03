package com.flora.comm.ssh.crypto;

/** rsa-sha2-512 签名适配 */
public class SignatureRsaSha512 extends FloraSignatureRsa {
  @Override
  String getName() {
    return "rsa-sha2-512";
  }
}
