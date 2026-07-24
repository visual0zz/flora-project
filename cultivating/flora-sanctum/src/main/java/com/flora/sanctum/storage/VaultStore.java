package com.flora.sanctum.storage;

import com.flora.sanctum.crypto.AesGcmCipher;
import com.flora.sanctum.crypto.CryptoException;
import com.flora.sanctum.crypto.HmacSigner;
import com.flora.sanctum.model.Entry;
import com.flora.sanctum.model.Vault;
import com.flora.sanctum.model.VaultMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 保险库本地存储。
 * <p>
 * 管理 vault 目录下的明文元数据和加密条目文件：
 * <ul>
 *   <li>{@code meta.json} — 明文 VaultMeta（含 KDF 参数、盐值、远程信息）</li>
 *   <li>{@code meta.json.sig} — meta.json 的 HMAC-SHA256 签名</li>
 *   <li>{@code entries/} 目录 — 每个 Entry 一个加密文件，Base64 编码</li>
 * </ul>
 */
public class VaultStore {

    private static final String META_FILE = "meta.json";
    private static final String META_SIG_FILE = "meta.json.sig";
    private static final String ENTRIES_DIR = "entries";

    private VaultStore() {
    }

    // ======================== 创建 ========================

    /**
     * 在指定目录创建新保险库。
     *
     * @param vaultDir 保险库目录（必须不存在，或为空）
     * @param meta     保险库元数据
     * @param macKey   HMAC 密钥（32 字节），用于签名 meta.json
     * @return 已初始化的空保险库
     * @throws IOException 如果目录创建或文件写入失败
     */
    public static Vault create(Path vaultDir, VaultMeta meta, byte[] macKey) throws IOException {
        if (Files.exists(vaultDir)) {
            String[] existing = vaultDir.toFile().list();
            if (existing != null && existing.length > 0) {
                throw new IOException("vault directory is not empty: " + vaultDir);
            }
        } else {
            Files.createDirectories(vaultDir);
        }

        Path entriesDir = vaultDir.resolve(ENTRIES_DIR);
        Files.createDirectories(entriesDir);

        saveMeta(vaultDir, meta, macKey);

        return new Vault(meta, new ArrayList<>());
    }

    // ======================== 加载 ========================

    /**
     * 从指定目录加载保险库。
     *
     * @param vaultDir 保险库目录
     * @param encKey   AES-256-GCM 密钥（32 字节）
     * @param macKey   HMAC 密钥（32 字节）
     * @return 加载的保险库（含解密后的所有条目）
     * @throws IOException        如果文件读取失败
     * @throws CryptoException    如果解密失败或签名验证失败
     */
    public static Vault load(Path vaultDir, byte[] encKey, byte[] macKey) throws IOException {
        // 1. 加载元数据
        VaultMeta meta = loadMeta(vaultDir);

        // 2. 验证 meta.json 完整性
        if (!verifyMetaIntegrity(vaultDir, macKey)) {
            throw new CryptoException("meta.json integrity check failed: data may be tampered");
        }

        // 3. 加载所有条目
        List<Entry> entries = loadAllEntries(vaultDir, encKey);

        return new Vault(meta, entries);
    }

    // ======================== 元数据 ========================

    /**
     * 读取 meta.json（明文）。
     *
     * @param vaultDir 保险库目录
     * @return VaultMeta
     * @throws IOException 如果文件不存在或读取失败
     */
    public static VaultMeta loadMeta(Path vaultDir) throws IOException {
        Path metaFile = vaultDir.resolve(META_FILE);
        String json = Files.readString(metaFile, StandardCharsets.UTF_8);
        return VaultMeta.fromMap(JsonCodec.parseObject(json));
    }

    /**
     * 写入 meta.json 及其 HMAC 签名。
     *
     * @param vaultDir 保险库目录
     * @param meta     保险库元数据
     * @param macKey   HMAC 密钥（32 字节）
     * @throws IOException 如果文件写入失败
     */
    public static void saveMeta(Path vaultDir, VaultMeta meta, byte[] macKey) throws IOException {
        meta.touch();
        String json = JsonCodec.toJson(meta.toMap());
        Path metaFile = vaultDir.resolve(META_FILE);
        Files.writeString(metaFile, json, StandardCharsets.UTF_8);

        // 写入 HMAC 签名
        byte[] signature = HmacSigner.sign(json.getBytes(StandardCharsets.UTF_8), macKey);
        String sigBase64 = Base64.getEncoder().encodeToString(signature);
        Files.writeString(vaultDir.resolve(META_SIG_FILE), sigBase64, StandardCharsets.UTF_8);
    }

