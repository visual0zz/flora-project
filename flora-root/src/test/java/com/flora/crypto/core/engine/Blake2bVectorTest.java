package com.flora.crypto.core.engine;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * BLAKE2b 已知答案测试（RFC 7693 附录 A 测试向量）。
 */
class Blake2bVectorTest {

  private static byte[] digest(Blake2bDigest d, String s) {
    byte[] data = s.getBytes(StandardCharsets.UTF_8);
    d.update(data, 0, data.length);
    byte[] out = new byte[d.getDigestSize()];
    d.doFinal(out, 0);
    return out;
  }

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  @Test
  void blake2b512Empty() {
    assertArrayEquals(hex("786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419"
            + "d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce"),
        digest(Blake2bDigest.of512(), ""));
  }

  @Test
  void blake2b512Abc() {
    assertArrayEquals(hex("ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d"
            + "17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923"),
        digest(Blake2bDigest.of512(), "abc"));
  }

  @Test
  void blake2b256Abc() {
    assertArrayEquals(hex("bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319"),
        digest(Blake2bDigest.of256(), "abc"));
  }

  @Test
  void blake2b512Fox() {
    assertArrayEquals(hex("a8add4bdddfd93e4877d2746e62817b116364a1fa7bc148d95090bc7333b3673"
            + "f82401cf7aa2e4cb1ecd90296e3f14cb5413f8ed77be73045b13914cdcd6a918"),
        digest(Blake2bDigest.of512(), "The quick brown fox jumps over the lazy dog"));
  }

  @Test
  void blake2b512LongMessage() {
    // RFC 7693 A.2：1 MiB 全 'a' 消息
    StringBuilder sb = new StringBuilder(1 << 20);
    sb.append("a".repeat(1 << 20));
    assertArrayEquals(hex("e662a19f0d588279d5f373a1d31d0a5cb8de2efe2400e7389af4df561999f530"
            + "83f83d04f5618a2307a87a8aa094e63710627c5798fb2f2068c98b9d31012079"),
        digest(Blake2bDigest.of512(), sb.toString()));
  }
}
