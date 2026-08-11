package com.flora.crypto.newcore.wrapper;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.BufferedBlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.Padding;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.crypto.newcore.padding.PKCS7Padding;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Set;

/**
 * 带填充的分组密码缓冲装饰器。
 * <p>组合一个 {@link BlockCipher}（底层原语）与一个 {@link Padding}，使「逐块」引擎也能直接处理
 * 任意长度数据：加密时在末块加填充，解密时从末块去除填充。默认使用 PKCS7 填充。</p>
 */
@ThreadFragile
public final class PaddedBufferedBlockCipherWrapper implements BufferedBlockCipher {

    private final BlockCipher cipher;
    private final Padding padding;
    private final int blockSize;
    private boolean forEncryption;

    public PaddedBufferedBlockCipherWrapper(BlockCipher cipher, Padding padding) {
        CheckUtil.notNull(cipher, "底层分组密码不能为空");
        CheckUtil.notNull(padding, "填充策略不能为空");
        this.cipher = cipher;
        this.padding = padding;
        this.blockSize = cipher.getBlockSize();
    }

    public PaddedBufferedBlockCipherWrapper(BlockCipher cipher) {
        this(cipher, new PKCS7Padding());
    }

    public void init(boolean forEncryption, CipherParameter params) {
        this.forEncryption = forEncryption;
        cipher.init(forEncryption, params);
        padding.init(new SecureRandom());
    }

    public String getAlgorithmName() {
        return "PaddedBuffered";
    }

    public int getBlockSize() {
        return blockSize;
    }

    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        return process(data, 0, data.length);
    }

    public byte[] process(byte[] data, int off, int len) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = off;
        int end = off + len;
        while (pos + blockSize <= end) {
            byte[] block = new byte[blockSize];
            cipher.processBlock(data, pos, block, 0);
            out.write(block, 0, blockSize);
            pos += blockSize;
        }

        if (forEncryption) {
            byte[] last = new byte[blockSize];
            int rem = end - pos;
            System.arraycopy(data, pos, last, 0, rem);
            padding.addPadding(last, rem);
            byte[] block = new byte[blockSize];
            cipher.processBlock(last, 0, block, 0);
            out.write(block, 0, blockSize);
        } else {
            if (pos != end) {
                throw new IllegalStateException("密文长度不是块大小的整数倍，无法去填充");
            }
            byte[] full = out.toByteArray();
            int lastStart = full.length - blockSize;
            byte[] lastBlock = new byte[blockSize];
            System.arraycopy(full, lastStart, lastBlock, 0, blockSize);
            int pad = padding.padCount(lastBlock, 0);
            out.reset();
            out.write(full, 0, lastStart);
            out.write(lastBlock, 0, blockSize - pad);
        }
        return out.toByteArray();
    }

    /**
     * 收尾。本组合器在 {@link #process(byte[])} 中已一次性完成末块填充 / 去填充，
     * 整段处理后无遗留缓冲，故此处恒返回空数组。
     *
     * @return 空数组
     */
    @Override
    public byte[] doFinal() {
        return new byte[0];
    }

    @Override
    public AlgorithmFactory<? extends BufferedBlockCipher> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<BufferedBlockCipher> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("PaddedBuffered");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[]{BlockCipher.class, Padding.class};
        }

        @Override
        public BufferedBlockCipher construct(
                String algorithmName, AlgorithmComponent... components) {
            BlockCipher cipher = (BlockCipher) components[0];
            Padding padding = components.length > 1
                    ? (Padding) components[1]
                    : new PKCS7Padding();
            return new PaddedBufferedBlockCipherWrapper(cipher, padding);
        }
    };
}
