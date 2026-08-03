package com.flora.communication.crypto;

import java.security.spec.NamedParameterSpec;

/** ML-KEM-768 密钥封装适配。 */
public class Mlkem768 extends FloraKem {
  private static final byte[] pkMlKem768 = {(byte) 0x60, (byte) 0x86, (byte) 0x48, (byte) 0x01,
      (byte) 0x65, (byte) 0x03, (byte) 0x04, (byte) 0x04, (byte) 0x02};

  public Mlkem768() {
    params = NamedParameterSpec.ML_KEM_768;
    algorithmIdentifier = pkMlKem768;
    publicKeyLen = 1184;
  }
}
