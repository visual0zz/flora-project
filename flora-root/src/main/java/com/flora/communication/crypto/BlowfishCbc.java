package com.flora.communication.crypto;

/** Blowfish-CBC 密文适配（引擎块 8，对外块 16 与 JSch 对齐） */
public class BlowfishCbc extends FloraCipher {
  public BlowfishCbc() {
    super("Blowfish", "CBC", 16, 8, 16, 8, true);
  }
}
