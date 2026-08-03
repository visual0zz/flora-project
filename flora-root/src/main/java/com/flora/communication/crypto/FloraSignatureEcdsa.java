package com.flora.communication.crypto;

import com.flora.communication.Buffer;
import com.flora.communication.SignatureECDSA;
import com.flora.crypto.core.engine.JdkSignature;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;

/**
 * ECDSA 签名适配基类。
 * <p>签名/验签原语委托 flora {@link JdkSignature}（JDK {@code SHA256/SHA384/SHA512withECDSA}）；
 * SSH mpint 与 DER ASN.1 之间的转换、曲线识别等协议逻辑保留在本层。</p>
 */
abstract class FloraSignatureEcdsa implements SignatureECDSA {

  private JdkSignature signature;
  private KeyFactory keyFactory;

  abstract String getName();

  @Override
  public void init() throws Exception {
    String name = getName();
    String foo = "SHA256withECDSA";
    if (name.equals("ecdsa-sha2-nistp384"))
      foo = "SHA384withECDSA";
    else if (name.equals("ecdsa-sha2-nistp521"))
      foo = "SHA512withECDSA";
    signature = JdkSignature.of(foo);
    keyFactory = KeyFactory.getInstance("EC");
  }

  @Override
  public void setPubKey(byte[] r, byte[] s) throws Exception {
    // r and s must be unsigned values.
    r = insert0(r);
    s = insert0(s);

    String name = "secp256r1";
    if (r.length >= 64)
      name = "secp521r1";
    else if (r.length >= 48)
      name = "secp384r1";

    AlgorithmParameters param = AlgorithmParameters.getInstance("EC");
    param.init(new ECGenParameterSpec(name));
    ECParameterSpec ecparam = param.getParameterSpec(ECParameterSpec.class);
    ECPoint w = new ECPoint(new BigInteger(1, r), new BigInteger(1, s));
    PublicKey pubKey = keyFactory.generatePublic(new ECPublicKeySpec(w, ecparam));
    signature.initVerify(pubKey);
  }

  @Override
  public void setPrvKey(byte[] d) throws Exception {
    // d must be unsigned value.
    d = insert0(d);

    String name = "secp256r1";
    if (d.length >= 64)
      name = "secp521r1";
    else if (d.length >= 48)
      name = "secp384r1";

    AlgorithmParameters param = AlgorithmParameters.getInstance("EC");
    param.init(new ECGenParameterSpec(name));
    ECParameterSpec ecparam = param.getParameterSpec(ECParameterSpec.class);
    BigInteger _d = new BigInteger(1, d);
    PrivateKey prvKey = keyFactory.generatePrivate(new ECPrivateKeySpec(_d, ecparam));
    signature.initSign(prvKey);
  }

  @Override
  public byte[] sign() throws Exception {
    byte[] sig = signature.sign();

    // It seems that the output from SunEC is in ASN.1,
    // so we have to convert it.
    if (sig[0] == 0x30 && // in ASN.1
        ((sig[1] + 2 == sig.length)
            || ((sig[1] & 0x80) != 0 && (sig[2] & 0xff) + 3 == sig.length))) { // 2bytes for len

      int index = 3;
      if ((sig[1] & 0x80) != 0 && (sig[2] & 0xff) + 3 == sig.length)
        index = 4;

      byte[] r = new byte[sig[index]];
      byte[] s = new byte[sig[index + 2 + sig[index]]];
      System.arraycopy(sig, index + 1, r, 0, r.length);
      System.arraycopy(sig, index + 3 + sig[index], s, 0, s.length);

      r = chop0(r);
      s = chop0(s);

      Buffer buf = new Buffer();
      buf.putMPInt(r);
      buf.putMPInt(s);

      sig = new byte[buf.getLength()];
      buf.setOffSet(0);
      buf.getByte(sig);
    }

    return sig;
  }

  @Override
  public void update(byte[] foo) throws Exception {
    signature.update(foo);
  }

  @Override
  public boolean verify(byte[] sig) throws Exception {
    // It seems that SunEC expects ASN.1 data,
    // so we have to convert it.
    if (!(sig[0] == 0x30 && // not in ASN.1
        ((sig[1] + 2 == sig.length)
            || ((sig[1] & 0x80) != 0 && (sig[2] & 0xff) + 3 == sig.length)))) {
      Buffer b = new Buffer(sig);

      b.getString(); // ecdsa-sha2-nistp256
      b.getInt();

      byte[] r = b.getMPInt();
      byte[] s = b.getMPInt();

      r = trimLeadingZeros(insert0(r));
      s = trimLeadingZeros(insert0(s));

      byte[] asn1 = null;
      if (r.length < 64) {
        asn1 = new byte[6 + r.length + s.length];
        asn1[0] = (byte) 0x30;
        asn1[1] = (byte) (4 + r.length + s.length);
        asn1[2] = (byte) 0x02;
        asn1[3] = (byte) r.length;
        System.arraycopy(r, 0, asn1, 4, r.length);
        asn1[r.length + 4] = (byte) 0x02;
        asn1[r.length + 5] = (byte) s.length;
        System.arraycopy(s, 0, asn1, (6 + r.length), s.length);
      } else {
        asn1 = new byte[6 + r.length + s.length + 1];
        asn1[0] = (byte) 0x30;
        asn1[1] = (byte) 0x81;
        asn1[2] = (byte) (4 + r.length + s.length);
        asn1[3] = (byte) 0x02;
        asn1[4] = (byte) r.length;
        System.arraycopy(r, 0, asn1, 5, r.length);
        asn1[r.length + 5] = (byte) 0x02;
        asn1[r.length + 6] = (byte) s.length;
        System.arraycopy(s, 0, asn1, (7 + r.length), s.length);
      }
      sig = asn1;
    }

    return signature.verify(sig);
  }

  private static byte[] insert0(byte[] buf) {
    if ((buf[0] & 0x80) == 0)
      return buf;
    byte[] tmp = new byte[buf.length + 1];
    System.arraycopy(buf, 0, tmp, 1, buf.length);
    bzero(buf);
    return tmp;
  }

  private static byte[] chop0(byte[] buf) {
    if (buf[0] != 0)
      return buf;
    byte[] tmp = new byte[buf.length - 1];
    System.arraycopy(buf, 1, tmp, 0, tmp.length);
    bzero(buf);
    return tmp;
  }

  private static byte[] trimLeadingZeros(byte[] buf) {
    if (buf.length < 2)
      return buf;

    int i = 0;
    while (i < buf.length - 1) {
      if (buf[i] == 0 && (buf[i + 1] & 0x80) == 0)
        i++;
      else
        break;
    }

    if (i == 0)
      return buf;

    byte[] tmp = new byte[buf.length - i];
    System.arraycopy(buf, i, tmp, 0, tmp.length);
    bzero(buf);
    return tmp;
  }

  private static void bzero(byte[] buf) {
    java.util.Arrays.fill(buf, (byte) 0);
  }
}
