package com.flora.crypto.core.impl;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * RIPEMD-160 已知答案测试（RFC 2286 及标准测试向量）。
 */
class Ripemd160VectorTest {

  private static byte[] digest(String s) throws Exception {
    Ripemd160Digest d = new Ripemd160Digest();
    byte[] data = s.getBytes(StandardCharsets.UTF_8);
    d.update(data, 0, data.length);
    byte[] out = new byte[d.getDigestSize()];
    d.doFinal(out, 0);
    return out;
  }

  private static void assertDigest(String expectedHex, String input) throws Exception {
    byte[] expected = hex(expectedHex);
    assertArrayEquals(expected, digest(input), "RIPEMD-160(\"" + input + "\")");
  }

  @Test
  void empty() throws Exception {
    assertDigest("9c1185a5c5e9fc54612808977ee8f548b2258d31", "");
  }

  @Test
  void singleA() throws Exception {
    assertDigest("0bdc9d2d256b3ee9daae347be6f4dc835a467ffe", "a");
  }

  @Test
  void abc() throws Exception {
    assertDigest("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc", "abc");
  }

  @Test
  void messageDigest() throws Exception {
    assertDigest("5d0689ef49d2fae572b881b123a85ffa21595f36", "message digest");
  }

  @Test
  void alphabet() throws Exception {
    assertDigest("f71c27109c692c1b56bbdceb5b9d2865b3708dbc",
        "abcdefghijklmnopqrstuvwxyz");
  }

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }
}
