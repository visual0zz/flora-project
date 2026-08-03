package com.flora.communication.crypto;

/** ssh-ed448 签名适配 */
public class SignatureEd448 extends FloraSignatureEdDsa {
  @Override
  String getName() {
    return "ssh-ed448";
  }

  @Override
  String getAlgo() {
    return "Ed448";
  }

  @Override
  int getKeylen() {
    return 57;
  }
}
