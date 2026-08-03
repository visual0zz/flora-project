package com.flora.crypto.core.engine;

import com.flora.crypto.core.KeyParameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Poly1305 已知答案测试（RFC 8439 §2.5.2 测试向量）。
 */
class Poly1305VectorTest {

  private static byte[] hex(String s) {
    String[] parts = s.split(":");
    byte[] out = new byte[parts.length];
    for (int i = 0; i < parts.length; i++) {
      out[i] = (byte) Integer.parseInt(parts[i], 16);
    }
    return out;
  }

  @Test
  void rfc8439TestVector() throws Exception {
    // RFC 8439 §2.5.2
    byte[] key = hex("85:d6:be:78:57:55:6d:33:7f:44:52:fe:42:d5:06:a8"
        + ":01:03:80:8a:fb:0d:b2:fd:4a:bf:f6:af:41:49:f5:1b");
    byte[] msg = "Cryptographic Forum Research Group".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] expected = hex("a8:06:1d:c1:30:51:36:c6:c2:2b:8b:af:0c:01:27:a9");

    Poly1305Mac mac = new Poly1305Mac();
    mac.init(new KeyParameter(key));
    mac.update(msg, 0, msg.length);
    byte[] out = new byte[16];
    mac.doFinal(out, 0);
    assertArrayEquals(expected, out);
  }
}
