package com.flora.communication.crypto;

/** ecdsa-sha2-nistp256 签名适配 */
public class SignatureEcdsa256 extends FloraSignatureEcdsa {
  @Override
  String getName() {
    return "ecdsa-sha2-nistp256";
  }
}
