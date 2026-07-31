package com.flora.crypto.core.mode;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.interfaces.provider.AEADBlockCipher;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.ParametersWithIV;

import com.flora.java.CheckUtil;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

/**
 * AES-GCM 自研实现（NIST SP 800-38D）。
 * <p>包裹裸分组密码原语（如 {@code JdkBlockCipher.of("AES")}），完整自研 GCM：
 * GHASH（GF(2^128) 乘法，约化多项式 {@code x^128+x^7+x^2+x+1}）、计数器模式（inc32）、
 * AAD 认证、认证标签生成/校验。不依赖 JDK 的 {@code "AES/GCM/NoPadding"} 组合结构。</p>
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
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        this.encrypting = forEncryption;
        byte[] iv;
        if (params instanceof ParametersWithIV p) {
            iv = p.getIV();
            engine.init(true, p.getParameters());
        } else {
            throw new IllegalArgumentException("GCM 需要 ParametersWithIV（IV + 密钥）");
        }
        // H = E(K, 0^128)
        java.util.Arrays.fill(h, (byte) 0);
        engine.processBlock(h, 0, h, 0);
        // J0：IV 96 位 → IV‖0^31‖1；否则 J0 = GHASH(H, IV)
        computeJ0(iv);
        java.util.Arrays.fill(s, (byte) 0);
        aad.reset();
        data.reset();
    }

    @Override
    public String getAlgorithmName() {
        return engine.getAlgorithmName() + "/GCM";
    }

    @Override
    public int getOutputSize(int len) {
        return encrypting ? len + tagLen : Math.max(0, len - tagLen);
    }

    @Override
    public int getUpdateOutputSize(int len) {
        return 0; // 本实现缓冲全部输入，输出集中在 doFinal
    }

    @Override
    public void processAADByte(byte in) {
        aad.write(in & 0xff);
    }

    @Override
    public void processAADBytes(byte[] in, int inOff, int len) {
        CheckUtil.notNull(in, "AAD 不能为空");
        aad.write(in, inOff, len);
    }

    @Override
    public int processByte(byte in, byte[] out, int outOff) {
        data.write(in & 0xff);
        return 0;
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
        CheckUtil.notNull(in, "数据不能为空");
        data.write(in, inOff, len);
        return 0;
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        CheckUtil.notNull(out, "输出不能为空");
        byte[] input = data.toByteArray();
        int totalLen = input.length;

        if (encrypting) {
            // 密文 = CTR(明文)，认证标签附于末尾
            byte[] ct = ctrProcess(input);
            byte[] tag = computeTag(aad.toByteArray(), ct);
            System.arraycopy(ct, 0, out, outOff, ct.length);
            System.arraycopy(tag, 0, out, outOff + ct.length, tagLen);
            resetState();
            return ct.length + tagLen;
        }
        // 解密：输入 = 密文主体 ‖ 认证标签
        if (totalLen < tagLen) {
            throw new IllegalArgumentException("密文过短，无法容纳认证标签");
        }
        int ctLen = totalLen - tagLen;
        byte[] ct = new byte[ctLen];
        System.arraycopy(input, 0, ct, 0, ctLen);
        byte[] inputTag = new byte[tagLen];
        System.arraycopy(input, ctLen, inputTag, 0, tagLen);
        byte[] expected = computeTag(aad.toByteArray(), ct);
        if (!MessageDigest.isEqual(expected, inputTag)) {
            throw new IllegalStateException("GCM 认证标签校验失败");
        }
        byte[] pt = ctrProcess(ct);
        System.arraycopy(pt, 0, out, outOff, pt.length);
        resetState();
        return pt.length;
    }

    @Override
    public byte[] getMac() {
        byte[] input = data.toByteArray();
        byte[] tag;
        if (encrypting) {
            byte[] ct = ctrProcess(input);
            tag = computeTag(aad.toByteArray(), ct);
        } else {
            // 解密方向：输入末尾即已接收的标签
            if (input.length >= tagLen) {
                tag = new byte[tagLen];
                System.arraycopy(input, input.length - tagLen, tag, 0, tagLen);
            } else {
                tag = new byte[0];
            }
        }
        return tag;
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
            // 长度块 = 0^64 || [len(IV)]_64（A 空、C = IV）
            byte[] lenBlock = new byte[blockSize];
            putLong((long) iv.length * 8, lenBlock, 8);
            ghashUpdate(lenBlock);
            System.arraycopy(s, 0, j0, 0, blockSize);
            java.util.Arrays.fill(s, (byte) 0);
        }
    }

    /** CTR 加解密（自反操作），计数器从 inc32(J0) 开始逐块递增。 */
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

    /** S = GHASH(H, AAD ‖ 0^v ‖ C ‖ 0^u ‖ [len(AAD)]_64 ‖ [len(C)]_64)，tag = E(J0) XOR S。 */
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

    /** 按块喂 GHASH，末尾不足一块补零。 */
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

    /** GF(2^128) 乘法：X = X * Y，就地更新。 */
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

    /** 无符号右移一位（字节序大端，从高位字节到低位字节传递进位）。 */
    private static void shiftRight(byte[] b) {
        int carry = 0;
        for (int i = 0; i < b.length; i++) {
            int next = b[i] & 0xff;
            b[i] = (byte) ((next >>> 1) | carry);
            carry = (next & 1) << 7;
        }
    }

    /** inc32：计数器低 32 位 +1。 */
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
}
