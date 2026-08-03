package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.KeyPairGenDSA;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;

/**
 * DSA 密钥对生成适配类。
 * <p>密钥对生成委托 flora {@link JdkKeyPairGenerator}；域参数提取保留在本层。</p>
 */
public class FloraKeyPairGenDsa implements KeyPairGenDSA {

  byte[] x; // private
  byte[] y; // public
  byte[] p;
  byte[] q;
  byte[] g;

  @Override
  public void init(int key_size) throws Exception {
    KeyPair pair = JdkKeyPairGenerator.of("DSA").generate(key_size);

    DSAPrivateKey prvKey = (DSAPrivateKey) pair.getPrivate();
    DSAPublicKey pubKey = (DSAPublicKey) pair.getPublic();

    x = prvKey.getX().toByteArray();
    y = pubKey.getY().toByteArray();

    DSAParams params = ((DSAKey) prvKey).getParams();
    p = params.getP().toByteArray();
    q = params.getQ().toByteArray();
    g = params.getG().toByteArray();
  }

  @Override
  public byte[] getX() {
    return x;
  }

  @Override
  public byte[] getY() {
    return y;
  }

  @Override
  public byte[] getP() {
    return p;
  }

  @Override
  public byte[] getQ() {
    return q;
  }

  @Override
  public byte[] getG() {
    return g;
  }
}
