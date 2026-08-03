package com.flora.communication.crypto;

import com.flora.communication.JSchException;
import com.flora.communication.KDF;
import com.flora.communication.asn1.ASN1;
import com.flora.communication.asn1.ASN1Exception;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.engine.JdkMac;
import com.flora.crypto.core.engine.Pbkdf2ParametersGenerator;
import java.util.Arrays;

/**
 * PBKDF2 口令派生适配类（OpenSSH 加密私钥 KDF）。
 * <p>ASN.1 参数解析保留在本层；实际的 PBKDF2 迭代计算委托 flora
 * {@link Pbkdf2ParametersGenerator}（以 {@link JdkMac} 为 PRF 原语），
 * 取代 JDK {@code SecretKeyFactory} 的 PBKDF2 组合结构。</p>
 */
public class FloraPbkdf2 implements KDF {

  private static final byte[] hmacWithSha1 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
      (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x07};

  private static final byte[] hmacWithSha224 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
      (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x08};

  private static final byte[] hmacWithSha256 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
      (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x09};

  private static final byte[] hmacWithSha384 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
      (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x0a};

  private static final byte[] hmacWithSha512 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48, (byte) 0x86,
      (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x0b};

  private static final byte[] hmacWithSha512224 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48,
      (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x0c};

  private static final byte[] hmacWithSha512256 = {(byte) 0x2a, (byte) 0x86, (byte) 0x48,
      (byte) 0x86, (byte) 0xf7, (byte) 0x0d, (byte) 0x02, (byte) 0x0d};

  private byte[] salt;
  private int iterations;
  private String hmacName;

  @Override
  public void initWithASN1(byte[] asn1) throws Exception {
    try {
      ASN1 prf = null;
      ASN1 content = new ASN1(asn1);
      if (!content.isSEQUENCE()) {
        throw new ASN1Exception();
      }
      ASN1[] contents = content.getContents();
      if (contents.length < 2 || contents.length > 4) {
        throw new ASN1Exception();
      }
      if (!contents[0].isOCTETSTRING()) {
        throw new ASN1Exception();
      }
      if (!contents[1].isINTEGER()) {
        throw new ASN1Exception();
      }

      if (contents.length == 4) {
        if (!contents[2].isINTEGER()) {
          throw new ASN1Exception();
        }
        if (!contents[3].isSEQUENCE()) {
          throw new ASN1Exception();
        }
        prf = contents[3];
      } else if (contents.length == 3) {
        if (contents[2].isSEQUENCE()) {
          prf = contents[2];
        } else if (!contents[2].isINTEGER()) {
          throw new ASN1Exception();
        }
      }

      byte[] prfid = null;
      salt = contents[0].getContent();
      iterations = ASN1.parseASN1IntegerAsInt(contents[1].getContent());

      if (prf != null) {
        contents = prf.getContents();
        if (contents.length != 2) {
          throw new ASN1Exception();
        }
        if (!contents[0].isOBJECT()) {
          throw new ASN1Exception();
        }
        if (!contents[1].isNULL()) {
          throw new ASN1Exception();
        }
        prfid = contents[0].getContent();
      }

      hmacName = getHmacName(prfid);
    } catch (Exception e) {
      if (e instanceof JSchException)
        throw (JSchException) e;
      if (e instanceof ASN1Exception || e instanceof ArithmeticException)
        throw new JSchException("invalid ASN1", e);
      throw new JSchException("pbkdf2 unavailable", e);
    }
  }

  @Override
  public byte[] getKey(byte[] _pass, int size) {
    Pbkdf2ParametersGenerator gen = new Pbkdf2ParametersGenerator(JdkMac.of(hmacName));
    gen.init(_pass, salt, iterations);
    KeyParameter key = (KeyParameter) gen.generateDerivedParameters(size * 8);
    return key.getKey();
  }

  static String getHmacName(byte[] id) throws JSchException {
    String name = null;
    if (id == null || Arrays.equals(id, hmacWithSha1)) {
      name = "HmacSHA1";
    } else if (Arrays.equals(id, hmacWithSha224)) {
      name = "HmacSHA224";
    } else if (Arrays.equals(id, hmacWithSha256)) {
      name = "HmacSHA256";
    } else if (Arrays.equals(id, hmacWithSha384)) {
      name = "HmacSHA384";
    } else if (Arrays.equals(id, hmacWithSha512)) {
      name = "HmacSHA512";
    } else if (Arrays.equals(id, hmacWithSha512224)) {
      name = "HmacSHA512/224";
    } else if (Arrays.equals(id, hmacWithSha512256)) {
      name = "HmacSHA512/256";
    }

    if (name == null) {
      throw new JSchException("unsupported pbkdf2 function oid: " + toHex(id));
    }
    return name;
  }

  static String toHex(byte[] str) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length; i++) {
      String foo = Integer.toHexString(str[i] & 0xff);
      sb.append("0x" + (foo.length() == 1 ? "0" : "") + foo);
      if (i + 1 < str.length)
        sb.append(":");
    }
    return sb.toString();
  }
}
