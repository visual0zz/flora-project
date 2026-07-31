package com.flora.crypto.core.padding;

import com.flora.crypto.core.AsymmetricPadding;
import com.flora.crypto.core.Digest;
import com.flora.java.CheckUtil;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * EME-OAEP 填充（RFC 8017 §7.1）。
 * <p>加密块结构：{@code 0x00 ‖ maskedSeed ‖ maskedDB}，其中
 * DB = lHash ‖ PS(全零) ‖ 0x01 ‖ M，seed 为随机 hLen 字节。
 * 基于 MGF1 与哈希，标签 L 默认为空。</p>
 */
public final class OAEPPadding implements AsymmetricPadding {

    private final Digest hash;
    private final Digest mgfHash;
    private final byte[] label;
    private final SecureRandom random;

    public OAEPPadding(Digest hash) {
        this(hash, hash, null, new SecureRandom());
    }

    public OAEPPadding(Digest hash, Digest mgfHash) {
        this(hash, mgfHash, null, new SecureRandom());
    }

    public OAEPPadding(Digest hash, Digest mgfHash, byte[] label, SecureRandom random) {
        CheckUtil.notNull(hash, "哈希不能为空");
        CheckUtil.notNull(mgfHash, "MGF 哈希不能为空");
        this.hash = hash;
        this.mgfHash = mgfHash;
        this.label = label == null ? new byte[0] : label.clone();
        this.random = random;
    }

    @Override
    public String getPaddingName() {
        return "OAEP";
    }

    @Override
    public int getInputBlockSize(int keyBytes) {
        int hLen = hash.getDigestSize();
        return keyBytes - 2 * hLen - 2;
    }

    @Override
    public byte[] pad(byte[] in, int inOff, int inLen, int keyBytes) {
        CheckUtil.notNull(in, "输入不能为空");
        int hLen = hash.getDigestSize();
        if (inLen > keyBytes - 2 * hLen - 2) {
            throw new IllegalArgumentException("明文过长: " + inLen + " > " + (keyBytes - 2 * hLen - 2));
        }
        // lHash = H(label)
        byte[] lHash = digestOf(label);
        // DB = lHash || PS || 0x01 || M
        int psLen = keyBytes - inLen - 2 * hLen - 2;
        byte[] db = new byte[keyBytes - hLen - 1];
        System.arraycopy(lHash, 0, db, 0, hLen);
        db[hLen + psLen] = 0x01;
        System.arraycopy(in, inOff, db, hLen + psLen + 1, inLen);
        // seed 随机 hLen 字节
        byte[] seed = new byte[hLen];
        random.nextBytes(seed);
        // maskedDB = DB XOR MGF1(seed)
        Mgf1Generator mgf = new Mgf1Generator(mgfHash);
        byte[] dbMask = new byte[db.length];
        mgf.generateMask(seed, 0, seed.length, dbMask, 0, dbMask.length);
        for (int i = 0; i < db.length; i++) {
            db[i] ^= dbMask[i];
        }
        // maskedSeed = seed XOR MGF1(maskedDB)
        byte[] maskedDb = db;
        byte[] seedMask = new byte[hLen];
        mgf.generateMask(maskedDb, 0, maskedDb.length, seedMask, 0, seedMask.length);
        byte[] maskedSeed = new byte[hLen];
        for (int i = 0; i < hLen; i++) {
            maskedSeed[i] = (byte) (seed[i] ^ seedMask[i]);
        }
        // EM = 0x00 || maskedSeed || maskedDB
        byte[] em = new byte[keyBytes];
        em[0] = 0x00;
        System.arraycopy(maskedSeed, 0, em, 1, hLen);
        System.arraycopy(maskedDb, 0, em, 1 + hLen, maskedDb.length);
        return em;
    }

    @Override
    public byte[] unpad(byte[] in) throws IllegalArgumentException {
        CheckUtil.notNull(in, "输入不能为空");
        int hLen = hash.getDigestSize();
        if (in.length < 2 * hLen + 2) {
            throw new IllegalArgumentException("OAEP 块过短");
        }
        if (in[0] != 0x00) {
            throw new IllegalArgumentException("OAEP 块首字节非零");
        }
        byte[] maskedSeed = new byte[hLen];
        System.arraycopy(in, 1, maskedSeed, 0, hLen);
        int dbLen = in.length - hLen - 1;
        byte[] maskedDb = new byte[dbLen];
        System.arraycopy(in, 1 + hLen, maskedDb, 0, dbLen);
        // seed = maskedSeed XOR MGF1(maskedDB)
        Mgf1Generator mgf = new Mgf1Generator(mgfHash);
        byte[] seedMask = new byte[hLen];
        mgf.generateMask(maskedDb, 0, maskedDb.length, seedMask, 0, seedMask.length);
        byte[] seed = new byte[hLen];
        for (int i = 0; i < hLen; i++) {
            seed[i] = (byte) (maskedSeed[i] ^ seedMask[i]);
        }
        // DB = maskedDB XOR MGF1(seed)
        byte[] dbMask = new byte[dbLen];
        mgf.generateMask(seed, 0, seed.length, dbMask, 0, dbMask.length);
        byte[] db = new byte[dbLen];
        for (int i = 0; i < dbLen; i++) {
            db[i] = (byte) (maskedDb[i] ^ dbMask[i]);
        }
        // 校验 lHash
        byte[] lHash = digestOf(label);
        if (!constantTimeEquals(lHash, 0, db, 0, hLen)) {
            throw new IllegalArgumentException("OAEP lHash 校验失败");
        }
        // 找到 0x01 分隔符，前面 PS 必须全零
        int sep = -1;
        for (int i = hLen; i < dbLen; i++) {
            if (db[i] == 0x01) {
                sep = i;
                break;
            }
        }
        if (sep < 0) {
            throw new IllegalArgumentException("OAEP 缺少 0x01 分隔符");
        }
        for (int i = hLen; i < sep; i++) {
            if (db[i] != 0x00) {
                throw new IllegalArgumentException("OAEP PS 含非零字节");
            }
        }
        byte[] m = new byte[dbLen - sep - 1];
        System.arraycopy(db, sep + 1, m, 0, m.length);
        return m;
    }

    private byte[] digestOf(byte[] data) {
        hash.reset();
        hash.update(data, 0, data.length);
        byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return MessageDigest.isEqual(
                java.util.Arrays.copyOfRange(a, aOff, aOff + len),
                java.util.Arrays.copyOfRange(b, bOff, bOff + len));
    }
}
