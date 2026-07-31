package com.flora.os.secret;

import com.flora.os.ffi.NativeLib;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * macOS Security.framework 密钥存储实现。
 * <p>通过 FFM API 直接调用 Security.framework 的 Keychain 服务。
 * 密钥数据持久化在用户登录 Keychain 中（系统行为无法绕过）。</p>
 */
class MacSecretStore implements SecretStore {

    private static final boolean AVAILABLE;
    private static final NativeLib SEC;
    private static final int NO_SUCH_KEYCHAIN = -25293;

    static {
        boolean ok = false;
        NativeLib lib = null;
        try {
            lib = NativeLib.load("/System/Library/Frameworks/Security.framework/Security");
            ok = true;
        } catch (Exception ignored) {
        }
        AVAILABLE = ok;
        SEC = lib;
    }

    static boolean isAvailable() { return AVAILABLE; }

    @Override
    public void store(String domain, String account, byte[] secret) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment svc = arena.allocateFrom(domain, StandardCharsets.UTF_8);
            MemorySegment acct = arena.allocateFrom(account, StandardCharsets.UTF_8);
            MemorySegment pwd = arena.allocate(secret.length);
            pwd.copyFrom(MemorySegment.ofArray(secret));

            int rc = SEC.callInt("SecKeychainAddGenericPassword",
                    MemorySegment.NULL,
                    domain.length(), svc,
                    account.length(), acct,
                    secret.length, pwd,
                    MemorySegment.NULL);
            if (rc != 0 && rc != NO_SUCH_KEYCHAIN)
                throw new SecretStoreException("SecKeychainAddGenericPassword 失败: " + rc);
        }
    }

    @Override
    public byte[] retrieve(String domain, String account) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment svc = arena.allocateFrom(domain, StandardCharsets.UTF_8);
            MemorySegment acct = arena.allocateFrom(account, StandardCharsets.UTF_8);
            MemorySegment pwdLenPtr = arena.allocate(ValueLayout.JAVA_INT.byteSize());
            MemorySegment pwdPtrPtr = arena.allocate(ValueLayout.ADDRESS.byteSize());

            int rc = SEC.callInt("SecKeychainFindGenericPassword",
                    MemorySegment.NULL,
                    domain.length(), svc,
                    account.length(), acct,
                    pwdLenPtr, pwdPtrPtr,
                    MemorySegment.NULL);
            if (rc != 0)
                throw new SecretStoreException("凭据不存在: " + domain + "/" + account);

            MemorySegment pwdData = pwdPtrPtr.get(ValueLayout.ADDRESS, 0);
            int pwdLen = pwdLenPtr.get(ValueLayout.JAVA_INT, 0);
            byte[] result = pwdData.reinterpret(pwdLen).toArray(ValueLayout.JAVA_BYTE);

            SEC.callVoid("SecKeychainItemFreeContent", MemorySegment.NULL, pwdData);
            return result;
        }
    }

    @Override
    public void delete(String domain, String account) throws SecretStoreException {
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
    public String getProvider() { return "macOS Keychain (FFM)"; }

    @Override
    public void close() {}
}
