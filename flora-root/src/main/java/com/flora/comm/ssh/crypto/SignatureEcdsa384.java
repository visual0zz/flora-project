package com.flora.comm.ssh.crypto;

/** ecdsa-sha2-nistp384 签名适配 */
public class SignatureEcdsa384 extends FloraSignatureEcdsa {
  @Override
  String getName() {
    return "ecdsa-sha2-nistp384";
  }
}