    /**
     * 验证 meta.json 的 HMAC 签名。
     *
     * @param vaultDir 保险库目录
     * @param macKey   HMAC 密钥（32 字节）
     * @return 签名有效返回 true，文件不存在或签名不匹配返回 false
     */
    public static boolean verifyMetaIntegrity(Path vaultDir, byte[] macKey) {
        try {
            Path metaFile = vaultDir.resolve(META_FILE);
            Path sigFile = vaultDir.resolve(META_SIG_FILE);
            if (!Files.exists(metaFile) || !Files.exists(sigFile)) {
                return false;
            }
            String json = Files.readString(metaFile, StandardCharsets.UTF_8);
            String sigBase64 = Files.readString(sigFile, StandardCharsets.UTF_8).trim();
            byte[] signature = Base64.getDecoder().decode(sigBase64);
            return HmacSigner.verify(json.getBytes(StandardCharsets.UTF_8), macKey, signature);
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== 条目操作 ========================

    /**
     * 加密并写入单条条目。
     *
     * @param vaultDir 保险库目录
     * @param entry    条目
     * @param encKey   AES-256-GCM 密钥（32 字节）
     * @throws IOException 如果文件写入失败
     */
    public static void saveEntry(Path vaultDir, Entry entry, byte[] encKey) throws IOException {
        entry.touch();
        String json = JsonCodec.toJson(entry.toMap());
        byte[] encrypted = AesGcmCipher.encrypt(json.getBytes(StandardCharsets.UTF_8), encKey);
        String base64 = Base64.getEncoder().encodeToString(encrypted);

        Path entryFile = vaultDir.resolve(ENTRIES_DIR).resolve(entry.getId() + ".enc");
        Files.writeString(entryFile, base64, StandardCharsets.UTF_8);
    }

    /**
     * 读取并解密单条条目。
     *
     * @param vaultDir 保险库目录
     * @param entryId  条目 ID（UUID 字符串）
     * @param encKey   AES-256-GCM 密钥（32 字节）
     * @return 解密后的条目，如果文件不存在返回 null
     * @throws IOException     如果文件读取失败
     * @throws CryptoException 如果解密失败
     */
    public static Entry loadEntry(Path vaultDir, String entryId, byte[] encKey) throws IOException {
        Path entryFile = vaultDir.resolve(ENTRIES_DIR).resolve(entryId + ".enc");
        if (!Files.exists(entryFile)) {
            return null;
        }
        String base64 = Files.readString(entryFile, StandardCharsets.UTF_8).trim();
        byte[] encrypted = Base64.getDecoder().decode(base64);
        byte[] decrypted = AesGcmCipher.decrypt(encrypted, encKey);
        String json = new String(decrypted, StandardCharsets.UTF_8);
        return Entry.fromMap(JsonCodec.parseObject(json));
    }

    /**
     * 删除单条条目文件。
     *
     * @param vaultDir 保险库目录
     * @param entryId  条目 ID（UUID 字符串）
     * @throws IOException 如果删除失败
     */
    public static void deleteEntry(Path vaultDir, String entryId) throws IOException {
        Path entryFile = vaultDir.resolve(ENTRIES_DIR).resolve(entryId + ".enc");
        Files.deleteIfExists(entryFile);
    }

    /**
     * 加载 entries/ 目录下的所有条目。
     */
    public static List<Entry> loadAllEntries(Path vaultDir, byte[] encKey) throws IOException {
        Path entriesDir = vaultDir.resolve(ENTRIES_DIR);
        if (!Files.exists(entriesDir)) {
            return new ArrayList<>();
        }

        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entriesDir, "*.enc")) {
            for (Path entryFile : stream) {
                String fileName = entryFile.getFileName().toString();
                String entryId = fileName.substring(0, fileName.length() - 4); // remove .enc
                Entry entry = loadEntry(vaultDir, entryId, encKey);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }
}
