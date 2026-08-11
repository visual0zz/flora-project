package com.flora.crypto.newcore.impl;

import com.flora.crypto.newcore.param.Argon2Parameters;
import com.flora.crypto.newcore.param.ScryptParameters;
import java.lang.SuppressWarnings;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * newcore KDF 已知答案测试（对齐 core 测试向量：RFC 7914 / RFC 9106）。
 */
@SuppressWarnings("osmetes:secret")
class NewCoreKdfVectorTest {

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  // ===== scrypt (RFC 7914 §12) =====

  private static byte[] scrypt(String pass, String salt, int n, int r, int p, int dkLen) {
    Scrypt scrypt = new Scrypt();
    scrypt.init(new ScryptParameters(pass.getBytes(StandardCharsets.UTF_8),
        salt.getBytes(StandardCharsets.UTF_8), n, r, p));
    byte[] out = new byte[dkLen];
    scrypt.generateBytes(out, 0, dkLen);
    return out;
  }

  @Test
  void rfc7914Empty() {
    byte[] expected = hex("77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442"
        + "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906");
    assertArrayEquals(expected, scrypt("", "", 16, 1, 1, 64));
  }

  @Test
  void rfc7914PasswordNaCl() {
    byte[] expected = hex("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162"
        + "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640");
    assertArrayEquals(expected, scrypt("password", "NaCl", 1024, 8, 16, 64));
  }

  // ===== Argon2 (RFC 9106 §5, v1.3, m=32, t=3, p=4) =====

  private static byte[] fill(int value, int len) {
    byte[] out = new byte[len];
    java.util.Arrays.fill(out, (byte) value);
    return out;
  }

  private static byte[] argon2(int type, int dkLen) {
    Argon2 argon2 = new Argon2();
    argon2.init(new Argon2Parameters(fill(0x01, 32), fill(0x02, 16), fill(0x03, 8),
        fill(0x04, 12), 3, 32, 4, type));
    byte[] out = new byte[dkLen];
    argon2.generateBytes(out, 0, dkLen);
    return out;
  }

  @Test
  void argon2d() {
    assertArrayEquals(hex("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb"),
        argon2(Argon2Parameters.ARGON2d, 32));
  }

  @Test
  void argon2i() {
    assertArrayEquals(hex("c814d9d1dc7f37aa13f0d77f2494bda1c8de6b016dd388d29952a4c4672b6ce8"),
        argon2(Argon2Parameters.ARGON2i, 32));
  }

  @Test
  void argon2id() {
    assertArrayEquals(hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"),
        argon2(Argon2Parameters.ARGON2id, 32));
  }

  // ===== PBKDF2 (RFC 6070, HMAC-SHA1) =====

  @Test
  void pbkdf2Rfc6070() {
    Pbkdf2DerivationFunction pbkdf2 = new Pbkdf2DerivationFunction(
        new HMac(com.flora.crypto.newcore.bridge.JdkDigest.of("SHA-1")));
    pbkdf2.init(new com.flora.crypto.newcore.param.Pbkdf2Parameters(
        "password".getBytes(StandardCharsets.US_ASCII),
        "salt".getBytes(StandardCharsets.US_ASCII), 4096));
    byte[] out = new byte[20];
    pbkdf2.generateBytes(out, 0, 20);
    assertArrayEquals(hex("4b007901b765489abead49d926f721d065a429c1"), out);
  }

  // ===== HKDF (RFC 5869, HMAC-SHA256) =====

  @Test
  void hkdfRfc5869() {
    HkdfDerivationFunction hkdf = new HkdfDerivationFunction(
        new HMac(com.flora.crypto.newcore.bridge.JdkDigest.of("SHA-256")));
    hkdf.init(new com.flora.crypto.newcore.param.HkdfParameters(
        hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"),
        hex("f0f1f2f3f4f5f6f7f8f9")));
    byte[] out = new byte[42];
    hkdf.generateBytes(out, 0, 42);
    assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf3"
        + "4007208d5b887185865"), out);
  }

  // ===== BCrypt（与 core 实现同参数对齐） =====

  @Test
  void bcryptMatchesCore() {
    byte[] password = "password".getBytes(StandardCharsets.UTF_8);
    byte[] salt = hex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
    int rounds = 6;
    int dkLen = 24;

    BCrypt bcrypt = new BCrypt();
    bcrypt.init(new com.flora.crypto.newcore.param.BCryptParameters(password, salt, rounds));
    byte[] out = new byte[dkLen];
    bcrypt.generateBytes(out, 0, dkLen);

    byte[] expected = new byte[dkLen];
    new com.flora.crypto.core.impl.BCrypt().pbkdf(password, salt, rounds, expected);
    assertArrayEquals(expected, out);
  }
}
