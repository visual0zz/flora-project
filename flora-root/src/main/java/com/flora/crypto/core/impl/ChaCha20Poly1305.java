package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.interfaces.algorithm.AEADBlockCipher;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.crypto.core.interfaces.material.param.KeyParameter;
import com.flora.crypto.core.interfaces.material.param.ParameterWithIV;
import com.flora.java.CheckUtil;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Set;

/**
 * ChaCha20-Poly1305 AEAD（RFC 8439 §2.8）。
 * <p>标准 IETF 构造：Poly1305 密钥取 ChaCha20 第 0 块前 32 字节，数据加解密用第 1 块起的
 * 密钥流；MAC 覆盖 {@code AAD‖pad16(AAD)‖密文‖pad16(密文)‖len64(AAD)‖len64(密文)}。</p>
 */
public final class ChaCha20Poly1305 implements AEADBlockCipher {

  private static final int TAG_LEN = 16;

  private byte[] key;
  private byte[] nonce;
  private boolean encrypting;

  private final ByteArrayOutputStream aad = new ByteArrayOutputStream();
  private final ByteArrayOutputStream data = new ByteArrayOutputStream();
  private final Poly1305Mac poly1305 = new Poly1305Mac();

  private byte[] lastTag;

  @Override
  public void init(boolean forEncryption, CipherParameter params) {
    if (!(params instanceof ParameterWithIV p) || !(p.getParameters() instanceof KeyParameter)) {
      throw new IllegalArgumentException("ChaCha20Poly1305 需要 ParameterWithIV(KeyParameter, nonce)");
    }
    this.encrypting = forEncryption;
    this.key = ((KeyParameter) p.getParameters()).getKey();
    this.nonce = p.getIV();
    aad.reset();
    data.reset();
    lastTag = null;
  }

  @Override
  public String getAlgorithmName() {
    return "ChaCha20-Poly1305";
  }

  @Override
  public int getBlockSize() {
    return 64;
  }

  @Override
  public void processAADBytes(byte[] assocText) {
    aad.write(assocText, 0, assocText.length);
  }

  @Override
  public void processAADBytes(byte[] assocText, int off, int len) {
    aad.write(assocText, off, len);
  }

  @Override
  public byte[] process(byte[] data) {
    this.data.write(data, 0, data.length);
    return new byte[0];
  }

  @Override
  public byte[] doFinal() {
    byte[] input = data.toByteArray();
    int ctLen = encrypting ? input.length : input.length - TAG_LEN;
    if (!encrypting && input.length < TAG_LEN) {
      throw new IllegalArgumentException("密文过短，无法容纳认证标签");
    }

    // Poly1305 密钥 = 第 0 块前 32 字节；数据加解密用第 1 块起
    ChaCha20Engine polyKeyGen = new ChaCha20Engine();
    polyKeyGen.init(key, nonce, 0);
    byte[] polyKey = new byte[32];
    polyKeyGen.processBytes(polyKey, 0, 32, polyKey, 0);

    ChaCha20Engine cipher = new ChaCha20Engine();
    cipher.init(key, nonce, 1);
    byte[] processed = new byte[input.length];
    cipher.processBytes(input, 0, input.length, processed, 0);

    byte[] ct = encrypting ? processed : Arrays.copyOf(input, ctLen);

    poly1305.init(new com.flora.crypto.core.interfaces.material.param.KeyParameterImpl(polyKey));
    byte[] aadBytes = aad.toByteArray();
    poly1305.update(aadBytes, 0, aadBytes.length);
    pad(aadBytes.length);
    poly1305.update(ct, 0, ct.length);
    pad(ct.length);
    poly1305.update(length64(aadBytes.length), 0, 8);
    poly1305.update(length64(ct.length), 0, 8);
    lastTag = new byte[TAG_LEN];
    poly1305.doFinal(lastTag, 0);

    byte[] result;
    if (encrypting) {
      result = new byte[ctLen + TAG_LEN];
      System.arraycopy(processed, 0, result, 0, ctLen);
      System.arraycopy(lastTag, 0, result, ctLen, TAG_LEN);
    } else {
      byte[] receivedTag = new byte[TAG_LEN];
      System.arraycopy(input, ctLen, receivedTag, 0, TAG_LEN);
      if (!constantTimeEquals(receivedTag, lastTag)) {
        resetState();
        throw new IllegalStateException("ChaCha20-Poly1305 认证标签校验失败");
      }
      result = Arrays.copyOf(processed, ctLen);
    }
    resetState();
    return result;
  }

  @Override
  public int getMacSize() {
    return TAG_LEN;
  }

  private void pad(int len) {
    int n = (16 - (len % 16)) % 16;
    for (int i = 0; i < n; i++) {
      poly1305.update((byte) 0);
    }
  }

  private static byte[] length64(int len) {
    byte[] out = new byte[8];
    out[0] = (byte) len;
    out[1] = (byte) (len >>> 8);
    out[2] = (byte) (len >>> 16);
    out[3] = (byte) (len >>> 24);
    return out;
  }

  private static boolean constantTimeEquals(byte[] a, byte[] b) {
    if (a.length != b.length) {
      return false;
    }
    int r = 0;
    for (int i = 0; i < a.length; i++) {
      r |= a[i] ^ b[i];
    }
    return r == 0;
  }

  private void resetState() {
    aad.reset();
    data.reset();
  }

  @Override
  public AlgorithmFactory<? extends AEADBlockCipher> factory() {
    return FACTORY;
  }

  public static final AlgorithmFactory<AEADBlockCipher> FACTORY = new AlgorithmFactory<>() {
    @Override
    public Class<? extends AlgorithmFactoryRegister> registerTo() {
      return CryptoAlgorithmFactoryRegister.class;
    }

    @Override
    public Set<String> supportedAlgorithms() {
      return Set.of("ChaCha20Poly1305", "ChaCha20-Poly1305");
    }

    @Override
    public int priority() {
      return 0;
    }

    @Override
    public Class<AlgorithmComponent>[] componentTypes() {
      return new Class[0];
    }

    @Override
    public AEADBlockCipher construct(String algorithmName, AlgorithmComponent... components) {
      CheckUtil.notNull(algorithmName, "算法名不能为空");
      return new ChaCha20Poly1305();
    }
  };
}
