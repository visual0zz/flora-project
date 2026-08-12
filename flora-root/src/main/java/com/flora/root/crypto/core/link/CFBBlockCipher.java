package com.flora.root.crypto.core.link;

import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.root.crypto.core.interfaces.algorithm.BlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.LinkedBlockCipher;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.root.crypto.core.interfaces.material.param.ParameterWithIV;
import com.flora.root.java.CheckUtil;
import com.flora.root.tag.ThreadFragile;

import java.util.Set;

/**
 * CFB 模式（整块 CFB128，纯 Java 链式）。
 * <p>包裹一个原始分组密码，把分组密码变成自同步流密码。本实现采用整块反馈，
 * IV 作为初始状态。{@link #process(byte[])} 要求输入块对齐。</p>
 */
@ThreadFragile
public final class CFBBlockCipher implements LinkedBlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] IV;
    private final byte[] cfbV;
    private final byte[] cfbOutV;
    private final byte[] buf;
    private int bufOff;
    private boolean encrypting;

    public CFBBlockCipher(BlockCipher cipher) {
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.IV = new byte[blockSize];
        this.cfbV = new byte[blockSize];
        this.cfbOutV = new byte[blockSize];
        this.buf = new byte[blockSize];
        this.bufOff = 0;
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params) {
        this.encrypting = forEncryption;
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
        bufOff = 0;
    }

    @Override
    public String getAlgorithmName() {
        return "CFB";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    private int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        cipher.processBlock(cfbV, 0, cfbOutV, 0);
        for (int i = 0; i < blockSize; i++) {
            out[outOff + i] = (byte) (in[inOff + i] ^ cfbOutV[i]);
        }
        if (encrypting) {
            System.arraycopy(out, outOff, cfbV, 0, blockSize);
        } else {
            System.arraycopy(in, inOff, cfbV, 0, blockSize);
        }
        return blockSize;
    }

    @Override
    public byte[] update(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        return update(data, 0, data.length);
    }

    @Override
    public byte[] update(byte[] data, int off, int len) {
        CheckUtil.notNull(data, "数据不能为空");
        byte[] out = new byte[len];
        int outPos = 0;
        int pos = off;
        int end = off + len;
        while (pos < end) {
            buf[bufOff++] = data[pos++];
            if (bufOff == blockSize) {
                processBlock(buf, 0, out, outPos);
                outPos += blockSize;
                bufOff = 0;
            }
        }
        return outPos == 0 ? new byte[0] : java.util.Arrays.copyOf(out, outPos);
    }

    @Override
    public byte[] doFinal() {
        if (bufOff != 0) {
            throw new IllegalStateException("CFB 模式要求输入块对齐");
        }
        return new byte[0];
    }

    @Override
    public AlgorithmFactory<? extends LinkedBlockCipher> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<LinkedBlockCipher> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("CFB");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[]{BlockCipher.class};
        }

        @Override
        public LinkedBlockCipher construct(String algorithmName, AlgorithmComponent... components) {
            return new CFBBlockCipher((BlockCipher) components[0]);
        }
    };
}
