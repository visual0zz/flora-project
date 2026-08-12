package com.flora.root.crypto.core.param;

import com.flora.root.crypto.core.interfaces.material.param.DerivationParameter;

import com.flora.root.java.CheckUtil;

/**
 * bcrypt 口令派生参数：口令、盐与轮数（OpenSSH 加密私钥的 bcrypt KDF）。
 */
public final class BCryptParameters implements DerivationParameter {

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
