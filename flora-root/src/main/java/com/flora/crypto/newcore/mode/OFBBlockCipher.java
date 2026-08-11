package com.flora.crypto.newcore.mode;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.crypto.newcore.interfaces.material.param.ParameterWithIV;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import java.util.Set;

/**
 * OFB 模式（整块 OFB，纯 Java 链式）。
 * <p>包裹一个原始分组密码，把分组密码变成同步流密码。加密与解密使用完全相同的操作，
 * IV 作为初始状态。{@link #process(byte[])} 要求输入块对齐。</p>
 */
@ThreadFragile
public final class OFBBlockCipher implements BlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] IV;
    private final byte[] cfbV;
    private final byte[] cfbOutV;

    public OFBBlockCipher(BlockCipher cipher) {
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.IV = new byte[blockSize];
        this.cfbV = new byte[blockSize];
        this.cfbOutV = new byte[blockSize];
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params) {
        CipherParameter param = params;
        byte[] iv = null;
        if (param instanceof ParameterWithIV) {
            iv = ((ParameterWithIV) param).getIV();
            param = ((ParameterWithIV) param).getParameters();
        }
        CheckUtil.notNull(param, "缺少密钥参数");
        cipher.init(true, param);
        if (iv != null) {
            System.arraycopy(iv, 0, IV, 0, blockSize);
        }
        System.arraycopy(IV, 0, cfbV, 0, blockSize);
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName() + "/OFB";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        cipher.processBlock(cfbV, 0, cfbOutV, 0);
        for (int i = 0; i < blockSize; i++) {
            out[outOff + i] = (byte) (in[inOff + i] ^ cfbOutV[i]);
        }
        System.arraycopy(cfbOutV, 0, cfbV, 0, blockSize);
        return blockSize;
    }

    /** 便捷入口：一次性处理整段块对齐数据。 */
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        if (data.length % blockSize != 0) {
            throw new IllegalStateException("OFB 模式要求输入块对齐");
        }
        byte[] out = new byte[data.length];
        for (int off = 0; off < data.length; off += blockSize) {
            processBlock(data, off, out, off);
        }
        return out;
    }

    @Override
    public AlgorithmFamily<? extends BlockCipher> factory() {
        return FAMILY;
    }

    public static final AlgorithmFamily<BlockCipher> FAMILY = new AlgorithmFamily<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("OFB");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[]{BlockCipher.class};
        }

        @Override
        public BlockCipher construct(String algorithmName, AlgorithmComponent... components) {
            return new OFBBlockCipher((BlockCipher) components[0]);
        }
    };
}
