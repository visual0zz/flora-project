package com.flora.crypto.core.param;

import com.flora.crypto.core.interfaces.material.param.DerivationParameter;

import com.flora.java.CheckUtil;

/**
 * Argon2 口令派生参数（RFC 9106）：口令、盐、可选密钥/关联数据、迭代次数 t、
 * 内存大小 m（KiB）、并行度 p、版本与类型（Argon2d/i/id）。
 */
public final class Argon2Parameters implements DerivationParameter {

  public static final int ARGON2d = 0;
  public static final int ARGON2i = 1;
  public static final int ARGON2id = 2;

  public static final int VERSION = 0x13;

  private final byte[] password;
  private final byte[] salt;
  private final byte[] secret;
  private final byte[] additional;
  private final int iterations;
  private final int memoryKib;
  private final int parallelism;
  private final int type;

  public Argon2Parameters(byte[] password, byte[] salt, byte[] secret, byte[] additional,
      int iterations, int memoryKib, int parallelism, int type) {
    CheckUtil.notNull(password, "口令不能为空");
    CheckUtil.notNull(salt, "盐不能为空");
    CheckUtil.mustTrue(iterations > 0, "t 须为正");
    CheckUtil.mustTrue(memoryKib >= 8 * parallelism, "m 须不小于 8p");
    CheckUtil.mustTrue(parallelism > 0, "p 须为正");
    CheckUtil.mustTrue(type >= ARGON2d && type <= ARGON2id, "非法 Argon2 类型");
    this.password = password.clone();
    this.salt = salt.clone();
    this.secret = secret != null ? secret.clone() : new byte[0];
    this.additional = additional != null ? additional.clone() : new byte[0];
    this.iterations = iterations;
    this.memoryKib = memoryKib;
    this.parallelism = parallelism;
    this.type = type;
  }

  public byte[] getPassword() {
    return password.clone();
  }

  public byte[] getSalt() {
    return salt.clone();
  }

  public byte[] getSecret() {
    return secret.clone();
  }

  public byte[] getAdditional() {
    return additional.clone();
  }

  public int getIterations() {
    return iterations;
  }

  public int getMemoryKib() {
    return memoryKib;
  }

  public int getParallelism() {
    return parallelism;
  }

  public int getType() {
    return type;
  }
}
