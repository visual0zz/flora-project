package com.flora.crypto.core.impl;

import com.flora.crypto.core.bridge.JdkDigest;
import com.flora.crypto.core.param.KeyParameter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * 自研 HMAC 已知答案测试：RFC 4231 向量 + 空密钥向量（JDK Mac 不支持空密钥）。
 */
class HMacVectorTest {

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static byte[] hmacSha256(byte[] key, byte[] data) {
    HMac mac = new HMac(JdkDigest.of("SHA-256"));
    mac.init(new KeyParameter(key));
    mac.update(data, 0, data.length);
    byte[] out = new byte[mac.getMacSize()];
    mac.doFinal(out, 0);
    return out;
  }

  @Test
  void rfc4231TestCase1() {
    // RFC 4231 TC1
    byte[] key = new byte[20];
    java.util.Arrays.fill(key, (byte) 0x0b);
    byte[] data = "Hi There".getBytes(StandardCharsets.UTF_8);
    byte[] expected = hex("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
    assertArrayEquals(expected, hmacSha256(key, data));
  }

  @Test
  void rfc4231TestCase2() {
    // RFC 4231 TC2：key = "Jefe", data = "what do ya want for nothing?"
    byte[] key = "Jefe".getBytes(StandardCharsets.UTF_8);
    byte[] data = "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);
    byte[] expected = hex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    assertArrayEquals(expected, hmacSha256(key, data));
  }

  @Test
  void emptyKeyEmptyMessage() {
    // Python hmac 参考值
    assertArrayEquals(hex("b613679a0814d9ec772f95d778c35fc5ff1697c493715653c6c712144292c5ad"),
        hmacSha256(new byte[0], new byte[0]));
  }

  @Test
  void emptyKeyAbc() {
    assertArrayEquals(hex("fd7adb152c05ef80dccf50a1fa4c05d5a3ec6da95575fc312ae7c5d091836351"),
        hmacSha256(new byte[0], "abc".getBytes(StandardCharsets.UTF_8)));
  }
}
