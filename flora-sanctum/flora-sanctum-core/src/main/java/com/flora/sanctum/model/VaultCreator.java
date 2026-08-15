package com.flora.sanctum.model;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.Envelope;
import com.flora.sanctum.crypto.SecureRandomSource;
import com.flora.sanctum.store.Base58;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.ObjectStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

/**
 * 新建库（见设计 02"manifest"与"文件夹 DEK"）。
 * <p>
 * 生成 salt、manifest（明文块 + MAC）、三个顶层 group（普通对象/icon/sshKey root，
 * 各持独立随机 KEK 包裹的 root DEK），写入库根。
 */
public final class VaultCreator {

    private final ObjectStore store;
    private final SecureRandomSource random = new SecureRandomSource();

    public VaultCreator(ObjectStore store) {
        this.store = store;
    }

    /**
     * 创建新库。默认高安全档 Argon2id 参数。
     */
    public void create(char[] masterPassword) {
        create(masterPassword, Argon2Kdf.DEFAULT_MEMORY_KIB, Argon2Kdf.DEFAULT_ITERATIONS, Argon2Kdf.DEFAULT_PARALLELISM);
    }

    public void create(char[] masterPassword, int memoryKiB, int iterations, int parallelism) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt, memoryKiB, iterations, parallelism);
        byte[] kek = kdf.derive(masterPassword);
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            Json.Node manifest = buildManifestJson(salt, memoryKiB, iterations, parallelism, macKey);
            writePlaintextBlock(manifest, Envelope.FLAG_PLAINTEXT);
            // 三个顶层 group：各持独立随机 DEK（用 KEK 包裹）
            for (String role : new String[]{"objects", "icon", "sshKey"}) {
                writeRootGroup(role, kek);
            }
        } finally {
            java.util.Arrays.fill(kek, (byte) 0);
        }
    }

    private Json.Node buildManifestJson(byte[] salt, int m, int i, int p, byte[] macKey) {
        Json.Node manifest = Json.obj();
        Json.put(manifest, "version", Json.of(1));
        Json.put(manifest, "type", Json.of("manifest"));
        Json.put(manifest, "cryptoVersion", Json.of("gcm-siv-1"));
        Json.put(manifest, "kdf", Json.of("argon2id"));
        Json.put(manifest, "salt", Json.of(Base64.getEncoder().encodeToString(salt)));
        Json.Node params = Json.obj();
        Json.put(params, "m", Json.of(m));
        Json.put(params, "i", Json.of(i));
        Json.put(params, "p", Json.of(p));
        Json.put(manifest, "params", params);
        Json.put(manifest, "warehouseTime", Json.of(1));
        Json.put(manifest, "updateTimestamp", Json.of(1));
        String canonical = "1|manifest|gcm-siv-1|argon2id|" + Base64.getEncoder().encodeToString(salt)
                + "|" + m + "," + i + "," + p + "|1|";
        byte[] mac = hmac(macKey, canonical.getBytes(StandardCharsets.UTF_8));
        Json.put(manifest, "mac", Json.of(Base64.getEncoder().encodeToString(mac)));
        return manifest;
    }

    private void writeRootGroup(String role, byte[] kek) {
        Json.Node group = Json.obj();
        Json.put(group, "version", Json.of(1));
        Json.put(group, "type", Json.of("group"));
        Json.put(group, "role", Json.of(role));
        Json.put(group, "parent", Json.ofNull());
        // 生成独立随机 DEK，用 KEK 包裹（存于 group 密文块内）
        byte[] dek = new byte[32];
        random.nextBytes(dek);
        byte[] wrapped = wrap(dek, kek);
        Json.put(group, "dek", Json.of(Base64.getEncoder().encodeToString(wrapped)));
        Json.put(group, "updateTimestamp", Json.of(1));
        writeCipherBlock(group, kek);
    }

    /** 用 KEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机）。 */
    private byte[] wrap(byte[] dek, byte[] kek) {
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, dek, random);
        return codec.encode(UUID.randomUUID(), dek, codec.makeKeyIdWith(kek));
    }

    private void writeCipherBlock(Json.Node payload, byte[] keyMaterial) {
        byte[] json = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(keyMaterial, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, keyMaterial, random);
        UUID uuid = UUID.randomUUID();
        byte[] block = codec.encode(uuid, json, codec.makeKeyIdWith(keyMaterial));
        try {
            Path f = ((com.flora.sanctum.store.impl.MarkdownObjectStore) store).root().resolve(uuid + ".md");
            Files.writeString(f, Base58.encode(block) + "\n");
        } catch (Exception e) {
            throw new IllegalStateException("write cipher block failed", e);
        }
    }

    private void writePlaintextBlock(Json.Node payload, byte flags) {
        byte[] json = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        byte[] block = new byte[6 + 16 + json.length];
        System.arraycopy(Envelope.MAGIC, 0, block, 0, 4);
        block[4] = Envelope.VERSION_1;
        block[5] = flags;
        UUID uuid = UUID.randomUUID();
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, 6, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, block, 22, json.length);
        byte xor = random.nextByte();
        byte[] obf = BlockHeader.obfuscate(block, xor);
        try {
            Path f = ((com.flora.sanctum.store.impl.MarkdownObjectStore) store).root().resolve(uuid + ".md");
            Files.writeString(f, Base58.encode(obf) + "\n");
        } catch (Exception e) {
            throw new IllegalStateException("write manifest/group failed", e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
