package com.flora.communication.crypto;

import com.flora.communication.KeyPairGenEdDSA;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.util.Arrays;

/**
 * EdDSA 密钥对生成适配类。
 * <p>密钥对生成委托 flora {@link JdkKeyPairGenerator}；RFC 8032 的点编码
 * （y 坐标小端 + x 奇偶最高位）保留在本层。</p>
 */
public class FloraKeyPairGenEdDsa implements KeyPairGenEdDSA {

  byte[] prv; // private
  byte[] pub; // public
  int keylen;

  @Override
  public void init(String name, int keylen) throws Exception {
    this.keylen = keylen;

    KeyPair pair = JdkKeyPairGenerator.of(name).generate();

    EdECPublicKey pubKey = (EdECPublicKey) pair.getPublic();
    EdECPrivateKey prvKey = (EdECPrivateKey) pair.getPrivate();
    EdECPoint point = pubKey.getPoint();

    prv = prvKey.getBytes().get();
    pub = rotate(point.getY().toByteArray());
    if (point.isXOdd()) {
      pub[pub.length - 1] |= (byte) 0x80;
    }
  }

  @Override
  public byte[] getPrv() {
    return prv;
  }

  @Override
  public byte[] getPub() {
    return pub;
  }

  private byte[] rotate(byte[] in) {
    int len = in.length;
    byte[] out = new byte[len];

    for (int i = 0; i < len; i++) {
      out[i] = in[len - i - 1];
    }

    return Arrays.copyOf(out, keylen);
  }
}
