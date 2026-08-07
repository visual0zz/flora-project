package com.flora.os.natives.ffm;

import com.flora.cache.Cache;
import com.flora.cache.Caches;

import java.lang.foreign.MemorySegment;

/**
 * 统一 native 调用门面。以库名为键缓存已加载的 {@link NativeLib} 实例，
 * 调用方无需先 {@code load}，直接 {@code Native.callX(lib, func, args)} 即可。
 *
 * <p>库句柄一旦加载便在进程生命周期内常驻（无界缓存、不驱逐），与 native 库句柄
 * 通常随进程存活的语义一致。并发调用安全：每个库名至多加载一次。
 */
public final class Native {

    private static final Cache<String, NativeLib> LIBS = Caches.<String, NativeLib>memory().get();

    private Native() {
    }

    public static int callInt(String lib, String func, Object... args) {
        return lib(lib).callInt(func, args);
    }

    public static long callLong(String lib, String func, Object... args) {
        return lib(lib).callLong(func, args);
    }

    public static double callDouble(String lib, String func, Object... args) {
        return lib(lib).callDouble(func, args);
    }

    public static void callVoid(String lib, String func, Object... args) {
        lib(lib).callVoid(func, args);
    }

    public static MemorySegment callPtr(String lib, String func, Object... args) {
        return lib(lib).callPtr(func, args);
    }

    /** 取或懒加载并缓存指定库；并发下保证每个库名只加载一次。 */
    private static NativeLib lib(String name) {
        NativeLib existing = LIBS.get(name);
        if (existing != null) {
            return existing;
        }
        NativeLib created = NativeLib.load(name);
        if (LIBS.putIfAbsent(name, created)) {
            return created;
        }
        // 其他线程已先行加载，丢弃本次多余实例（避免重复打开同一原生库）
        created.close();
        return LIBS.get(name);
    }

    /** 测试辅助：当前缓存的库数量。 */
    static int cachedLibraryCount() {
        return (int) LIBS.approxCount();
    }
}
