package com.flora.crypto.core.engine;

import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.ParametersWithIV;
import com.flora.crypto.core.interfaces.provider.AEADBlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

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
  public void init(boolean forEncryption, CipherParameters params) {
    if (!(params instanceof ParametersWithIV p) || !(p.getParameters() instanceof KeyParameter)) {
      throw new IllegalArgumentException("ChaCha20Poly1305 需要 ParametersWithIV(KeyParameter, nonce)");
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
  public int getOutputSize(int len) {
    return encrypting ? len + TAG_LEN : Math.max(0, len - TAG_LEN);
  }

  @Override
  public int getUpdateOutputSize(int len) {
    return 0; // 缓冲全部输入，输出集中在 doFinal
  }

  @Override
  public void processAADByte(byte in) {
    aad.write(in & 0xff);
  }

  @Override
  public void processAADBytes(byte[] in, int inOff, int len) {
    aad.write(in, inOff, len);
  }

  @Override
  public int processByte(byte in, byte[] out, int outOff) {
    data.write(in & 0xff);
    return 0;
  }

  @Override
  public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
    data.write(in, inOff, len);
    return 0;
  }

  @Override
  public int doFinal(byte[] out, int outOff) {
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

    poly1305.init(new KeyParameter(polyKey));
    byte[] aadBytes = aad.toByteArray();
    poly1305.update(aadBytes, 0, aadBytes.length);
    pad(aadBytes.length);
    poly1305.update(ct, 0, ct.length);
    pad(ct.length);
    poly1305.update(length64(aadBytes.length), 0, 8);
    poly1305.update(length64(ct.length), 0, 8);
    lastTag = new byte[TAG_LEN];
    poly1305.doFinal(lastTag, 0);

    if (encrypting) {
      System.arraycopy(processed, 0, out, outOff, ctLen);
      System.arraycopy(lastTag, 0, out, outOff + ctLen, TAG_LEN);
      resetState();
      return ctLen + TAG_LEN;
    }
    // 解密：校验标签
    byte[] receivedTag = new byte[TAG_LEN];
    System.arraycopy(input, ctLen, receivedTag, 0, TAG_LEN);
    if (!MessageDigest.isEqual(receivedTag, lastTag)) {
      resetState();
      throw new IllegalStateException("ChaCha20-Poly1305 认证标签校验失败");
    }
    System.arraycopy(processed, 0, out, outOff, ctLen);
    resetState();
    return ctLen;
  }

  @Override
  public byte[] getMac() {
    return lastTag != null ? lastTag.clone() : new byte[0];
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

  private void resetState() {
    aad.reset();
    data.reset();
  }
}
