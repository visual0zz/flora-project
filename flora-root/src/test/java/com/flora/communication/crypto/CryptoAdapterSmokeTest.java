package com.flora.communication.crypto;

import com.flora.communication.Buffer;
import com.flora.communication.Cipher;
import com.flora.communication.DH;
import com.flora.communication.HASH;
import com.flora.communication.MAC;
import com.flora.communication.Signature;
import com.flora.communication.SignatureEdDSA;
import com.flora.communication.SignatureRSA;
import com.flora.communication.XDH;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.engine.JdkKem;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * crypto 适配层冒烟测试。
 * <p>验证 flora 适配器（{@code com.flora.communication.crypto}）在 SSH 用法下的
 * 加解密/摘要/MAC/签名/密钥协商往返正确性。不依赖外部 SSH 服务器。</p>
 */
class CryptoAdapterSmokeTest {

  private static final byte[] MESSAGE = "The quick brown fox jumps over the lazy dog"
      .getBytes(StandardCharsets.UTF_8);

  // ── AES-GCM：SSH 用法（updateAAD + doFinal），并验证 IV 计数器跨包递增 ──

  @Test
  void aes128GcmSshUsageRoundTrip() throws Exception {
    byte[] key = new byte[16];
    Arrays.fill(key, (byte) 0x11);
    // 12 字节 IV：前 4 字节固定 + 8 字节计数器（对应 RFC 5647 隐式 IV）
    byte[] iv = new byte[12];
    iv[11] = 0x00;

    byte[] lengthField = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x20};

    Aes128Gcm enc = new Aes128Gcm();
    enc.init(Cipher.ENCRYPT_MODE, key, iv);
    Aes128Gcm dec = new Aes128Gcm();
    dec.init(Cipher.DECRYPT_MODE, key, iv);

