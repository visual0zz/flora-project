package com.flora.communication.crypto;

import com.flora.communication.KeyPairGenRSA;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA 密钥对生成适配类。
 * <p>密钥对生成委托 flora {@link JdkKeyPairGenerator}；CRT 参数提取保留在本层。</p>
 */
public class FloraKeyPairGenRsa implements KeyPairGenRSA {

  byte[] d; // private
  byte[] e; // public
  byte[] n;

  byte[] c; // coefficient
  byte[] ep; // exponent p
  byte[] eq; // exponent q
  byte[] p; // prime p
  byte[] q; // prime q

  @Override
  public void init(int key_size) throws Exception {
    KeyPair pair = JdkKeyPairGenerator.of("RSA").generate(key_size);

    RSAPrivateKey prvKey = (RSAPrivateKey) pair.getPrivate();
    RSAPublicKey pubKey = (RSAPublicKey) pair.getPublic();

    d = prvKey.getPrivateExponent().toByteArray();
    e = pubKey.getPublicExponent().toByteArray();
    n = prvKey.getModulus().toByteArray();

    RSAPrivateCrtKey crt = (RSAPrivateCrtKey) prvKey;
    c = crt.getCrtCoefficient().toByteArray();
    ep = crt.getPrimeExponentP().toByteArray();
    eq = crt.getPrimeExponentQ().toByteArray();
    p = crt.getPrimeP().toByteArray();
    q = crt.getPrimeQ().toByteArray();
  }

  @Override
  public byte[] getD() {
    return d;
  }

  @Override
  public byte[] getE() {
    return e;
  }

  @Override
  public byte[] getN() {
    return n;
  }

  @Override
  public byte[] getC() {
    return c;
  }

  @Override
  public byte[] getEP() {
    return ep;
  }

  @Override
  public byte[] getEQ() {
    return eq;
  }

  @Override
  public byte[] getP() {
    return p;
  }

  @Override
  public byte[] getQ() {
    return q;
  }
}
