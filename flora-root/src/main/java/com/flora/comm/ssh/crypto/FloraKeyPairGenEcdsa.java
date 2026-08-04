package com.flora.comm.ssh.crypto;

import com.flora.comm.ssh.JSchException;
import com.flora.comm.ssh.KeyPairGenECDSA;
import com.flora.crypto.core.bridge.JdkKeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;

/**
 * ECDSA 密钥对生成适配类。
 * <p>密钥对生成委托 flora {@link JdkKeyPairGenerator}；坐标/标量定长化与符号位处理保留在本层。</p>
 */
public class FloraKeyPairGenEcdsa implements KeyPairGenECDSA {

  byte[] d;
  byte[] r;
  byte[] s;

  @Override
  public void init(int key_size) throws Exception {
    String name = null;
    if (key_size == 256)
      name = "secp256r1";
    else if (key_size == 384)
      name = "secp384r1";
    else if (key_size == 521)
      name = "secp521r1";
    else
      throw new JSchException("unsupported key size: " + key_size);

    for (int i = 0; i < 1000; i++) {
      KeyPair kp = JdkKeyPairGenerator.of("EC").generate(new ECGenParameterSpec(name));
      ECPrivateKey prvKey = (ECPrivateKey) kp.getPrivate();
      ECPublicKey pubKey = (ECPublicKey) kp.getPublic();
      d = prvKey.getS().toByteArray();
      ECPoint w = pubKey.getW();
      r = w.getAffineX().toByteArray();
      s = w.getAffineY().toByteArray();

      if (r.length != s.length)
        continue;
      if (key_size == 256 && r.length == 32)
        break;
      if (key_size == 384 && r.length == 48)
        break;
      if (key_size == 521 && r.length == 66)
        break;
    }
    if (d.length < r.length) {
      d = insert0(d);
    }
  }

  @Override
  public byte[] getD() {
    return d;
  }

  @Override
  public byte[] getR() {
    return r;
  }

  @Override
  public byte[] getS() {
    return s;
  }

  private byte[] insert0(byte[] buf) {
    byte[] tmp = new byte[buf.length + 1];
    System.arraycopy(buf, 0, tmp, 1, buf.length);
    bzero(buf);
    return tmp;
  }

  private static void bzero(byte[] buf) {
    java.util.Arrays.fill(buf, (byte) 0);
  }
}