    // 两个连续数据包：doFinal 内部应自动递增计数器
    for (int p = 0; p < 2; p++) {
      byte[] plain = Arrays.copyOf(MESSAGE, MESSAGE.length);
      plain[0] ^= (byte) p;

      byte[] encOut = new byte[plain.length + enc.getTagSize()];
      enc.updateAAD(lengthField, 0, 4);
      enc.doFinal(plain, 0, plain.length, encOut, 0);

      byte[] decOut = new byte[encOut.length];
      dec.updateAAD(lengthField, 0, 4);
      dec.doFinal(encOut, 0, encOut.length, decOut, 0);

      assertArrayEquals(plain, Arrays.copyOf(decOut, plain.length),
          "GCM 数据包 " + p + " 解密结果不一致");
    }
  }

  @Test
  void aes128GcmBadTagRejected() throws Exception {
    byte[] key = new byte[16];
    byte[] iv = new byte[12];
    byte[] plain = Arrays.copyOf(MESSAGE, MESSAGE.length);

    Aes128Gcm enc = new Aes128Gcm();
    enc.init(Cipher.ENCRYPT_MODE, key, iv);
    byte[] encOut = new byte[plain.length + enc.getTagSize()];
    enc.doFinal(plain, 0, plain.length, encOut, 0);
    encOut[encOut.length - 1] ^= 0x01; // 篡改认证标签

    Aes128Gcm dec = new Aes128Gcm();
    dec.init(Cipher.DECRYPT_MODE, key, iv);
    byte[] decOut = new byte[encOut.length];
    assertThrows(javax.crypto.AEADBadTagException.class,
        () -> dec.doFinal(encOut, 0, encOut.length, decOut, 0));
  }

  @Test
  void aes128GcmJdkCross() throws Exception {
    byte[] key = new byte[16];
    byte[] iv = new byte[12];
    byte[] aad = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x20};

    // 自研加密 → JDK 解密
    Aes128Gcm enc = new Aes128Gcm();
    enc.init(Cipher.ENCRYPT_MODE, key, iv);
    byte[] encOut = new byte[MESSAGE.length + enc.getTagSize()];
    enc.updateAAD(aad, 0, 4);
    enc.doFinal(MESSAGE, 0, MESSAGE.length, encOut, 0);

    javax.crypto.Cipher jdk = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
    jdk.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
        new javax.crypto.spec.GCMParameterSpec(128, iv));
    jdk.updateAAD(aad);
    byte[] recovered = jdk.doFinal(encOut);
    assertArrayEquals(MESSAGE, recovered);

    // JDK 加密 → 自研解密
    javax.crypto.Cipher jdk2 = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
    jdk2.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"),
        new javax.crypto.spec.GCMParameterSpec(128, iv));
    jdk2.updateAAD(aad);
    byte[] jdkCt = jdk2.doFinal(MESSAGE);

    Aes128Gcm dec = new Aes128Gcm();
    dec.init(Cipher.DECRYPT_MODE, key, iv);
    byte[] decOut = new byte[jdkCt.length];
    dec.updateAAD(aad, 0, 4);
    dec.doFinal(jdkCt, 0, jdkCt.length, decOut, 0);
    assertArrayEquals(MESSAGE, Arrays.copyOf(decOut, MESSAGE.length));
  }

  // ── AES-CBC / AES-CTR 往返 ──

  @Test
  void aes128CbcRoundTrip() throws Exception {
    assertCipherRoundTrip(new Aes128Cbc(), 32);
  }

  @Test
  void aes128CtrRoundTrip() throws Exception {
    assertCipherRoundTrip(new Aes128Ctr(), 48);
  }

  @Test
  void aes256CbcRoundTrip() throws Exception {
    assertCipherRoundTrip(new Aes256Cbc(), 32);
  }

  private void assertCipherRoundTrip(Cipher cipher, int blockAlignedLen) throws Exception {
    byte[] key = new byte[16];
    byte[] iv = new byte[cipher.getIVSize()];
    Arrays.fill(key, (byte) 0x22);
    Arrays.fill(iv, (byte) 0x33);

    byte[] plain = new byte[blockAlignedLen];
    for (int i = 0; i < plain.length; i++) {
      plain[i] = (byte) (i * 3 + 1);
    }

    cipher.init(Cipher.ENCRYPT_MODE, key, iv);
    byte[] encOut = new byte[plain.length];
    cipher.update(plain, 0, plain.length, encOut, 0);

    Cipher dec = (Cipher) cipher.getClass().getDeclaredConstructor().newInstance();
    dec.init(Cipher.DECRYPT_MODE, key, iv);
    byte[] decOut = new byte[encOut.length];
    dec.update(encOut, 0, encOut.length, decOut, 0);

    assertArrayEquals(plain, decOut, "分组密码往返解密不一致");
  }

  // ── HMAC / 摘要 ──

  @Test
  void hmacSha256KnownAnswer() throws Exception {
    // RFC 4231 Test Case 1
    byte[] key = new byte[20];
    Arrays.fill(key, (byte) 0x0b);
    byte[] data = "Hi There".getBytes(StandardCharsets.UTF_8);
    byte[] expected = {(byte) 0xb0, (byte) 0x34, (byte) 0x4c, (byte) 0x61, (byte) 0xd8, (byte) 0xdb,
        (byte) 0x38, (byte) 0x53, (byte) 0x5c, (byte) 0xa8, (byte) 0xaf, (byte) 0xce,
        (byte) 0xaf, (byte) 0x0b, (byte) 0xf1, (byte) 0x2b, (byte) 0x88, (byte) 0x1d,
        (byte) 0xc2, (byte) 0x00, (byte) 0xc9, (byte) 0x83, (byte) 0x3d, (byte) 0xa7,
        (byte) 0x26, (byte) 0xe9, (byte) 0x37, (byte) 0x6c, (byte) 0x2e, (byte) 0x32,
        (byte) 0xcf, (byte) 0xf7};

    HmacSha256 mac = new HmacSha256();
    mac.init(key);
    mac.update(data, 0, data.length);
    byte[] out = new byte[mac.getBlockSize()];
    mac.doFinal(out, 0);
    assertArrayEquals(expected, out);
  }

  @Test
  void sha256DigestKnownAnswer() throws Exception {
    // FIPS 180-4：SHA-256("The quick brown fox jumps over the lazy dog")
    byte[] expected = {(byte) 0xd7, (byte) 0xa8, (byte) 0xfb, (byte) 0xb3, (byte) 0x07,
        (byte) 0xd7, (byte) 0x80, (byte) 0x94, (byte) 0x69, (byte) 0xca, (byte) 0x9a,
        (byte) 0xbc, (byte) 0xb0, (byte) 0x08, (byte) 0x2e, (byte) 0x4f, (byte) 0x8d,
        (byte) 0x56, (byte) 0x51, (byte) 0xe4, (byte) 0x6d, (byte) 0x3c, (byte) 0xdb,
        (byte) 0x76, (byte) 0x2d, (byte) 0x02, (byte) 0xd0, (byte) 0xbf, (byte) 0x37,
        (byte) 0xc9, (byte) 0xe5, (byte) 0x92};
    HASH sha = new Sha256();
    sha.init();
    sha.update(MESSAGE, 0, MESSAGE.length);
    assertArrayEquals(expected, sha.digest());
  }

  // ── 签名：Ed25519 ──

  @Test
  void ed25519SignVerifyRoundTrip() throws Exception {
    FloraKeyPairGenEdDsa kpg = new FloraKeyPairGenEdDsa();
    kpg.init("Ed25519", 32);

    SignatureEdDSA signer = new SignatureEd25519();
    signer.init();
    signer.setPrvKey(kpg.getPrv());
    signer.update(MESSAGE);
    byte[] raw = signer.sign();

    SignatureEdDSA verifier = new SignatureEd25519();
    verifier.init();
    verifier.setPubKey(kpg.getPub());
    verifier.update(MESSAGE);
    assertTrue(verifier.verify(wrapSignature("ssh-ed25519", raw)));
  }

  // ── 签名：RSA (rsa-sha2-256) ──

  @Test
  void rsaSha256SignVerifyRoundTrip() throws Exception {
    KeyPair kp = JdkKeyPairGenerator.of("RSA").generate(2048);
    RSAPrivateKey prv = (RSAPrivateKey) kp.getPrivate();
    RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

    SignatureRSA signer = new SignatureRsaSha256();
    signer.init();
    signer.setPrvKey(prv.getPrivateExponent().toByteArray(), prv.getModulus().toByteArray());
    signer.update(MESSAGE);
    byte[] raw = signer.sign();

    SignatureRSA verifier = new SignatureRsaSha256();
    verifier.init();
    verifier.setPubKey(pub.getPublicExponent().toByteArray(), pub.getModulus().toByteArray());
    verifier.update(MESSAGE);
    assertTrue(verifier.verify(wrapSignature("rsa-sha2-256", raw)));
  }

  // ── 密钥协商：X25519 ──

  @Test
  void x25519AgreementMatches() throws Exception {
    FloraXdh alice = new FloraXdh();
    alice.init("X25519", 32);
    FloraXdh bob = new FloraXdh();
    bob.init("X25519", 32);

    byte[] qAlice = alice.getQ();
    byte[] qBob = bob.getQ();
    assertTrue(alice.validate(qBob));
    assertTrue(bob.validate(qAlice));

    byte[] secretAlice = alice.getSecret(qBob);
    byte[] secretBob = bob.getSecret(qAlice);
    assertArrayEquals(secretAlice, secretBob);
  }

  // ── 密钥协商：经典 DH ──

  @Test
  void dhAgreementMatches() throws Exception {
    java.math.BigInteger p =
        new java.math.BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
            + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
            + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
            + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
            + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
            + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
            + "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    java.math.BigInteger g = java.math.BigInteger.valueOf(2);

    DH alice = new FloraDh();
    alice.init();
    alice.setP(p.toByteArray());
    alice.setG(g.toByteArray());
    byte[] e = alice.getE();

    DH bob = new FloraDh();
    bob.init();
    bob.setP(p.toByteArray());
    bob.setG(g.toByteArray());
    byte[] f = bob.getE();

    alice.setF(f);
    alice.checkRange();
    bob.setF(e);
    bob.checkRange();

    byte[] kAlice = alice.getK();
    byte[] kBob = bob.getK();
    assertArrayEquals(kAlice, kBob);
  }

  // ── ML-KEM（JDK KEM 封装/解封装）──

  @Test
  void mlkem768EncapsulateDecapsulate() throws Exception {
    KeyPair kp = JdkKeyPairGenerator.of("ML-KEM").generate(NamedParameterSpec.ML_KEM_768);
    JdkKem kem = JdkKem.of("ML-KEM");
    com.flora.crypto.core.interfaces.Encapsulator enc =
        kem.newEncapsulator(new AsymmetricKeyParameter(kp.getPublic()));
    com.flora.crypto.core.interfaces.Decapsulator dec =
        kem.newDecapsulator(new AsymmetricKeyParameter(kp.getPrivate()));

    com.flora.crypto.core.interfaces.SecretWithEncapsulation e = enc.encapsulate();
    byte[] shared = dec.decapsulate(e.getEncapsulation()).getSecret();
    assertArrayEquals(e.getSecret(), shared);
  }

  // ── 工具：把裸签名包装成 SSH 线格式（string 算法名 + string 签名）──

  private static byte[] wrapSignature(String algorithm, byte[] rawSig) {
    Buffer buf = new Buffer();
    buf.putString(algorithm.getBytes(StandardCharsets.UTF_8));
    buf.putString(rawSig);
    byte[] wire = new byte[buf.getLength()];
    buf.setOffSet(0);
    buf.getByte(wire);
    return wire;
  }
}
