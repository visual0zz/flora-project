package com.flora.crypto.core.engine;

import com.flora.crypto.core.Argon2Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Argon2 已知答案测试（RFC 9106 §5 测试向量，v1.3，m=32, t=3, p=4）。
 */
class Argon2VectorTest {

  private static byte[] fill(int value, int len) {
    byte[] out = new byte[len];
    java.util.Arrays.fill(out, (byte) value);
    return out;
  }

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static byte[] argon2(int type, String expectedHex) {
    Argon2 argon2 = new Argon2();
    argon2.init(new Argon2Parameters(fill(0x01, 32), fill(0x02, 16), fill(0x03, 8),
        fill(0x04, 12), 3, 32, 4, type));
    byte[] out = new byte[32];
    argon2.generateBytes(out, 0, 32);
    return out;
  }

  @Test
  void argon2d() {
    assertArrayEquals(hex("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb"),
        argon2(Argon2Parameters.ARGON2d, ""));
  }

  @Test
  void argon2i() {
    assertArrayEquals(hex("c814d9d1dc7f37aa13f0d77f2494bda1c8de6b016dd388d29952a4c4672b6ce8"),
        argon2(Argon2Parameters.ARGON2i, ""));
  }

  @Test
  void argon2id() {
    assertArrayEquals(hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"),
        argon2(Argon2Parameters.ARGON2id, ""));
  }
}
