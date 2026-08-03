package com.flora.communication.crypto;

/** AES-128-GCM AEAD 密文适配。 */
public class Aes128Gcm extends FloraAeadCipher {
  public Aes128Gcm() {
    super(16);
  }
}
