package com.flora.crypto.core.impl;

import com.flora.crypto.core.param.ScryptParameters;
import java.lang.SuppressWarnings;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * scrypt 已知答案测试（RFC 7914 §12 测试向量）。
 */
@SuppressWarnings("osmetes:secret")
class ScryptVectorTest {

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

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
    // RFC 7914 §12，第一个向量：N=16, r=1, p=1
    byte[] expected = hex("77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442"
        + "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906");
    assertArrayEquals(expected, scrypt("", "", 16, 1, 1, 64));
  }

  @Test
  void rfc7914PasswordNaCl() {
    // RFC 7914 §12，第二个向量：N=1024, r=8, p=16
    byte[] expected = hex("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162"
        + "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640");
    assertArrayEquals(expected, scrypt("password", "NaCl", 1024, 8, 16, 64));
  }
}
