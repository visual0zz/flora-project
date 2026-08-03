package com.flora.communication.crypto;

/** AES-256-GCM AEAD 密文适配。 */
public class Aes256Gcm extends FloraAeadCipher {
  public Aes256Gcm() {
    super(32);
  }
}
