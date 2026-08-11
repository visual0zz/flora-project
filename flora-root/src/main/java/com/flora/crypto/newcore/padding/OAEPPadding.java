package com.flora.crypto.newcore.padding;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricScheme;
import com.flora.crypto.newcore.interfaces.algorithm.Digest;
import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.java.CheckUtil;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Set;

/**
 * EME-OAEP 消息编码方案（RFC 8017 §7.1）。
 * <p>编码块结构：{@code 0x00 ‖ maskedSeed ‖ maskedDB}，其中
 * DB = lHash ‖ PS(全零) ‖ 0x01 ‖ M，seed 为随机 hLen 字节。
 * 基于 MGF1 与哈希，标签 L 默认为空。块大小（密钥字节长度）在 {@link #init} 时
 * 从非对称密钥参数（{@link AsymmetricPublicKeyParameter} / {@link AsymmetricPrivateKeyParameter}）
 * 的字节长度推断。</p>
 */
public final class OAEPPadding implements AsymmetricScheme {

    private final Digest hash;
    private final Digest mgfHash;
    private final byte[] label;
    private final SecureRandom random;
    private int keyBytes = -1;

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
    public void init(boolean forEncryption, CipherParameter params, SecureRandom random) {
        this.keyBytes = inferKeyBytes(params);
    }

    private static int inferKeyBytes(CipherParameter params) {
        if (params instanceof AsymmetricPublicKeyParameter pub) {
            return pub.getPublicKey().length;
        }
        if (params instanceof AsymmetricPrivateKeyParameter priv) {
            return priv.getPrivateKey().length;
        }
        throw new IllegalArgumentException(
                "OAEP 需要非对称密钥参数（AsymmetricPublicKeyParameter / AsymmetricPrivateKeyParameter）以推断块大小");
    }

    @Override
    public String getAlgorithmName() {
        return "OAEP";
    }

    private int hLen() {
        return hash.getDigestResultSize();
    }

    @Override
    public int getInputBlockSize() {
        ensureKeyBytes();
        return keyBytes - 2 * hLen() - 2;
    }

    @Override
    public int getOutputBlockSize() {
        ensureKeyBytes();
        return keyBytes;
    }

    private void ensureKeyBytes() {
        if (keyBytes < 0) {
            throw new IllegalStateException("OAEP 尚未初始化（init 未调用）");
        }
    }

    @Override
    public byte[] encode(byte[] in, int inOff, int inLen) {
        ensureKeyBytes();
        CheckUtil.notNull(in, "输入不能为空");
        int hLen = hLen();
        if (inLen > keyBytes - 2 * hLen - 2) {
            throw new IllegalArgumentException("明文过长: " + inLen + " > " + (keyBytes - 2 * hLen - 2));
        }
        byte[] lHash = digestOf(label);
        int psLen = keyBytes - inLen - 2 * hLen - 2;
        byte[] db = new byte[keyBytes - hLen - 1];
        System.arraycopy(lHash, 0, db, 0, hLen);
        db[hLen + psLen] = 0x01;
        System.arraycopy(in, inOff, db, hLen + psLen + 1, inLen);
        byte[] seed = new byte[hLen];
        random.nextBytes(seed);
        Mgf1Generator mgf = new Mgf1Generator(mgfHash);
        byte[] dbMask = new byte[db.length];
        mgf.generateMask(seed, 0, seed.length, dbMask, 0, dbMask.length);
        for (int i = 0; i < db.length; i++) {
            db[i] ^= dbMask[i];
        }
        byte[] maskedDb = db;
        byte[] seedMask = new byte[hLen];
        mgf.generateMask(maskedDb, 0, maskedDb.length, seedMask, 0, seedMask.length);
        byte[] maskedSeed = new byte[hLen];
        for (int i = 0; i < hLen; i++) {
            maskedSeed[i] = (byte) (seed[i] ^ seedMask[i]);
        }
        byte[] em = new byte[keyBytes];
        em[0] = 0x00;
        System.arraycopy(maskedSeed, 0, em, 1, hLen);
        System.arraycopy(maskedDb, 0, em, 1 + hLen, maskedDb.length);
        return em;
    }

    @Override
    public byte[] decode(byte[] in, int inOff, int len) {
        ensureKeyBytes();
        CheckUtil.notNull(in, "输入不能为空");
        if (len != keyBytes) {
            throw new IllegalArgumentException("OAEP 块长度不符: " + len + " != " + keyBytes);
        }
        int hLen = hLen();
        if (in[inOff] != 0x00) {
            throw new IllegalArgumentException("OAEP 块首字节非零");
        }
        byte[] maskedSeed = new byte[hLen];
        System.arraycopy(in, inOff + 1, maskedSeed, 0, hLen);
        int dbLen = keyBytes - hLen - 1;
        byte[] maskedDb = new byte[dbLen];
        System.arraycopy(in, inOff + 1 + hLen, maskedDb, 0, dbLen);
        Mgf1Generator mgf = new Mgf1Generator(mgfHash);
        byte[] seedMask = new byte[hLen];
        mgf.generateMask(maskedDb, 0, maskedDb.length, seedMask, 0, seedMask.length);
        byte[] seed = new byte[hLen];
        for (int i = 0; i < hLen; i++) {
            seed[i] = (byte) (maskedSeed[i] ^ seedMask[i]);
        }
        byte[] dbMask = new byte[dbLen];
        mgf.generateMask(seed, 0, seed.length, dbMask, 0, dbMask.length);
        byte[] db = new byte[dbLen];
        for (int i = 0; i < dbLen; i++) {
            db[i] = (byte) (maskedDb[i] ^ dbMask[i]);
        }
        byte[] lHash = digestOf(label);
        if (!constantTimeEquals(lHash, 0, db, 0, hLen)) {
            throw new IllegalArgumentException("OAEP lHash 校验失败");
        }
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
        byte[] out = new byte[hash.getDigestResultSize()];
        hash.doFinal(out, 0);
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        return MessageDigest.isEqual(
                java.util.Arrays.copyOfRange(a, aOff, aOff + len),
                java.util.Arrays.copyOfRange(b, bOff, bOff + len));
    }

    @Override
    public AlgorithmFactory<? extends AsymmetricScheme> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AsymmetricScheme> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("OAEP");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[]{Digest.class};
        }

        @Override
        public AsymmetricScheme construct(String algorithmName, AlgorithmComponent... components) {
            return new OAEPPadding((Digest) components[0]);
        }
    };
}
