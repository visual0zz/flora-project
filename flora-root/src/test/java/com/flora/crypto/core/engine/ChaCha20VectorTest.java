package com.flora.crypto.core.engine;

import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.ParametersWithIV;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ChaCha20 与 ChaCha20-Poly1305 已知答案测试（RFC 8439）。
 */
class ChaCha20VectorTest {

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static byte[] rangeKey(int start) {
    byte[] key = new byte[32];
    for (int i = 0; i < 32; i++) {
      key[i] = (byte) (start + i);
    }
    return key;
  }

  @Test
  void chacha20Block0Keystream() throws Exception {
    // RFC 8439 §2.3.2：counter=1 的第 0 块密钥流
    byte[] key = rangeKey(0x00);
    byte[] nonce = hex("000000090000004a00000000");
    byte[] expected = hex("10f1e7e4d13b5915500fdd1fa32071c4c7d1f4c733c068030422aa9ac3d46c4e"
        + "d2826446079faa0914c2d705d98b02a2b5129cd1de164eb9cbd083e8a2503c4e");

    ChaCha20Engine engine = new ChaCha20Engine();
    engine.init(key, nonce, 1);
    byte[] zero = new byte[64];
    byte[] stream = new byte[64];
    engine.processBytes(zero, 0, 64, stream, 0);
    assertArrayEquals(expected, stream);
  }

  @Test
  void chacha20Encryption() throws Exception {
    // RFC 8439 §2.4.2
    byte[] key = rangeKey(0x00);
    byte[] nonce = hex("000000000000004a00000000");
    byte[] plain = ("Ladies and Gentlemen of the class of '99: If I could offer you only one tip "
        + "for the future, sunscreen would be it.").getBytes(StandardCharsets.UTF_8);
    byte[] expected = hex("6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae0b"
        + "f91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f0861d807ca0dbf500d6a6156a3"
        + "8e088a22b65e52bc514d16ccf806818ce91ab77937365af90bbf74a35be6b40b8eedf2785e42874d");

    ChaCha20Engine engine = new ChaCha20Engine();
    engine.init(key, nonce, 1);
    byte[] ct = new byte[plain.length];
    engine.processBytes(plain, 0, plain.length, ct, 0);
    assertArrayEquals(expected, ct);
  }

  @Test
  void chacha20Poly1305Aead() throws Exception {
    // RFC 8439 §2.8.2：96 位 nonce = 32 位固定前缀 07:00:00:00 ‖ 8 字节 IV 40:..:47
    byte[] key = rangeKey(0x80);
    byte[] nonce = hex("070000004041424344454647");
    byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
    byte[] plain = ("Ladies and Gentlemen of the class of '99: If I could offer you only one tip "
        + "for the future, sunscreen would be it.").getBytes(StandardCharsets.UTF_8);
    byte[] expected = hex("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6"
        + "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692"
        + "ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3ff4"
        + "def08e4b7a9de576d26586cec64b6116"
        + "1ae10b594f09e26a7e902ecbd0600691");

    ChaCha20Poly1305 enc = new ChaCha20Poly1305();
    enc.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
    enc.processAADBytes(aad, 0, aad.length);
    byte[] ct = new byte[enc.getOutputSize(plain.length)];
    enc.processBytes(plain, 0, plain.length, ct, 0);
    int len = enc.doFinal(ct, 0);
    assertArrayEquals(expected, java.util.Arrays.copyOf(ct, len));

    // 解密往返
    ChaCha20Poly1305 dec = new ChaCha20Poly1305();
    dec.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
    dec.processAADBytes(aad, 0, aad.length);
    byte[] pt = new byte[ct.length];
    dec.processBytes(ct, 0, ct.length, pt, 0);
    int ptLen = dec.doFinal(pt, 0);
    assertArrayEquals(plain, java.util.Arrays.copyOf(pt, ptLen));
  }

  @Test
  void chacha20Poly1305BadTagRejected() throws Exception {
    byte[] key = rangeKey(0x80);
    byte[] nonce = hex("070000004041424344454647");
    byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
    byte[] plain = "hello".getBytes(StandardCharsets.UTF_8);

    ChaCha20Poly1305 enc = new ChaCha20Poly1305();
    enc.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
    enc.processAADBytes(aad, 0, aad.length);
    byte[] ct = new byte[enc.getOutputSize(plain.length)];
    enc.processBytes(plain, 0, plain.length, ct, 0);
    enc.doFinal(ct, 0);
    ct[ct.length - 1] ^= 0x01; // 篡改标签

    ChaCha20Poly1305 dec = new ChaCha20Poly1305();
    dec.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
    dec.processAADBytes(aad, 0, aad.length);
    byte[] pt = new byte[ct.length];
    dec.processBytes(ct, 0, ct.length, pt, 0);
    assertThrows(IllegalStateException.class, () -> dec.doFinal(pt, 0));
  }
}
