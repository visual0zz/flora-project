package com.flora.os.secret;

import com.flora.os.ffi.NativeLib;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * Windows Credential Manager 密钥存储实现（SESSION 级）。
 * <p>通过 FFM API 调用 advapi32 的 CredWriteW/CredReadW/CredDeleteW。
 * 使用 {@code CRED_PERSIST_SESSION}（随登录会话销毁，非持久化）。</p>
 */
class WinSecretStore implements SecretStore {

    private static final boolean AVAILABLE;
    private static final NativeLib ADVAPI;
    private static final int TYPE_GENERIC = 1;
    private static final int PERSIST_SESSION = 1;  // SESSION 级，随会话销毁
    private static final int CRED_ALLOWED = 0;

    // CREDENTIALW 结构体偏移（x64）
    private static final int OFF_TYPE      = 4;
    private static final int OFF_TARGET    = 8;
    private static final int OFF_BLOB_SIZE = 32;
    private static final int OFF_BLOB      = 40;
    private static final int OFF_PERSIST   = 48;
    private static final int OFF_USER      = 72;
    private static final int STRUCT_SIZE   = 80;

    static {
        boolean ok = false;
        NativeLib lib = null;
        try {
            lib = NativeLib.load("advapi32");
            ok = true;
        } catch (Exception ignored) {
        }
        AVAILABLE = ok;
        ADVAPI = lib;
    }

    static boolean isAvailable() { return AVAILABLE; }

    @Override
    public void store(String domain, String account, byte[] secret) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            MemorySegment userWide = wideString(account, arena);
            MemorySegment blob = arena.allocate(secret.length + 2);
            blob.copyFrom(MemorySegment.ofArray(secret));
            // null-terminate (UTF-16LE null word)
            blob.set(ValueLayout.JAVA_BYTE, secret.length, (byte) 0);
            blob.set(ValueLayout.JAVA_BYTE, secret.length + 1, (byte) 0);

            MemorySegment cred = arena.allocate(STRUCT_SIZE);
            cred.set(ValueLayout.JAVA_INT, OFF_TYPE, TYPE_GENERIC);
            cred.set(ValueLayout.ADDRESS, OFF_TARGET, targetWide);
            cred.set(ValueLayout.JAVA_INT, OFF_BLOB_SIZE, secret.length);
            cred.set(ValueLayout.ADDRESS, OFF_BLOB, blob);
            cred.set(ValueLayout.JAVA_INT, OFF_PERSIST, PERSIST_SESSION);
            cred.set(ValueLayout.ADDRESS, OFF_USER, userWide);

            boolean ok = ADVAPI.callInt("CredWriteW", cred, CRED_ALLOWED) != 0;
            if (!ok) throw new SecretStoreException("CredWriteW 失败");
        }
    }

    @Override
    public byte[] retrieve(String domain, String account) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            MemorySegment pCredPtr = arena.allocate(ValueLayout.ADDRESS.byteSize());

            boolean ok = ADVAPI.callInt("CredReadW", targetWide, TYPE_GENERIC, 0, pCredPtr) != 0;
            if (!ok) throw new SecretStoreException("凭据不存在: " + domain + "/" + account);

            MemorySegment cred = pCredPtr.get(ValueLayout.ADDRESS, 0)
                    .reinterpret(STRUCT_SIZE);
            int blobSize = cred.get(ValueLayout.JAVA_INT, OFF_BLOB_SIZE);
            MemorySegment blob = cred.get(ValueLayout.ADDRESS, OFF_BLOB)
                    .reinterpret(blobSize);
            byte[] result = blob.toArray(ValueLayout.JAVA_BYTE);

            ADVAPI.callVoid("CredFree", pCredPtr.get(ValueLayout.ADDRESS, 0));
            return result;
        }
    }

    @Override
    public void delete(String domain, String account) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            boolean ok = ADVAPI.callInt("CredDeleteW", targetWide, TYPE_GENERIC, 0) != 0;
            if (!ok) {
                int err = ADVAPI.callInt("GetLastError");
                if (err != 1168) // ERROR_NOT_FOUND
                    throw new SecretStoreException("CredDeleteW 失败, error=" + err);
            }
        }
    }

    @Override
    public String getProvider() { return "Windows Credential Manager (SESSION)"; }

    @Override
    public void close() {}

    private static MemorySegment wideString(String s, Arena arena) {
        byte[] encoded = s.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment seg = arena.allocate(encoded.length + 2);
        seg.copyFrom(MemorySegment.ofArray(encoded));
        seg.set(ValueLayout.JAVA_BYTE, encoded.length, (byte) 0);
        seg.set(ValueLayout.JAVA_BYTE, encoded.length + 1, (byte) 0);
        return seg;
    }
}
