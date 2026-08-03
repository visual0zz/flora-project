package com.flora.comm.ssh.crypto;

import java.security.spec.NamedParameterSpec;

/** ML-KEM-1024 密钥封装适配。 */
public class Mlkem1024 extends FloraKem {
  private static final byte[] pkMlKem1024 = {(byte) 0x60, (byte) 0x86, (byte) 0x48, (byte) 0x01,
      (byte) 0x65, (byte) 0x03, (byte) 0x04, (byte) 0x04, (byte) 0x03};

  public Mlkem1024() {
    params = NamedParameterSpec.ML_KEM_1024;
    algorithmIdentifier = pkMlKem1024;
    publicKeyLen = 1568;
  }
}
