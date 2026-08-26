package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportException;
import com.flora.sanctum.crypto.Argon2KDF;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * KDBX4 文件解析与解密（参考 KeePass KDBX 4.0/4.1 规范）。
 * <p>流程：魔数/版本校验 → 头部字段解析 → 复合主密钥 + KDF → 头部 HMAC 校验
 * → 分块解密 → 解压 → 内层头（内层随机流）→ 内层 XML（受保护字段用内层流解密）。</p>
 *
 * <p>KDBX 4.0 与 4.1 的头部/块 HMAC 处理一致（HMAC-SHA256），已用 KeePassXC 官方文件逐字节验证。</p>
 */
final class KdbxParser {

    private static final int SIG1 = 0x9AA2D903;
    private static final int SIG2 = 0xB54BFB67;

    /** 头部/块 HMAC 密钥派生用的 8 字节 0xFF。 */
    private static final byte[] FF8 = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };

    /** Argon2d KDF UUID（KeePass/KeePassXC 规范）。 */
    private static final UUID ARGON2_UUID = UUID.fromString("ef636ddf-8c29-444b-91f7-a9a403e30a0c");
    /** Argon2id KDF UUID。 */
    private static final UUID ARGON2ID_UUID = UUID.fromString("9e298b19-56db-4773-b23d-fc3ec6f0a1e6");
    /** AES-KDF UUID（KDBX3 沿用 / KDBX4 亦可）。 */
    private static final UUID AESKDF_UUID = UUID.fromString("c9d9f39a-628a-4460-bf74-0d08c18a4fea");

    private final byte[] data;
    private final char[] password;
    private final byte[] keyFileBytes;
    private byte[] masterSeed;

    private KdbxParser(byte[] data, char[] password, byte[] keyFileBytes) {
        this.data = data;
        this.password = password;
        this.keyFileBytes = keyFileBytes;
    }

    /** 解析并解密为内存模型。 */
    static KdbxDocument parse(byte[] data, char[] password, byte[] keyFileBytes) throws ImportException {
        return new KdbxParser(data, password, keyFileBytes).parse();
    }

    private KdbxDocument parse() throws ImportException {
        if (data.length < 12) {
            throw new ImportException("文件过短，不是 KDBX 文件");
        }
        int sig1 = readLe32(0);
        int sig2 = readLe32(4);
        if (sig1 != SIG1 || sig2 != SIG2) {
            throw new ImportException("魔数不匹配，不是 KDBX 文件");
        }
        int version = readLe32(8);
        int major = (version >>> 16) & 0xFFFF;
        if (major != 4) {
            throw new ImportException("仅支持 KDBX4，当前文件版本 0x" + Integer.toHexString(version));
        }

        // ---- 头部字段 ----
        int pos = 12;
        UUID cipherId = null;
        int compression = 0;
        byte[] encryptionIV = null;
        byte[] kdfParamsBytes = null;
        int headerEnd = pos;
        while (pos < data.length) {
            int id = data[pos++] & 0xff;
            int len = readLe32(pos);
            pos += 4;
            byte[] field = slice(pos, len);
            pos += len;
            headerEnd = pos; // 指向最后一个头部字段数据之后（含 End 字段）
            switch (id) {
                case 0: // End
                    pos = headerEnd; // 已到头部末尾
                    break;
                case 2:
                    cipherId = uuidFromBytes(field);
                    break;
                case 3:
                    compression = readLe32(field, 0);
                    break;
                case 4:
                    masterSeed = field;
                    break;
                case 7:
                    encryptionIV = field;
                    break;
                case 11: // KdfParameters（KDBX4 规范：字段 ID 11）
                    kdfParamsBytes = field;
                    break;
                case 10: // InnerRandomStreamID（KDBX4 已移入内层头，外层忽略）
                case 12: // PublicCustomData（导入无需使用）
                default:
                    // 其余头部字段本导入器忽略
                    break;
            }
            if (id == 0) {
                break;
            }
        }

        if (cipherId == null || masterSeed == null || encryptionIV == null || kdfParamsBytes == null) {
            throw new ImportException("KDBX 头部不完整");
        }
        if (!KdbxCipher.isSupported(cipherId)) {
            throw new ImportException("不支持的加密算法: " + cipherId);
        }

        // headerData = 12 字节魔数(sig1+sig2+版本) + 所有头部字段(含 End)。
        // KDBX 4.0/4.1 布局一致：headerData | SHA256(headerData) 32B | HMAC-SHA256(headerData) 32B
        byte[] headerData = slice(0, headerEnd);
        byte[] storedSha256 = slice(headerEnd, 32);
        if (!constantTimeEq(sha256(headerData), storedSha256)) {
            throw new ImportException("文件头损坏（SHA256 校验失败）");
        }
        byte[] storedHmac = slice(headerEnd + 32, 32);
        int payloadStart = headerEnd + 64;

        // ---- 派生密钥（严格对齐 KeePassXC 源码）----
        // KDF 输入 = CompositeKey::rawKey = sha256( concat( 各分量 rawKey ) )，口令分量 rawKey = sha256(password)
        // transformedDatabaseKey = KDF 原始输出（KDF 后不再 sha256）
        // finalKey = sha256(masterSeed ‖ transformedDatabaseKey)
        // K_1 = sha512(masterSeed ‖ transformedDatabaseKey ‖ 0x01)
        Map<String, Object> kdf = parseVariantDictionary(kdfParamsBytes);
        byte[] transformedDatabaseKey = compositeKey(kdf);
        byte[] finalKey = sha256(concat(masterSeed, transformedDatabaseKey));

        // ---- 校验头部 HMAC 认证 ----
        byte[] base = sha512(concat(masterSeed, transformedDatabaseKey, new byte[]{0x01})); // K_1
        byte[] computedHmac = headerHmac(base, headerData);
        if (!constantTimeEq(computedHmac, storedHmac)) {
            throw new ImportException("主密码错误或文件已损坏");
        }

        // ---- 解密载荷块 ----
        byte[] plaintext = decryptPayload(payloadStart, finalKey, base, cipherId, encryptionIV);

        // ---- 解压 ----
        byte[] inner;
        if (compression == 1) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(plaintext))) {
                inner = readAll(gz);
            } catch (IOException e) {
                throw new ImportException("GZip 解压失败", e);
            }
        } else {
            inner = plaintext;
        }

        // ---- 内层头 + XML ----
        return KdbxXml.parse(inner);
    }

    private byte[] decryptPayload(int start, byte[] finalKey, byte[] base,
            UUID cipherId, byte[] encryptionIV) throws ImportException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = start;
        long blockIndex = 0;
        while (pos + 36 <= data.length) {
            byte[] storedHmac = slice(pos, 32);
            pos += 32;
            int blockLen = readLe32(pos);
            pos += 4;
            if (blockLen == 0) {
                break; // 终止块
            }
            if (pos + blockLen > data.length) {
                throw new ImportException("载荷块越界，文件损坏");
            }
            byte[] cipherBlock = slice(pos, blockLen);
            pos += blockLen;

            // 块密钥 Ki = SHA512(i || K_1)，消息 = i || n || C（均小端），HMAC-SHA256
            byte[] blockKey = sha512(concat(le64(blockIndex), base));
            byte[] expected = hmacSha256(blockKey, concat(le64(blockIndex), le32Bytes(blockLen), cipherBlock));
            if (!constantTimeEq(expected, storedHmac)) {
                throw new ImportException("载荷块校验失败，主密码错误或文件损坏");
            }

            try {
                out.write(KdbxCipher.decrypt(finalKey, encryptionIV, cipherBlock, cipherId));
            } catch (Exception e) {
                throw new ImportException("载荷解密失败", e);
            }
            blockIndex++;
        }
        return out.toByteArray();
    }

    /** 头部 HMAC：密钥 = SHA512(0xFF*8 || K_1)，HMAC-SHA256(headerData)。 */
    private static byte[] headerHmac(byte[] base, byte[] headerData) {
        byte[] hmacKey = sha512(concat(FF8, base));
        return hmacSha256(hmacKey, headerData);
    }

    /** KDF 输入 = CompositeKey::rawKey = sha256( concat( 各分量 rawKey ) )，口令分量 rawKey = sha256(password)。 */
    private byte[] compositeKey(Map<String, Object> kdf) throws ImportException {
        ByteArrayOutputStream rawKeys = new ByteArrayOutputStream();
        boolean any = false;
        if (password != null && password.length > 0) {
            rawKeys.write(sha256(utf8(password)), 0, 32); // 口令分量 rawKey = sha256(password)
            any = true;
        }
        if (keyFileBytes != null && keyFileBytes.length > 0) {
            rawKeys.write(sha256(keyFileBytes), 0, 32); // 哈希型密钥文件：rawKey = sha256(内容)
            any = true;
        }
        if (!any) {
            throw new ImportException("至少需要主密码或密钥文件");
        }
        byte[] kdfInput = sha256(rawKeys.toByteArray()); // CompositeKey::rawKey
        return runKdf(kdf, kdfInput); // transformedDatabaseKey = KDF 原始输出
    }

    @SuppressWarnings("unchecked")
    private byte[] runKdf(Map<String, Object> kdf, byte[] input) throws ImportException {
        byte[] uuidBytes = (byte[]) kdf.get("$UUID");
        if (uuidBytes == null) {
            throw new ImportException("缺少 KDF UUID");
        }
        UUID uuid = uuidFromBytes(uuidBytes);
        if (ARGON2_UUID.equals(uuid) || ARGON2ID_UUID.equals(uuid)) {
            byte[] salt = (byte[]) kdf.get("S");
            long m = ((Number) kdf.getOrDefault("M", 0L)).longValue();
            long it = ((Number) kdf.getOrDefault("I", 0L)).longValue();
            long p = ((Number) kdf.getOrDefault("P", 0L)).longValue();
            // Argon2 类型由 $UUID 决定（Argon2D / Argon2ID）；若变体字典含 "T" 则以它为准。
            int type;
            if (kdf.containsKey("T")) {
                type = ((Number) kdf.get("T")).intValue();
            } else {
                type = ARGON2_UUID.equals(uuid) ? 0 : 2; // 0=Argon2d, 2=Argon2id
            }
            if (salt == null || m <= 0 || it <= 0 || p <= 0) {
                throw new ImportException("Argon2 参数不完整");
            }
            int memoryKiB = (int) (m / 1024);
            if (memoryKiB < 8 * p) {
                memoryKiB = (int) (8 * p);
            }
            return Argon2KDF.deriveRaw(type, input, salt, memoryKiB, (int) it, (int) p, 32);
        }
        if (AESKDF_UUID.equals(uuid)) {
            long rounds = ((Number) kdf.getOrDefault("R", 0L)).longValue();
            if (rounds <= 0) {
                throw new ImportException("AES-KDF 轮数无效");
            }
            if (masterSeed == null) {
                throw new ImportException("AES-KDF 缺少 MasterSeed");
            }
            try {
                // KeePass AES-KDF（KDBX4，UUID c9d9f39a-...）：以 KDF 盐 S 为 AES-256-ECB 密钥，
                // 对复合主密钥逐轮加密 rounds 次，结果再 SHA-256 得 32 字节 transformed key。
                byte[] salt = (byte[]) kdf.get("S");
                if (salt == null) {
                    throw new ImportException("AES-KDF 缺少盐 S");
                }
                javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
                c.init(javax.crypto.Cipher.ENCRYPT_MODE,
                        new javax.crypto.spec.SecretKeySpec(salt, "AES"));
                byte[] transformed = input.clone();
                for (long i = 0; i < rounds; i++) {
                    transformed = c.doFinal(transformed);
                }
                return sha256(transformed);
            } catch (javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException
                     | java.security.InvalidKeyException e) {
                throw new ImportException("AES-KDF 计算失败: " + e.getMessage());
            } catch (java.security.NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
                throw new ImportException("不支持 AES-KDF: " + e.getMessage());
            }
        }
        throw new ImportException("不支持的 KDF: " + uuid);
    }

    // ===== 变体字典（KeePass KVP）=====

    private static Map<String, Object> parseVariantDictionary(byte[] d) throws ImportException {
        Map<String, Object> map = new HashMap<>();
        if (d.length < 2) {
            throw new ImportException("变体字典为空");
        }
        int ver = readLe16(d, 0);
        if ((ver & 0xFF00) > 0x0100) { // VARIANTMAP_CRITICAL_MASK = 0xFF00, VARIANTMAP_VERSION = 0x0100
            throw new ImportException("不支持的变体字典版本 0x" + Integer.toHexString(ver));
        }
        int p = 2;
        while (p < d.length) {
            int type = d[p++] & 0xff;
            if (type == 0x00) { // End
                break;
            }
            int nameLen = readLe32(d, p);
            p += 4;
            String name = new String(d, p, nameLen, java.nio.charset.StandardCharsets.UTF_8);
            p += nameLen;
            int valLen = readLe32(d, p);
            p += 4;
            switch (type) {
                case 0x04: // UInt32
                    map.put(name, readLe32(d, p));
                    p += 4;
                    break;
                case 0x05: // UInt64
                    map.put(name, readLe64(d, p));
                    p += 8;
                    break;
                case 0x08: // Bool
                    map.put(name, d[p] != 0);
                    p += 1;
                    break;
                case 0x0C: // Int32
                    map.put(name, readLe32(d, p));
                    p += 4;
                    break;
                case 0x0D: // Int64
                    map.put(name, readLe64(d, p));
                    p += 8;
                    break;
                case 0x18: // String (UTF-8)
                    map.put(name, new String(d, p, valLen, java.nio.charset.StandardCharsets.UTF_8));
                    p += valLen;
                    break;
                case 0x42: // ByteArray
                    map.put(name, slice(d, p, valLen));
                    p += valLen;
                    break;
                default:
                    throw new ImportException("变体字典未知类型 0x" + Integer.toHexString(type));
            }
        }
        return map;
    }

    private static int readLe16(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8;
    }

    // ===== 字节工具 =====

    private byte[] slice(int off, int len) {
        return slice(data, off, len);
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(b, off, r, 0, len);
        return r;
    }

    private int readLe32(int off) {
        return readLe32(data, off);
    }

    private static int readLe32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }

    private static long readLe64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xffL) << (8 * i);
        }
        return v;
    }

    private static byte[] le64(long v) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (v >>> (8 * i));
        }
        return b;
    }

    private static byte[] le32Bytes(int v) {
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static UUID uuidFromBytes(byte[] b) {
        // KeePass UUID 为 16 字节大端（高位在前）
        long high = readBe64(b, 0);
        long low = readBe64(b, 8);
        return new UUID(high, low);
    }

    private static long readBe64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[off + i] & 0xffL);
        }
        return v;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    private static byte[] utf8(char[] cs) {
        StringBuilder sb = new StringBuilder(cs.length);
        sb.append(cs);
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha512(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] msg) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(msg);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEq(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length; i++) {
            r |= a[i] ^ b[i];
        }
        return r == 0;
    }

    private static byte[] readAll(GZIPInputStream gz) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = gz.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
