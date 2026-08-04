package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.Buffer;
import com.flora.comm.ssh.SignatureDSA;
import com.flora.crypto.core.bridge.JdkSignature;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;

/**
 * DSA 签名适配类（ssh-dss，SHA-1）。
 * <p>签名/验签原语委托 flora {@link JdkSignature}；SSH 40 字节固定长度与 ASN.1 互转等
 * 协议逻辑保留在本层。</p>
 */
public class FloraSignatureDsa implements SignatureDSA {

  private JdkSignature signature;
  private KeyFactory keyFactory;

  @Override
  public void init() throws Exception {
    signature = JdkSignature.of("SHA1withDSA");
    keyFactory = KeyFactory.getInstance("DSA");
  }

  @Override
  public void setPubKey(byte[] y, byte[] p, byte[] q, byte[] g) throws Exception {
    DSAPublicKeySpec dsaPubKeySpec = new DSAPublicKeySpec(new BigInteger(y), new BigInteger(p),
        new BigInteger(q), new BigInteger(g));
    PublicKey pubKey = keyFactory.generatePublic(dsaPubKeySpec);
    signature.initVerify(pubKey);
  }

  @Override
  public void setPrvKey(byte[] x, byte[] p, byte[] q, byte[] g) throws Exception {
    DSAPrivateKeySpec dsaPrivKeySpec = new DSAPrivateKeySpec(new BigInteger(x), new BigInteger(p),
        new BigInteger(q), new BigInteger(g));
    PrivateKey prvKey = keyFactory.generatePrivate(dsaPrivKeySpec);
    signature.initSign(prvKey);
  }

  @Override
  public byte[] sign() throws Exception {
    byte[] sig = signature.sign();
    // sig is in ASN.1: SEQUENCE::={ r INTEGER, s INTEGER }
    int len = 0;
    int index = 3;
    len = sig[index++] & 0xff;
    byte[] r = new byte[len];
    System.arraycopy(sig, index, r, 0, r.length);
    index = index + len + 1;
    len = sig[index++] & 0xff;
    byte[] s = new byte[len];
    System.arraycopy(sig, index, s, 0, s.length);

    byte[] result = new byte[40];
    // result must be 40 bytes, but length of r and s may not be 20 bytes
    System.arraycopy(r, (r.length > 20) ? 1 : 0, result, (r.length > 20) ? 0 : 20 - r.length,
        (r.length > 20) ? 20 : r.length);
    System.arraycopy(s, (s.length > 20) ? 1 : 0, result, (s.length > 20) ? 20 : 40 - s.length,
        (s.length > 20) ? 20 : s.length);
    return result;
  }

  @Override
  public void update(byte[] foo) throws Exception {
    signature.update(foo);
  }

  @Override
  public boolean verify(byte[] sig) throws Exception {
    int i = 0;
    int j = 0;
    byte[] tmp;
    Buffer buf = new Buffer(sig);

    if (new String(buf.getString(), StandardCharsets.UTF_8).equals("ssh-dss")) {
      j = buf.getInt();
      i = buf.getOffSet();
      tmp = new byte[j];
      System.arraycopy(sig, i, tmp, 0, j);
      sig = tmp;
    }

    byte[] _frst = new byte[20];
    System.arraycopy(sig, 0, _frst, 0, 20);
    _frst = normalize(_frst);

    byte[] _scnd = new byte[20];
    System.arraycopy(sig, 20, _scnd, 0, 20);
    _scnd = normalize(_scnd);

    // ASN.1
    int frst = ((_frst[0] & 0x80) != 0 ? 1 : 0);
    int scnd = ((_scnd[0] & 0x80) != 0 ? 1 : 0);

    int length = _frst.length + _scnd.length + 6 + frst + scnd;
    tmp = new byte[length];
    tmp[0] = (byte) 0x30;
    tmp[1] = (byte) (_frst.length + _scnd.length + 4);
    tmp[1] += (byte) frst;
    tmp[1] += (byte) scnd;
    tmp[2] = (byte) 0x02;
    tmp[3] = (byte) _frst.length;
    tmp[3] += (byte) frst;
    System.arraycopy(_frst, 0, tmp, 4 + frst, _frst.length);
    tmp[4 + tmp[3]] = (byte) 0x02;
    tmp[5 + tmp[3]] = (byte) _scnd.length;
    tmp[5 + tmp[3]] += (byte) scnd;
    System.arraycopy(_scnd, 0, tmp, 6 + tmp[3] + scnd, _scnd.length);
    sig = tmp;

    return signature.verify(sig);
  }

  protected byte[] normalize(byte[] secret) {
    if (secret.length > 1 && secret[0] == 0 && (secret[1] & 0x80) == 0) {
      byte[] tmp = new byte[secret.length - 1];
      System.arraycopy(secret, 1, tmp, 0, tmp.length);
      return normalize(tmp);
    } else {
      return secret;
    }
  }
}
