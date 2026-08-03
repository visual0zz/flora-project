package com.flora.communication.crypto;

/** ecdsa-sha2-nistp521 签名适配 */
public class SignatureEcdsa521 extends FloraSignatureEcdsa {
  @Override
  String getName() {
    return "ecdsa-sha2-nistp521";
  }
}
