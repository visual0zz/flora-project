package com.flora.crypto.core.impl;

import com.flora.crypto.core.interfaces.algorithm.AEADBlockCipher;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.crypto.core.interfaces.material.param.KeyParameterImpl;
import com.flora.crypto.core.interfaces.material.param.ParameterWithIV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ChaCha20Poly1305 已知答案测试（RFC 8439 §2.8.2 测试向量）。
 */
class ChaCha20Poly1305VectorTest {

  private static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return out;
  }

  private static ParameterWithIV params(byte[] key, byte[] nonce) {
    return new ParameterWithIV() {
      @Override
      public CipherParameter getParameters() {
        return new KeyParameterImpl(key);
      }

      @Override
      public byte[] getIV() {
        return nonce.clone();
      }
    };
  }

  // 简化：直接用内部接口（此处仅测试 process/doFinal 形态）
  private static AEADBlockCipher cipher(boolean encrypt, byte[] key, byte[] nonce) {
    ChaCha20Poly1305 c = new ChaCha20Poly1305();
    c.init(encrypt, params(key, nonce));
    return c;
  }

  @Test
  void rfc8439Vector() {
    byte[] key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
    byte[] nonce = hex("070000004041424344454647");
    byte[] plaintext = hex(
        "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66202739393a"
            + "204966204920636f756c64206f6666657220796f75206f6e6c79206f6e652074697020666f722074"
            + "6865206675747572652c2073756e73637265656e20776f756c642062652069742e");
    byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
    byte[] expectedCiphertext = hex(
        "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9671282f"
            + "afb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee328091b58f"
            + "ab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b6116");
    byte[] expectedTag = hex("1ae10b594f09e26a7e902ecbd0600691");

    AEADBlockCipher enc = cipher(true, key, nonce);
    enc.processAADBytes(aad, 0, aad.length);
    enc.process(plaintext);
    byte[] out = enc.doFinal();
    byte[] expected = concat(expectedCiphertext, expectedTag);
    assertArrayEquals(expected, out);

    AEADBlockCipher dec = cipher(false, key, nonce);
    dec.processAADBytes(aad, 0, aad.length);
    dec.process(out);
    byte[] back = dec.doFinal();
    assertArrayEquals(plaintext, back);

    // 篡改：AAD 改动应导致标签校验失败
    AEADBlockCipher decBad = cipher(false, key, nonce);
    byte[] badAad = aad.clone();
    badAad[0] ^= 0xff;
    decBad.processAADBytes(badAad, 0, badAad.length);
    decBad.process(out);
    assertThrows(IllegalStateException.class, decBad::doFinal);
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
