package com.flora.crypto.core;

import com.flora.crypto.core.interfaces.DerivationParameters;
import com.flora.java.CheckUtil;

/**
 * bcrypt 口令派生参数：口令、盐与轮数（OpenSSH 加密私钥的 bcrypt KDF）。
 */
public final class BCryptParameters implements DerivationParameters {

  private final byte[] password;
  private final byte[] salt;
  private final int rounds;

  public BCryptParameters(byte[] password, byte[] salt, int rounds) {
    CheckUtil.notNull(password, "口令不能为空");
    CheckUtil.notNull(salt, "盐不能为空");
    CheckUtil.mustTrue(rounds > 0, "轮数须为正");
    this.password = password.clone();
    this.salt = salt.clone();
    this.rounds = rounds;
  }

  public byte[] getPassword() {
    return password.clone();
  }

  public byte[] getSalt() {
    return salt.clone();
  }

  public int getRounds() {
    return rounds;
  }
}
