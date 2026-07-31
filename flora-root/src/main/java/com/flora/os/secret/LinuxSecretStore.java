package com.flora.os.secret;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Linux 内核 keyring 实现。
 * <p>通过 FFM 调用 glibc 的 {@code add_key()} / {@code request_key()} / {@code keyctl()}，
 * 在内核密钥保留服务中托管密钥数据。密钥仅存活于进程 keyring 中，进程退出即销毁。
 * 零外部依赖，仅依赖 {@code libc.so.6}（系统中永远存在）。</p>
 */
class LinuxSecretStore implements SecretStore {

    // kernel keyring 常量
    private static final int KEY_SPEC_PROCESS_KEYRING = -2;
    private static final int KEYCTL_READ       = 11;
    private static final int KEYCTL_INVALIDATE = 21;

    private static final Linker LINKER = Linker.nativeLinker();

    private static final boolean AVAILABLE;
    private static final MethodHandle ADD_KEY;
    private static final MethodHandle REQ_KEY;
    private static final MethodHandle KEYCTL_READ_H;
    private static final MethodHandle KEYCTL_INV_H;

    static {
        boolean ok = false;
        MethodHandle addKey = null;
        MethodHandle reqKey = null;
        MethodHandle readH  = null;
        MethodHandle invH   = null;
        try {
            Arena arena = Arena.ofShared();
            // add_key/request_key/keyctl 在 libkeyutils.so.1 中（Linux 标准系统库）
            SymbolLookup libc;
            try {
                libc = SymbolLookup.libraryLookup("libkeyutils.so.1", arena);
            } catch (Exception e) {
                // 回退到 defaultLookup（某些系统可能在 libc 或其他位置）
                libc = LINKER.defaultLookup();
            }

            MemorySegment addKeyAddr = libc.find("add_key").orElseThrow();
            MemorySegment reqKeyAddr = libc.find("request_key").orElseThrow();
            MemorySegment keyctlAddr = libc.find("keyctl").orElseThrow();

            // key_serial_t add_key(const char *type, const char *description,
            //                      const void *payload, size_t plen, key_serial_t keyring);
            addKey = LINKER.downcallHandle(addKeyAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT));

            // key_serial_t request_key(const char *type, const char *description,
            //                          const void *callout, size_t callout_len, key_serial_t keyring);
            reqKey = LINKER.downcallHandle(reqKeyAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT));

            // long keyctl(KEYCTL_READ, key_serial_t key, char *buf, size_t buflen)
            readH = LINKER.downcallHandle(keyctlAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG),
                    Linker.Option.firstVariadicArg(1));

            // long keyctl(KEYCTL_INVALIDATE, key_serial_t key)
            invH = LINKER.downcallHandle(keyctlAddr,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT),
                    Linker.Option.firstVariadicArg(1));

            ok = true;
        } catch (Exception ignored) {
        }
        AVAILABLE     = ok;
        ADD_KEY       = addKey;
        REQ_KEY       = reqKey;
        KEYCTL_READ_H = readH;
        KEYCTL_INV_H  = invH;
    }

    static boolean isAvailable() { return AVAILABLE; }

    private static String description(String domain, String account) {
        return domain + "|" + account;
    }

    // ====== SecretStore 接口 ======

    @Override
    public void store(String domain, String account, byte[] secret) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment type  = arena.allocateFrom("user", StandardCharsets.UTF_8);
            MemorySegment desc  = arena.allocateFrom(description(domain, account), StandardCharsets.UTF_8);
            MemorySegment payld = arena.allocate(secret.length);
            payld.copyFrom(MemorySegment.ofArray(secret));

            int serial = (int) ADD_KEY.invokeWithArguments(
                    type, desc, payld, (long) secret.length, KEY_SPEC_PROCESS_KEYRING);
            if (serial == -1)
                throw new SecretStoreException("add_key 失败");
        } catch (SecretStoreException e) {
            throw e;
        } catch (Throwable e) {
            throw new SecretStoreException("内核 keyring store 异常", e);
        }
    }

    @Override
    public byte[] retrieve(String domain, String account) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment type = arena.allocateFrom("user", StandardCharsets.UTF_8);
            MemorySegment desc = arena.allocateFrom(description(domain, account), StandardCharsets.UTF_8);

            // 1. 获取 key 序列号
            int serial = (int) REQ_KEY.invokeWithArguments(
                    type, desc, MemorySegment.NULL, 0L, KEY_SPEC_PROCESS_KEYRING);
            if (serial == -1)
                throw new SecretStoreException("密钥不存在: " + domain + "/" + account);

            // 2. 查询 payload 长度（buffer=NULL, buflen=0）
            long bufLen = (long) KEYCTL_READ_H.invokeWithArguments(
                    KEYCTL_READ, serial, MemorySegment.NULL, 0L);
            if (bufLen < 0)
                throw new SecretStoreException("keyctl(READ) 查询长度失败");

            // 3. 读取 payload
            MemorySegment buf = arena.allocate(bufLen);
            bufLen = (long) KEYCTL_READ_H.invokeWithArguments(
                    KEYCTL_READ, serial, buf, bufLen);
            if (bufLen < 0)
                throw new SecretStoreException("keyctl(READ) 读取失败");

            return buf.toArray(ValueLayout.JAVA_BYTE);
        } catch (SecretStoreException e) {
            throw e;
        } catch (Throwable e) {
            throw new SecretStoreException("内核 keyring retrieve 异常", e);
        }
    }

    @Override
    public void delete(String domain, String account) throws SecretStoreException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment type = arena.allocateFrom("user", StandardCharsets.UTF_8);
            MemorySegment desc = arena.allocateFrom(description(domain, account), StandardCharsets.UTF_8);

            int serial = (int) REQ_KEY.invokeWithArguments(
                    type, desc, MemorySegment.NULL, 0L, KEY_SPEC_PROCESS_KEYRING);
            if (serial == -1) return; // 不存在，静默忽略

            KEYCTL_INV_H.invokeWithArguments(KEYCTL_INVALIDATE, serial);
        } catch (Throwable e) {
            throw new SecretStoreException("内核 keyring delete 异常", e);
        }
    }

    @Override
    public String getProvider() { return "Linux Kernel Keyring"; }

    @Override
    public void close() {}
}
