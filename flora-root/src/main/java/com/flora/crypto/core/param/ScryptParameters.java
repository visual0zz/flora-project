package com.flora.crypto.core.param;

import com.flora.crypto.core.interfaces.DerivationParameters;
import com.flora.java.CheckUtil;

/**
 * scrypt 口令派生参数（RFC 7914）：口令、盐、CPU 成本 N（2 的幂）、块大小 r、并行度 p。
 */
public final class ScryptParameters implements DerivationParameters {

  private final byte[] password;
  private final byte[] salt;
  private final int n;
  private final int r;
  private final int p;

  public ScryptParameters(byte[] password, byte[] salt, int n, int r, int p) {
    CheckUtil.notNull(password, "口令不能为空");
    CheckUtil.notNull(salt, "盐不能为空");
    CheckUtil.mustTrue(n > 1 && (n & (n - 1)) == 0, "N 须为大于 1 的 2 的幂");
    CheckUtil.mustTrue(r > 0, "r 须为正");
    CheckUtil.mustTrue(p > 0, "p 须为正");
    this.password = password.clone();
    this.salt = salt.clone();
    this.n = n;
    this.r = r;
    this.p = p;
  }

  public byte[] getPassword() {
    return password.clone();
  }

  public byte[] getSalt() {
    return salt.clone();
  }

  public int getN() {
    return n;
  }

  public int getR() {
    return r;
  }

  public int getP() {
    return p;
  }
}
