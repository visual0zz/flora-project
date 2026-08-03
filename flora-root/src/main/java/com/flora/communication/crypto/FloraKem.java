package com.flora.communication.crypto;

import com.flora.communication.KEM;
import com.flora.communication.KeyPair;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.engine.JdkKem;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import com.flora.crypto.core.interfaces.Decapsulator;
import java.security.spec.NamedParameterSpec;

/**
 * ML-KEM（后量子密钥封装）适配基类。
 * <p>封装机制委托 flora {@link JdkKem}（JDK {@code javax.crypto.KEM}），密钥对生成委托
 * {@link JdkKeyPairGenerator}；X.509 公钥提取与算法标识符等 SSH 协议逻辑保留在本层。</p>
 */
abstract class FloraKem implements KEM {

  protected NamedParameterSpec params;
  protected byte[] algorithmIdentifier;
  protected int publicKeyLen;

  private Decapsulator decapsulator;
  private byte[] publicKey;

  @Override
  public void init() throws Exception {
    java.security.KeyPair kp = JdkKeyPairGenerator.of("ML-KEM").generate(params);
    JdkKem kem = JdkKem.of("ML-KEM");
    decapsulator = kem.newDecapsulator(new AsymmetricKeyParameter(kp.getPrivate()));
    publicKey = KeyPair.extractX509SubjectPublicKeyInfo(kp.getPublic().getEncoded(),
        algorithmIdentifier, publicKeyLen);
  }

  @Override
  public byte[] getPublicKey() throws Exception {
    return publicKey;
  }

  @Override
  public byte[] decapsulate(byte[] encapsulation) throws Exception {
    return decapsulator.decapsulate(encapsulation).getSecret();
  }
}
