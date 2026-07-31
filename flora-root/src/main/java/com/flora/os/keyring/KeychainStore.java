package com.flora.os.keyring;

import com.flora.os.natives.ffm.NativeLib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/** macOS Keychain 实现，通过 FFM API 调用 Security.framework。 */
class KeychainStore implements Keyring {

    private static final NativeLib SEC = NativeLib.load(
            "/System/Library/Frameworks/Security.framework/Security");
    private static final int NO_SUCH_KEYCHAIN = -25293;

    @Override
    public void setPassword(String domain, String account, String password) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment svc = arena.allocateFrom(domain, StandardCharsets.UTF_8);
            MemorySegment acct = arena.allocateFrom(account, StandardCharsets.UTF_8);
            byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);
            MemorySegment pwd = arena.allocate(pwdBytes.length);
            pwd.copyFrom(MemorySegment.ofArray(pwdBytes));

            int rc = SEC.callInt("SecKeychainAddGenericPassword",
                    MemorySegment.NULL,                          // keychainRef = NULL (default)
                    domain.length(), svc,                         // serviceName
                    account.length(), acct,                       // accountName
                    pwdBytes.length, pwd,                         // passwordData
                    MemorySegment.NULL);                          // itemRef = NULL
            if (rc != 0 && rc != NO_SUCH_KEYCHAIN)
                throw new KeyringException("SecKeychainAddGenericPassword 失败: " + rc);
        }
    }

    @Override
    public String getPassword(String domain, String account) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment svc = arena.allocateFrom(domain, StandardCharsets.UTF_8);
            MemorySegment acct = arena.allocateFrom(account, StandardCharsets.UTF_8);

            // 输出参数
            MemorySegment pwdLenPtr = arena.allocate(ValueLayout.JAVA_INT.byteSize());
            MemorySegment pwdPtrPtr = arena.allocate(ValueLayout.ADDRESS.byteSize());

            int rc = SEC.callInt("SecKeychainFindGenericPassword",
                    MemorySegment.NULL,            // keychainRef
                    domain.length(), svc,
                    account.length(), acct,
                    pwdLenPtr, pwdPtrPtr,
                    MemorySegment.NULL);          // itemRef = NULL
            if (rc != 0)
                throw new KeyringException("凭据不存在: " + domain + "/" + account);

            MemorySegment pwdData = pwdPtrPtr.get(ValueLayout.ADDRESS, 0);
            int pwdLen = pwdLenPtr.get(ValueLayout.JAVA_INT, 0);
            String result = new String(pwdData.reinterpret(pwdLen).toArray(ValueLayout.JAVA_BYTE),
                    StandardCharsets.UTF_8);

            // 释放内容
            SEC.callVoid("SecKeychainItemFreeContent", MemorySegment.NULL, pwdData);
            return result;
        }
    }

    @Override
    public void deletePassword(String domain, String account) throws KeyringException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment svc = arena.allocateFrom(domain, StandardCharsets.UTF_8);
            MemorySegment acct = arena.allocateFrom(account, StandardCharsets.UTF_8);
            MemorySegment itemPtr = arena.allocate(ValueLayout.ADDRESS.byteSize());

            int rc = SEC.callInt("SecKeychainFindGenericPassword",
                    MemorySegment.NULL,
                    domain.length(), svc,
                    account.length(), acct,
                    MemorySegment.NULL, MemorySegment.NULL, itemPtr);
            if (rc != 0) return;

            MemorySegment item = itemPtr.get(ValueLayout.ADDRESS, 0);
            if (!item.equals(MemorySegment.NULL))
                SEC.callVoid("SecKeychainItemDelete", item);
        }
    }

    @Override
    public String getStorageType() { return "macOS Keychain (FFM)"; }

    @Override
    public void close() {}
}
