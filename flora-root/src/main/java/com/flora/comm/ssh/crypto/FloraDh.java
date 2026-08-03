package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.DH;
import com.flora.comm.ssh.JSchException;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.engine.JdkAgreement;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

/**
 * Diffie-Hellman 密钥交换适配类。
 * <p>密钥协商原语委托 flora {@link JdkAgreement}，密钥对生成委托 {@link JdkKeyPairGenerator}；
 * RFC 8268 的合法域检查（{@code 1 < e,f < p-1}）保留在本层。</p>
 */
public class FloraDh implements DH {

  private BigInteger p;
  private BigInteger g;
  private BigInteger e; // my public key
  private byte[] e_array;
  private BigInteger f; // your public key

  private JdkKeyPairGenerator myKpairGen;
  private JdkAgreement myKeyAgree;

  @Override
  public void init() throws Exception {
    myKpairGen = JdkKeyPairGenerator.of("DH");
    myKeyAgree = JdkAgreement.of("DH");
  }

  @Override
  public byte[] getE() throws Exception {
    if (e == null) {
      DHParameterSpec dhSkipParamSpec = new DHParameterSpec(p, g);
      KeyPair myKpair = myKpairGen.generate(dhSkipParamSpec);
      myKeyAgree.init(new AsymmetricKeyParameter(myKpair.getPrivate()));
      e = ((DHPublicKey) (myKpair.getPublic())).getY();
      e_array = e.toByteArray();
    }
    // Per RFC 8268 4. Checking the Peer's DH Public Key:
    // 1 < e < p-1
    checkRange(e);
    return e_array;
  }

  @Override
  public byte[] getK() throws Exception {
    KeyFactory myKeyFac = KeyFactory.getInstance("DH");
    DHPublicKeySpec keySpec = new DHPublicKeySpec(f, p, g);
    PublicKey yourPubKey = myKeyFac.generatePublic(keySpec);
    return myKeyAgree.calculateAgreement(new AsymmetricKeyParameter(yourPubKey));
  }

  @Override
  public void setP(byte[] p) {
    setP(new BigInteger(1, p));
  }

  @Override
  public void setG(byte[] g) {
    setG(new BigInteger(1, g));
  }

  @Override
  public void setF(byte[] f) {
    setF(new BigInteger(1, f));
  }

  void setP(BigInteger p) {
    this.p = p;
  }

  void setG(BigInteger g) {
    this.g = g;
  }

  void setF(BigInteger f) {
    this.f = f;
  }

  // Per RFC 8268 4. Checking the Peer's DH Public Key:
  // 1 < f < p-1
  @Override
  public void checkRange() throws Exception {
    checkRange(f);
  }

  private void checkRange(BigInteger tmp) throws Exception {
    if (tmp.compareTo(BigInteger.ONE) <= 0 || tmp.compareTo(p.subtract(BigInteger.ONE)) >= 0) {
      throw new JSchException("invalid DH value");
    }
  }
}
