package com.flora.os.keyring;

import com.flora.os.ffi.NativeLib;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/** Windows Credential Manager 实现，通过 FFM API 调用 advapi32。 */
class WinCredentialStore implements Keyring {

    private static final NativeLib ADVAPI = NativeLib.load("advapi32");
    private static final int TYPE_GENERIC = 1;
    private static final int PERSIST_LOCAL_MACHINE = 2;
    private static final int CRED_ALLOWED = 0;

    // CREDENTIALW 结构体偏移（x64）
    private static final int OFF_TYPE      = 4;
    private static final int OFF_TARGET    = 8;
    private static final int OFF_BLOB_SIZE = 32;
    private static final int OFF_BLOB      = 40;
    private static final int OFF_PERSIST   = 48;
    private static final int OFF_USER      = 72;
    private static final int STRUCT_SIZE   = 80;

    @Override
    public void setPassword(String domain, String account, String password) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            MemorySegment userWide = wideString(account, arena);
            byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_16LE);
            MemorySegment blob = arena.allocate(pwdBytes.length + 2);
            blob.copyFrom(MemorySegment.ofArray(pwdBytes));

            MemorySegment cred = arena.allocate(STRUCT_SIZE);
            cred.set(ValueLayout.JAVA_INT, OFF_TYPE, TYPE_GENERIC);
            cred.set(ValueLayout.ADDRESS, OFF_TARGET, targetWide);
            cred.set(ValueLayout.JAVA_INT, OFF_BLOB_SIZE, pwdBytes.length);
            cred.set(ValueLayout.ADDRESS, OFF_BLOB, blob);
            cred.set(ValueLayout.JAVA_INT, OFF_PERSIST, PERSIST_LOCAL_MACHINE);
            cred.set(ValueLayout.ADDRESS, OFF_USER, userWide);

            boolean ok = ADVAPI.callInt("CredWriteW", cred, CRED_ALLOWED) != 0;
            if (!ok) throw new KeyringException("CredWriteW 失败");
        }
    }

    @Override
    public String getPassword(String domain, String account) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            MemorySegment pCredPtr = arena.allocate(ValueLayout.ADDRESS.byteSize());

            boolean ok = ADVAPI.callInt("CredReadW", targetWide, TYPE_GENERIC, 0, pCredPtr) != 0;
            if (!ok) throw new KeyringException("凭据不存在: " + domain + "/" + account);

            MemorySegment cred = pCredPtr.get(ValueLayout.ADDRESS, 0)
                    .reinterpret(STRUCT_SIZE);
            int blobSize = cred.get(ValueLayout.JAVA_INT, OFF_BLOB_SIZE);
            MemorySegment blob = cred.get(ValueLayout.ADDRESS, OFF_BLOB)
                    .reinterpret(blobSize);
            String result = new String(blob.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE)
                    .replace("\0", "");

            ADVAPI.callVoid("CredFree", pCredPtr.get(ValueLayout.ADDRESS, 0));
            return result;
        }
    }

    @Override
    public void deletePassword(String domain, String account) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            String target = domain + "|" + account;
            MemorySegment targetWide = wideString(target, arena);
            boolean ok = ADVAPI.callInt("CredDeleteW", targetWide, TYPE_GENERIC, 0) != 0;
            if (!ok) {
                int err = ADVAPI.callInt("GetLastError");
                if (err != 1168) // ERROR_NOT_FOUND
                    throw new KeyringException("CredDeleteW 失败, error=" + err);
            }
        }
    }

    @Override
    public String getStorageType() { return "Windows Credential Manager (FFM)"; }

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
