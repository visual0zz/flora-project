package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.XDH;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.engine.JdkAgreement;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

/**
 * X25519 / X448（RFC 7748 XDH）密钥交换适配类。
 * <p>密钥协商与密钥对生成委托 flora {@link JdkAgreement} / {@link JdkKeyPairGenerator}；
 * RFC 7748 的 u 坐标小端编码、RFC 8731 的长度校验等协议逻辑保留在本层。</p>
 */
public class FloraXdh implements XDH {

  private byte[] Q_array;
  private XECPublicKey publicKey;
  private int keylen;

  private JdkAgreement myKeyAgree;

  @Override
  public void init(String name, int keylen) throws Exception {
    this.keylen = keylen;
    NamedParameterSpec paramSpec = new NamedParameterSpec(name);
    KeyPair kp = JdkKeyPairGenerator.of(name).generate(paramSpec);
    publicKey = (XECPublicKey) kp.getPublic();
    Q_array = rotate(publicKey.getU().toByteArray());
    myKeyAgree = JdkAgreement.of(name);
    myKeyAgree.init(new AsymmetricKeyParameter(kp.getPrivate()));
  }

  @Override
  public byte[] getQ() throws Exception {
    return Q_array;
  }

  @Override
  public byte[] getSecret(byte[] Q) throws Exception {
    // The u coordinate in BigInteger format needs to be a positive value.
    // So zero extend the little-endian input before rotating into big-endian.
    // This should ensure that we end up with a positive BigInteger value.
    Q = rotate(Q);
    byte[] u = new byte[keylen + 1];
    System.arraycopy(Q, 0, u, 1, keylen);
    XECPublicKeySpec spec = new XECPublicKeySpec(publicKey.getParams(), new BigInteger(u));
    KeyFactory kf = KeyFactory.getInstance("XDH");
    PublicKey theirPublicKey = kf.generatePublic(spec);
    return myKeyAgree.calculateAgreement(new AsymmetricKeyParameter(theirPublicKey));
  }

  // https://cr.yp.to/ecdh.html#validate
  // RFC 8731,
  // 3. Key Exchange Methods
  // Clients and servers MUST also abort if the length of the received
  // public keys are not the expected lengths. No further validation is
  // required beyond what is described in [RFC7748].
  @Override
  public boolean validate(byte[] u) throws Exception {
    return u.length == keylen;
  }

  // RFC 7748,
  // 5. The X25519 and X448 Functions
  // The u-coordinates are encoded as an array of bytes in little-endian order.
  private byte[] rotate(byte[] in) {
    int len = in.length;
    byte[] out = new byte[len];

    for (int i = 0; i < len; i++) {
      out[i] = in[len - i - 1];
    }

    return Arrays.copyOf(out, keylen);
  }
}
