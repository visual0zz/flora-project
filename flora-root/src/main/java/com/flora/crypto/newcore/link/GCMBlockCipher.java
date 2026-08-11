package com.flora.crypto.newcore.link;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.AEADBlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.crypto.newcore.interfaces.material.param.ParameterWithIV;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Set;

/**
 * AES-GCM 自研实现（NIST SP 800-38D）。
 * <p>包裹裸分组密码原语（如 {@code BlockCipher("AES")}），完整自研 GCM：
 * GHASH（GF(2^128) 乘法，约化多项式 {@code x^128+x^7+x^2+x+1}）、计数器模式（inc32）、
 * AAD 认证、认证标签生成/校验。不依赖 JDK 的 {@code "AES/GCM/NoPadding"} 组合结构。</p>
 * <p>GCM 自遵循其自然的 AEAD 算法形态，不强求与链式模式一致：以 {@link #processAADBytes} 流式喂入 AAD，
 * 以整段 {@link #process(byte[])} 累积主数据，末尾 {@link #doFinal()} 产出「密文 ‖ 认证标签」
 * （加密）或校验标签后产出明文（解密）。</p>
 */
@ThreadFragile
public final class GCMBlockCipher implements AEADBlockCipher {

    /** GF(2^128) 约化多项式 R = x^7 + x^2 + x + 1 的高字节（0xE1 0^15） */
    private static final byte[] R = {(byte) 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    private final BlockCipher engine;
    private final int blockSize;
    private final int tagLen;

    private final byte[] h;       // H = E(K, 0^128)
    private final byte[] j0;      // 初始计数器
    private final byte[] s;       // GHASH 累加状态
    private final ByteArrayOutputStream aad = new ByteArrayOutputStream();
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();

    private boolean encrypting;

    public GCMBlockCipher(BlockCipher raw) {
        this(raw, 128);
    }

    /** @param tagBits 认证标签长度（位），须为 32 的倍数且在 [32, 128] */
    public GCMBlockCipher(BlockCipher raw, int tagBits) {
        CheckUtil.notNull(raw, "底层分组密码不能为空");
        if (tagBits % 32 != 0 || tagBits < 32 || tagBits > 128) {
            throw new IllegalArgumentException("tagBits 须为 32 的倍数且在 [32,128]: " + tagBits);
        }
        this.engine = raw;
        this.blockSize = raw.getBlockSize();
        if (blockSize != 16) {
            throw new IllegalArgumentException("GCM 需要 128 位块大小: " + blockSize);
        }
        this.tagLen = tagBits / 8;
        this.h = new byte[blockSize];
        this.j0 = new byte[blockSize];
        this.s = new byte[blockSize];
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params) {
        CheckUtil.notNull(params, "参数不能为空");
        this.encrypting = forEncryption;
        byte[] iv;
        if (params instanceof ParameterWithIV p) {
            iv = p.getIV();
            engine.init(true, p.getParameters());
        } else {
            throw new IllegalArgumentException("GCM 需要 ParameterWithIV（IV + 密钥）");
        }
        java.util.Arrays.fill(h, (byte) 0);
        engine.processBlock(h, 0, h, 0);
        computeJ0(iv);
        java.util.Arrays.fill(s, (byte) 0);
        aad.reset();
        data.reset();
    }

    @Override
    public String getAlgorithmName() {
        return "GCM";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    /** 累积整段主数据；加密/解密的实际产出与标签在 {@link #doFinal()} 交付。 */
    @Override
    public byte[] process(byte[] in) {
        CheckUtil.notNull(in, "数据不能为空");
        data.write(in, 0, in.length);
        return new byte[0];
    }

    private int getOutputSize(int len) {
        return encrypting ? len + tagLen : Math.max(0, len - tagLen);
    }

    @Override
    public void processAADBytes(byte[] in) {
        CheckUtil.notNull(in, "AAD 不能为空");
        aad.write(in, 0, in.length);
    }

    @Override
    public void processAADBytes(byte[] in, int inOff, int len) {
        CheckUtil.notNull(in, "AAD 不能为空");
        aad.write(in, inOff, len);
    }

    @Override
    public byte[] doFinal() {
        byte[] input = data.toByteArray();
        byte[] out = new byte[getOutputSize(input.length)];
        int n = doFinalInto(input, 0, input.length, out, 0);
        return java.util.Arrays.copyOf(out, n);
    }

    /** 核心：对 {@code in[inOff..inOff+len)} 执行 GCM 加解密并写 {@code out[outOff..]}。 */
    private int doFinalInto(byte[] in, int inOff, int len, byte[] out, int outOff) {
        byte[] input = new byte[len];
        System.arraycopy(in, inOff, input, 0, len);
        byte[] aadBytes = aad.toByteArray();

        if (encrypting) {
            byte[] ct = ctrProcess(input);
            byte[] tag = computeTag(aadBytes, ct);
            System.arraycopy(ct, 0, out, outOff, ct.length);
            System.arraycopy(tag, 0, out, outOff + ct.length, tagLen);
            resetState();
            return ct.length + tagLen;
        }
        if (len < tagLen) {
            throw new IllegalArgumentException("密文过短，无法容纳认证标签");
        }
        int ctLen = len - tagLen;
        byte[] ct = new byte[ctLen];
        System.arraycopy(input, 0, ct, 0, ctLen);
        byte[] inputTag = new byte[tagLen];
        System.arraycopy(input, ctLen, inputTag, 0, tagLen);
        byte[] expected = computeTag(aadBytes, ct);
        if (!MessageDigest.isEqual(expected, inputTag)) {
            throw new IllegalStateException("GCM 认证标签校验失败");
        }
        byte[] pt = ctrProcess(ct);
        System.arraycopy(pt, 0, out, outOff, pt.length);
        resetState();
        return pt.length;
    }

    @Override
    public int getMacSize() {
        return tagLen;
    }

    // ====== 内部 ======

    private void resetState() {
        java.util.Arrays.fill(s, (byte) 0);
        aad.reset();
        data.reset();
    }

    private void computeJ0(byte[] iv) {
        if (iv.length == 12) {
            System.arraycopy(iv, 0, j0, 0, 12);
            j0[12] = 0;
            j0[13] = 0;
            j0[14] = 0;
            j0[15] = 1;
        } else {
            java.util.Arrays.fill(s, (byte) 0);
            ghashUpdatePadded(iv);
            byte[] lenBlock = new byte[blockSize];
            putLong((long) iv.length * 8, lenBlock, 8);
            ghashUpdate(lenBlock);
            System.arraycopy(s, 0, j0, 0, blockSize);
            java.util.Arrays.fill(s, (byte) 0);
        }
    }

    private byte[] ctrProcess(byte[] input) {
        byte[] out = new byte[input.length];
        byte[] ctr = j0.clone();
        byte[] enc = new byte[blockSize];
        for (int off = 0; off < input.length; off += blockSize) {
            inc32(ctr);
            engine.processBlock(ctr, 0, enc, 0);
            int n = Math.min(blockSize, input.length - off);
            for (int i = 0; i < n; i++) {
                out[off + i] = (byte) (input[off + i] ^ enc[i]);
            }
        }
        return out;
    }

    private byte[] computeTag(byte[] aadBytes, byte[] ct) {
        java.util.Arrays.fill(s, (byte) 0);
        ghashUpdatePadded(aadBytes);
        ghashUpdatePadded(ct);
        byte[] lenBlock = new byte[blockSize];
        putLong((long) aadBytes.length * 8, lenBlock, 0);
        putLong((long) ct.length * 8, lenBlock, 8);
        ghashUpdate(lenBlock);
        byte[] eJ0 = new byte[blockSize];
        engine.processBlock(j0, 0, eJ0, 0);
        byte[] tag = new byte[tagLen];
        for (int i = 0; i < tagLen; i++) {
            tag[i] = (byte) (eJ0[i] ^ s[i]);
        }
        return tag;
    }

    private void ghashUpdatePadded(byte[] data) {
        int full = data.length / blockSize * blockSize;
        for (int off = 0; off < full; off += blockSize) {
            ghashUpdate(data, off);
        }
        if (full < data.length) {
            byte[] padded = new byte[blockSize];
            System.arraycopy(data, full, padded, 0, data.length - full);
            ghashUpdate(padded, 0);
        }
    }

    private void ghashUpdate(byte[] block) {
        ghashUpdate(block, 0);
    }

    private void ghashUpdate(byte[] block, int off) {
        for (int i = 0; i < blockSize; i++) {
            s[i] ^= block[off + i];
        }
        multiply(s, h);
    }

    private void multiply(byte[] x, byte[] y) {
        byte[] z = new byte[blockSize];
        byte[] v = new byte[blockSize];
        System.arraycopy(y, 0, v, 0, blockSize);
        for (int i = 0; i < 128; i++) {
            if ((x[i >>> 3] & (0x80 >>> (i & 7))) != 0) {
                for (int b = 0; b < blockSize; b++) {
                    z[b] ^= v[b];
                }
            }
            boolean lsb = (v[blockSize - 1] & 1) == 1;
            shiftRight(v);
            if (lsb) {
                for (int b = 0; b < blockSize; b++) {
                    v[b] ^= R[b];
                }
            }
        }
        System.arraycopy(z, 0, x, 0, blockSize);
    }

    private static void shiftRight(byte[] b) {
        int carry = 0;
        for (int i = 0; i < b.length; i++) {
            int next = b[i] & 0xff;
            b[i] = (byte) ((next >>> 1) | carry);
            carry = (next & 1) << 7;
        }
    }

    private static void inc32(byte[] ctr) {
        for (int i = ctr.length - 1; i >= ctr.length - 4; i--) {
            ctr[i] = (byte) (ctr[i] + 1);
            if (ctr[i] != 0) {
                break;
            }
        }
    }

    private static void putLong(long value, byte[] out, int off) {
        out[off] = (byte) (value >>> 56);
        out[off + 1] = (byte) (value >>> 48);
        out[off + 2] = (byte) (value >>> 40);
        out[off + 3] = (byte) (value >>> 32);
        out[off + 4] = (byte) (value >>> 24);
        out[off + 5] = (byte) (value >>> 16);
        out[off + 6] = (byte) (value >>> 8);
        out[off + 7] = (byte) value;
    }

    @Override
    public AlgorithmFactory<? extends AEADBlockCipher> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AEADBlockCipher> FACTORY =
            new AlgorithmFactory<>() {
                @Override
                public Class<? extends AlgorithmFamilyRegister> registerTo() {
                    return CryptoAlgorithmFamilyRegister.class;
                }

                @Override
                public Set<String> supportedAlgorithms() {
                    return Set.of("GCM");
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
                public AEADBlockCipher construct(
                        String algorithmName, AlgorithmComponent... components) {
                    return new GCMBlockCipher((BlockCipher) components[0]);
                }
            };
}
