package com.flora.comm.ssh.crypto;

/** ssh-ed25519 签名适配 */
public class SignatureEd25519 extends FloraSignatureEdDsa {
  @Override
  String getName() {
    return "ssh-ed25519";
  }

  @Override
  String getAlgo() {
    return "Ed25519";
  }

  @Override
  int getKeylen() {
    return 32;
  }
}
