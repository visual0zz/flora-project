package com.flora.sanctum.kdbx.internal;

import com.flora.root.crypto.Argon2Kdf;
import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.kdbx.KdbxReadException;
import com.flora.sanctum.kdbx.KdbxReadException.Stage;

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
 * KDBX 文件解析与解密（参考 KeePass KDBX 4.0/4.1 规范）。
 * <p>流程：魔数/版本校验 → 头部字段解析 → 复合主密钥 + KDF → 头部 HMAC 校验
 * → 分块解密 → 解压 → 内层头（内层随机流）→ 内层 XML（受保护字段用内层流解密）。</p>
 *
 * <p>KDBX 4.0 与 4.1 的头部/块 HMAC 处理一致（HMAC-SHA256），已用 KeePassXC 官方文件逐字节验证。</p>
 * <p>失败统一抛 {@link KdbxReadException}（结构化），携带阶段/版本/cipherId/KDF uuid（均非敏感）。</p>
 */
public final class KdbxParser {

    private static final int SIG1 = 0x9AA2D903;
    private static final int SIG2 = 0xB54BFB67;

    /** 头部/块 HMAC 密钥派生用的 8 字节 0xFF。 */
    private static final byte[] HMAC_KEY_FILL_BYTES = {
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
    private final Logger log;
    private byte[] masterSeed;

    /** 解析过程中逐步得到的上下文，用于丰富异常信息（均非敏感）。 */
    private Integer majorVersion;
    private String cipherIdHex;
    private String kdfUuidHex;

    private KdbxParser(byte[] data, char[] password, byte[] keyFileBytes) {
        this(data, password, keyFileBytes, LoggerFactory.noOp());
    }

    private KdbxParser(byte[] data, char[] password, byte[] keyFileBytes, Logger log) {
        this.data = data;
        this.password = password;
        this.keyFileBytes = keyFileBytes;
        this.log = log;
    }

    /** 解析并解密为内存模型（默认静默日志）。 */
    public static KdbxDocument parse(byte[] data, char[] password, byte[] keyFileBytes) throws KdbxReadException {
        return parse(data, password, keyFileBytes, LoggerFactory.noOp());
    }

    /**
     * 解析并解密为内存模型。
     *
     * @param log 外部注入的日志器（用于记录解析期诊断，如自定义图标解码失败）；不配置路径，仅记录
     */
    public static KdbxDocument parse(byte[] data, char[] password, byte[] keyFileBytes, Logger log) throws KdbxReadException {
        return new KdbxParser(data, password, keyFileBytes, log).parse();
    }

    private KdbxReadException fail(Stage stage, String message) {
        return new KdbxReadException(stage, message, null, majorVersion, cipherIdHex, kdfUuidHex);
    }

    private KdbxReadException fail(Stage stage, String message, Throwable cause) {
        return new KdbxReadException(stage, message, cause, majorVersion, cipherIdHex, kdfUuidHex);
    }

    private KdbxDocument parse() throws KdbxReadException {
        if (data.length < 12) {
            throw fail(Stage.MAGIC, "文件过短，不是 KDBX 文件");
        }
        int sig1 = readLe32(0);
        int sig2 = readLe32(4);
        if (sig1 != SIG1 || sig2 != SIG2) {
            throw fail(Stage.MAGIC, "魔数不匹配，不是 KDBX 文件");
        }
        int version = readLe32(8);
        int major = (version >>> 16) & 0xFFFF;
        this.majorVersion = major;
        if (major != 2 && major != 3 && major != 4) {
            throw fail(Stage.MAGIC, "不支持的 KDBX 主版本: 0x" + Integer.toHexString(version));
        }

        boolean kdbx4 = (major == 4);
        int fieldLenWidth = kdbx4 ? 4 : 2; // KDBX2/3 头部字段长度为 2 字节，KDBX4 为 4 字节

        // ---- 头部字段 ----
        int pos = 12;
        UUID cipherId = null;
        int compression = 0;
        byte[] encryptionIV = null;
        byte[] kdfParamsBytes = null;
        // KDBX2/3 的 KDF 与内层流参数直接来自外层头部字段（非变体字典 / 内层头）
        byte[] transformSeed = null;        // 字段 5：AES-KDF 盐
        long transformRounds = 0;           // 字段 6：AES-KDF 轮数
        byte[] protectedStreamKey = null;   // 字段 8：内层随机流密钥
        byte[] streamStartBytes = null;     // 字段 9：变换密钥校验（解密后须匹配）
        int innerRandomStreamId = 2;        // 字段 10：内层随机流算法，默认 Salsa20
        int headerEnd = pos;
        while (pos < data.length) {
            int id = data[pos++] & 0xff;
            int len;
            if (fieldLenWidth == 4) {
                len = readLe32(pos);
                pos += 4;
            } else {
                len = readLe16(data, pos);
                pos += 2;
            }
            byte[] field = slice(pos, len);
            pos += len;
            headerEnd = pos; // 指向最后一个头部字段数据之后（含 End 字段）
            switch (id) {
                case 0: // End
                    pos = headerEnd; // 已到头部末尾
                    break;
                case 2:
                    cipherId = uuidFromBytes(field);
                    this.cipherIdHex = cipherId == null ? null : cipherId.toString();
                    break;
                case 3:
                    compression = readLe32(field, 0);
                    break;
                case 4:
                    masterSeed = field;
                    break;
                case 5: // TransformSeed（KDBX2/3 的 KDF 盐）
                    transformSeed = field;
                    break;
                case 6: // TransformRounds（KDBX2/3 的 KDF 轮数）
                    transformRounds = readLe64(field, 0);
                    break;
                case 7:
                    encryptionIV = field;
                    break;
                case 8: // ProtectedStreamKey（KDBX2/3 内层随机流密钥）
                    protectedStreamKey = field;
                    break;
                case 9: // StreamStartBytes（KDBX2/3 变换密钥校验）
                    streamStartBytes = field;
                    break;
                case 10: // InnerRandomStreamID（KDBX2/3 内层随机流算法）
                    innerRandomStreamId = readLe32(field, 0);
                    break;
                case 11: // KdfParameters（KDBX4 规范：字段 ID 11）
                    kdfParamsBytes = field;
                    break;
                case 12: // PublicCustomData（导入无需使用）
                default:
                    // 其余头部字段本导入器忽略
                    break;
            }
            if (id == 0) {
                break;
            }
        }

        if (cipherId == null || masterSeed == null || encryptionIV == null) {
            throw fail(Stage.HEADER, "KDBX 头部不完整");
        }
        if (kdbx4 && kdfParamsBytes == null) {
            throw fail(Stage.HEADER, "KDBX4 头部缺少 KDF 参数（字段 11）");
        }
        if (!KdbxCipher.isSupported(cipherId)) {
            throw fail(Stage.HEADER, "不支持的加密算法: " + cipherId);
        }

        // headerData = 12 字节魔数(sig1+sig2+版本) + 所有头部字段(含 End)。
        // KDBX4    ：headerData | SHA256(headerData) 32B | HMAC-SHA256(headerData) 32B
        // KDBX2/3  ：headerData 之后直接是加密载荷（KeePassXC 的 Kdbx3Reader 不写/不校验文件级头哈希，
        //            仅 KDBX3.1 在 XML 的 <HeaderHash> 中交叉校验；载荷开头的 StreamStartBytes 即完整性校验）。
        byte[] headerData = slice(0, headerEnd);
        if (kdbx4) {
            byte[] storedSha256 = slice(headerEnd, 32);
            if (!constantTimeEq(sha256(headerData), storedSha256)) {
                throw fail(Stage.HEADER_HASH, "文件头损坏（SHA256 校验失败）");
            }
        }

        // ---- 派生密钥（严格对齐 KeePassXC 源码）----
        // KDF 输入 = CompositeKey::rawKey = sha256( concat( 各分量 rawKey ) )，口令分量 rawKey = sha256(password)
        // transformedDatabaseKey = KDF 原始输出（直接作为变换后的数据库密钥，不做二次散列）
        // finalKey = sha256(masterSeed ‖ transformedDatabaseKey)
        byte[] transformedDatabaseKey;
        if (kdbx4) {
            Map<String, Object> kdf = parseVariantDictionary(kdfParamsBytes);
            transformedDatabaseKey = compositeKey(kdf);
        } else {
            transformedDatabaseKey = compositeKeyKdbx23(transformSeed, transformRounds);
        }
        byte[] finalKey = sha256(concat(masterSeed, transformedDatabaseKey));

        // ---- 解头后的载荷，按版本分派 ----
        if (kdbx4) {
            // KDBX4：SHA256(header) 之后是 HMAC-SHA256(header)，再分块（每块带 HMAC）
            byte[] storedHmac = slice(headerEnd + 32, 32);
            int payloadStart = headerEnd + 64;
            byte[] base = sha512(concat(masterSeed, transformedDatabaseKey, new byte[]{0x01})); // K_1
            byte[] computedHmac = headerHmac(base, headerData);
            if (!constantTimeEq(computedHmac, storedHmac)) {
                throw fail(Stage.HEADER_HMAC, "主密码错误或文件已损坏（头部 HMAC 校验失败）");
            }
            byte[] plaintext = decryptPayload(payloadStart, finalKey, base, cipherId, encryptionIV);
            byte[] inner = decompress(plaintext, compression);
            return KdbxXml.parse(inner, log); // KDBX4：TLV 内层头 + XML
        }

        // KDBX2/3：头部结束处即为单一加密载荷（AES-CBC，无逐块 HMAC、无文件级头哈希块）。
        // 注意：KeePassXC 的 Kdbx3Reader 不写/不校验文件级头哈希，载荷紧跟头部；
        // 而部分 KeePass 2.0 实现会在头部与载荷间额外写入 32 字节头哈希。两者都尝试，
        // 以解密后前 32 字节是否等于 StreamStartBytes 作为判别。
        // 解密后：前 32 字节为变换密钥校验（须等于 StreamStartBytes），随后为 HashedBlockStream 帧。
        byte[] plaintext = null;
        String decryptErr = null;
        int payloadStart = headerEnd;
        for (int off : new int[]{headerEnd, headerEnd + 32}) {
            if (off + 16 > data.length) {
                continue;
            }
            try {
                byte[] p = KdbxCipher.decrypt(finalKey, encryptionIV,
                        slice(off, data.length - off), cipherId);
                if (streamStartBytes == null || constantTimeEq(slice(p, 0, 32), streamStartBytes)) {
                    plaintext = p;
                    payloadStart = off;
                    break;
                }
            } catch (Exception e) {
                decryptErr = e.getMessage();
            }
        }
        if (plaintext == null) {
            throw fail(Stage.DECRYPT, "主密码错误或文件已损坏（载荷解密/变换密钥校验失败）"
                    + (decryptErr == null ? "" : "：" + decryptErr));
        }
        byte[] blockData = slice(plaintext, 32, plaintext.length - 32);
        byte[] xmlBytes = decodeHashedBlocks(blockData);
        byte[] xml = decompress(xmlBytes, compression);
        KdbxStreamCipher stream = new KdbxStreamCipher(innerRandomStreamId, protectedStreamKey);
        return KdbxXml.parseInner(xml, stream, log); // KDBX2/3：内层流来自外层头，无 TLV 内层头
    }

    private byte[] decryptPayload(int start, byte[] finalKey, byte[] base,
            UUID cipherId, byte[] encryptionIV) throws KdbxReadException {
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
                throw fail(Stage.DECRYPT, "载荷块越界，文件损坏");
            }
            byte[] cipherBlock = slice(pos, blockLen);
            pos += blockLen;

            // 块密钥 Ki = SHA512(i || K_1)，消息 = i || n || C（均小端），HMAC-SHA256
            byte[] blockKey = sha512(concat(le64(blockIndex), base));
            byte[] expected = hmacSha256(blockKey, concat(le64(blockIndex), le32Bytes(blockLen), cipherBlock));
            if (!constantTimeEq(expected, storedHmac)) {
                throw fail(Stage.DECRYPT, "载荷块校验失败，主密码错误或文件损坏");
            }

            try {
                out.write(KdbxCipher.decrypt(finalKey, encryptionIV, cipherBlock, cipherId));
            } catch (Exception e) {
                throw fail(Stage.DECRYPT, "载荷解密失败", e);
            }
            blockIndex++;
        }
        return out.toByteArray();
    }

    /** 头部 HMAC：密钥 = SHA512(0xFF*8 || K_1)，HMAC-SHA256(headerData)。 */
    private static byte[] headerHmac(byte[] base, byte[] headerData) {
        byte[] hmacKey = sha512(concat(HMAC_KEY_FILL_BYTES, base));
        return hmacSha256(hmacKey, headerData);
    }

    /** KDF 输入 = CompositeKey::rawKey = sha256( concat( 各分量 rawKey ) )，口令分量 rawKey = sha256(password)。 */
    private byte[] compositeKey(Map<String, Object> kdf) throws KdbxReadException {
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
            throw fail(Stage.KDF, "至少需要主密码或密钥文件");
        }
        byte[] kdfInput = sha256(rawKeys.toByteArray()); // CompositeKey::rawKey
        return runKdf(kdf, kdfInput); // transformedDatabaseKey = KDF 原始输出
    }

    /** KDBX2/3 的复合主密钥：外层头直接给出 AES-KDF 的盐(TransformSeed)与轮数(TransformRounds)。 */
    private byte[] compositeKeyKdbx23(byte[] transformSeed, long transformRounds) throws KdbxReadException {
        if (transformSeed == null || transformRounds <= 0) {
            throw fail(Stage.KDF, "KDBX2/3 缺少 AES-KDF 参数（TransformSeed 或 TransformRounds）");
        }
        ByteArrayOutputStream rawKeys = new ByteArrayOutputStream();
        boolean any = false;
        if (password != null && password.length > 0) {
            rawKeys.write(sha256(utf8(password)), 0, 32);
            any = true;
        }
        if (keyFileBytes != null && keyFileBytes.length > 0) {
            rawKeys.write(sha256(keyFileBytes), 0, 32);
            any = true;
        }
        if (!any) {
            throw fail(Stage.KDF, "至少需要主密码或密钥文件");
        }
        byte[] kdfInput = sha256(rawKeys.toByteArray()); // CompositeKey::rawKey
        return aesKdf(transformSeed, transformRounds, kdfInput);
    }

    /** KeePass AES-KDF（KDBX2/3 与 KDBX4 共用）：以盐为 AES-256-ECB 密钥，对输入逐轮加密 rounds 次，
     *  结果再 SHA-256 得 32 字节 transformed key。 */
    private byte[] aesKdf(byte[] salt, long rounds, byte[] input) throws KdbxReadException {
        if (salt == null) {
            throw fail(Stage.KDF, "AES-KDF 缺少盐");
        }
        if (rounds <= 0) {
            throw fail(Stage.KDF, "AES-KDF 轮数无效");
        }
        if (masterSeed == null) {
            throw fail(Stage.KDF, "AES-KDF 缺少 MasterSeed");
        }
        try {
            return com.flora.root.crypto.AesKdf.transform(input, salt, rounds);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw fail(Stage.KDF, "AES-KDF 计算失败: " + e.getMessage(), e);
        }
    }

    /** KDBX2/3 的 HashedBlockStream：每块 = [4 字节块索引][32 字节 SHA256][4 字节大小][数据]；终止块大小=0 且 hash 全零。 */
    private byte[] decodeHashedBlocks(byte[] data) throws KdbxReadException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int p = 0;
        long index = 0;
        while (p + 40 <= data.length) {
            long blockIndex = readLe32(data, p) & 0xFFFFFFFFL;
            p += 4;
            if (blockIndex != index) {
                throw fail(Stage.DECRYPT, "HashedBlock 索引不匹配，文件损坏");
            }
            byte[] hash = slice(data, p, 32);
            p += 32;
            long size = readLe32(data, p) & 0xFFFFFFFFL;
            p += 4;
            if (size == 0) {
                for (byte b : hash) {
                    if (b != 0) {
                        throw fail(Stage.DECRYPT, "HashedBlock 终止块校验失败");
                    }
                }
                break; // 终止块
            }
            if (p + size > data.length) {
                throw fail(Stage.DECRYPT, "HashedBlock 数据越界，文件损坏");
            }
            byte[] block = slice(data, p, (int) size);
            p += (int) size;
            if (!constantTimeEq(sha256(block), hash)) {
                throw fail(Stage.DECRYPT, "HashedBlock 校验失败，文件已损坏");
            }
            out.write(block, 0, (int) size);
            index++;
        }
        return out.toByteArray();
    }

    /** compression==1 时按 GZIP 解压，否则原样返回。 */
    private static byte[] decompress(byte[] data, int compression) throws KdbxReadException {
        if (compression == 1) {
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return readAll(gz);
            } catch (IOException e) {
                throw new KdbxReadException(Stage.DECRYPT, "解压失败", e);
            }
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private byte[] runKdf(Map<String, Object> kdf, byte[] input) throws KdbxReadException {
        byte[] uuidBytes = (byte[]) kdf.get("$UUID");
        if (uuidBytes == null) {
            throw fail(Stage.KDF, "缺少 KDF UUID");
        }
        UUID uuid = uuidFromBytes(uuidBytes);
        this.kdfUuidHex = uuid.toString();
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
                throw fail(Stage.KDF, "Argon2 参数不完整");
            }
            int memoryKiB = (int) (m / 1024);
            if (memoryKiB < 8 * p) {
                memoryKiB = (int) (8 * p);
            }
            return Argon2Kdf.deriveRaw(type, input, salt, memoryKiB, (int) it, (int) p, 32);
        }
        if (AESKDF_UUID.equals(uuid)) {
            long rounds = ((Number) kdf.getOrDefault("R", 0L)).longValue();
            byte[] salt = (byte[]) kdf.get("S");
            return aesKdf(salt, rounds, input);
        }
        throw fail(Stage.KDF, "不支持的 KDF: " + uuid);
    }

    // ===== 变体字典（KeePass KVP）=====

    private Map<String, Object> parseVariantDictionary(byte[] d) throws KdbxReadException {
        Map<String, Object> map = new HashMap<>();
        if (d.length < 2) {
            throw fail(Stage.HEADER, "变体字典为空");
        }
        int ver = readLe16(d, 0);
        if ((ver & 0xFF00) > 0x0100) { // VARIANTMAP_CRITICAL_MASK = 0xFF00, VARIANTMAP_VERSION = 0x0100
            throw fail(Stage.HEADER, "不支持的变体字典版本 0x" + Integer.toHexString(ver));
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
                    throw fail(Stage.HEADER, "变体字典未知类型 0x" + Integer.toHexString(type));
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
