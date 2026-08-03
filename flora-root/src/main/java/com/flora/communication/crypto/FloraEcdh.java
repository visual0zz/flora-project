package com.flora.communication.crypto;

import com.flora.communication.ECDH;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.engine.JdkAgreement;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;

/**
 * ECDH 密钥交换适配类（NIST P-256/384/521）。
 * <p>密钥协商与密钥对生成委托 flora {@link JdkAgreement} / {@link JdkKeyPairGenerator}；
 * SEC 1 §3.2.2 椭圆曲线点校验（验证流程）保留在本层。</p>
 */
public class FloraEcdh implements ECDH {

  byte[] Q_array;
  ECPublicKey publicKey;

  private JdkAgreement myKeyAgree;

  @Override
  public void init(int size) throws Exception {
    myKeyAgree = JdkAgreement.of("ECDH");
    String curve = null;
    if (size == 256)
      curve = "secp256r1";
    else if (size == 384)
      curve = "secp384r1";
    else if (size == 521)
      curve = "secp521r1";
    else
      throw new com.flora.communication.JSchException("unsupported key size: " + size);

    for (int i = 0; i < 1000; i++) {
      KeyPair kp = JdkKeyPairGenerator.of("EC").generate(new ECGenParameterSpec(curve));
      ECPublicKey pub = (ECPublicKey) kp.getPublic();
      byte[] r = pub.getW().getAffineX().toByteArray();
      byte[] s = pub.getW().getAffineY().toByteArray();
      if (r.length == s.length) {
        if ((size == 256 && r.length == 32) || (size == 384 && r.length == 48)
            || (size == 521 && r.length == 66)) {
          publicKey = pub;
          Q_array = toPoint(r, s);
          myKeyAgree.init(new AsymmetricKeyParameter(kp.getPrivate()));
          return;
        }
      }
    }
    throw new com.flora.communication.JSchException("failed to generate EC key pair");
  }

  @Override
  public byte[] getQ() throws Exception {
    return Q_array;
  }

  @Override
  public byte[] getSecret(byte[] r, byte[] s) throws Exception {
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPoint w = new ECPoint(new BigInteger(1, r), new BigInteger(1, s));
    ECPublicKeySpec spec = new ECPublicKeySpec(w, publicKey.getParams());
    PublicKey theirPublicKey = kf.generatePublic(spec);
    return myKeyAgree.calculateAgreement(new AsymmetricKeyParameter(theirPublicKey));
  }

  private static BigInteger two = BigInteger.ONE.add(BigInteger.ONE);
  private static BigInteger three = two.add(BigInteger.ONE);

  // SEC 1: Elliptic Curve Cryptography, Version 2.0
  // http://www.secg.org/sec1-v2.pdf
  // 3.2.2.1 Elliptic Curve Public Key Validation Primitive
  @Override
  public boolean validate(byte[] r, byte[] s) throws Exception {
    BigInteger x = new BigInteger(1, r);
    BigInteger y = new BigInteger(1, s);

    // Step.1
    // Check that Q != O
    ECPoint w = new ECPoint(x, y);
    if (w.equals(ECPoint.POINT_INFINITY)) {
      return false;
    }

    // Step.2
    // If T represents elliptic curve domain parameters over Fp,
    // check that xQ and yQ are integers in the interval [0, p-1],
    // and that: y^2 = x^3 + x*a + b (mod p)
    ECParameterSpec params = publicKey.getParams();
    EllipticCurve curve = params.getCurve();
    BigInteger p = ((ECFieldFp) curve.getField()).getP(); // nistp should be Fp.

    // xQ and yQ should be integers in the interval [0, p-1]
    BigInteger p_sub1 = p.subtract(BigInteger.ONE);
    if (!(x.compareTo(p_sub1) <= 0 && y.compareTo(p_sub1) <= 0)) {
      return false;
    }

    // y^2 = x^3 + x*a + b (mod p)
    BigInteger tmp = x.multiply(curve.getA()).add(curve.getB()).add(x.modPow(three, p)).mod(p);
    BigInteger y_2 = y.modPow(two, p);
    if (!(y_2.equals(tmp))) {
      return false;
    }

    // Step.3
    // Check that nQ = O.
    // Unfortunately, JCE does not provide the point multiplication method.
    return true;
  }

  private byte[] toPoint(byte[] r_array, byte[] s_array) {
    byte[] tmp = new byte[1 + r_array.length + s_array.length];
    tmp[0] = 0x04;
    System.arraycopy(r_array, 0, tmp, 1, r_array.length);
    System.arraycopy(s_array, 0, tmp, 1 + r_array.length, s_array.length);
    return tmp;
  }
}
