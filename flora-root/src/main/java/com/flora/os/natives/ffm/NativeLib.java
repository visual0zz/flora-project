package com.flora.os.natives.ffm;

import com.flora.cache.Caches;
import com.flora.cache.MemoryCache;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.util.*;

/**
 * 本地动态库调用包装。封装 JDK FFM API，提供轻量调用接口。
 *
 * <p>本类是「按库一个句柄」的底层封装，调用方通常不需要直接使用它：
 * 统一入口见同包的 {@link Native}，它以内置缓存复用各库句柄，调用方无需先 load。
 *
 * <pre>{@code
 * // 统一入口（库句柄由 Native 内部缓存，无需先 load）
 * int pid = Native.callInt("kernel32", "GetCurrentProcessId");
 * }</pre>
 */
public final class NativeLib implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();

    /** 每库函数绑定缓存的容量上限；超过后按 W-TinyLFU 淘汰最少使用的绑定。 */
    private static final long FUNC_CACHE_CAPACITY = 256;
    /** 每库函数绑定缓存的条目存活时长；超过后下次访问触发重新绑定（downcall 桩重新生成）。 */
    private static final Duration FUNC_CACHE_TTL = Duration.ofMinutes(10);

    private final Arena arena;
    private final SymbolLookup lookup;
    /** 按 (func, returnType, argTypes) 缓存已绑定的 NativeFunc，避免每次调用都重新生成 downcall 桩；
     *  有界且限时（容量 {@link #FUNC_CACHE_CAPACITY}、TTL {@link #FUNC_CACHE_TTL}），
     *  超容按 W-TinyLFU 淘汰、超时自动过期。 */
    private final MemoryCache<BindKey, NativeFunc> funcCache;

    private NativeLib(Arena arena, SymbolLookup lookup) {
        this.arena = arena;
        this.lookup = lookup;
        this.funcCache = Caches.memory(FUNC_CACHE_CAPACITY);
    }

    // ====== 加载库（包内可见，统一入口见 Native） ======

    /**
     * 加载本地动态库并返回句柄。包内可见：对外的统一调用入口是 {@link Native}，
     * 它会缓存每个库名对应的句柄，调用方无需先 load。
     * 使用共享 arena，使缓存后的句柄可跨线程调用。
     */
    static NativeLib load(String libName) {
        Arena arena = Arena.ofShared();
        SymbolLookup lookup;
        try {
            lookup = SymbolLookup.libraryLookup(libName, arena);
        } catch (Exception e) {
            // 尝试默认查找路径
            lookup = LINKER.defaultLookup();
        }
        return new NativeLib(arena, lookup);
    }

    // ====== 实例方法 ======

    public int callInt(String func, Object... args) {
        return bind(func, int.class, args).callInt(args);
    }

    public long callLong(String func, Object... args) {
        return bind(func, long.class, args).callLong(args);
    }

    public double callDouble(String func, Object... args) {
        return bind(func, double.class, args).callDouble(args);
    }

    public void callVoid(String func, Object... args) {
        bind(func, void.class, args).callVoid(args);
    }

    public MemorySegment callPtr(String func, Object... args) {
        return bind(func, MemorySegment.class, args).callPtr(args);
    }

    /** 绑定函数并返回可复用的句柄，返回值按 int 解释（兼容旧用法）。 */
    public NativeFunc bind(String func, Object... args) {
        return bind(func, int.class, args);
    }

    /** 绑定函数并返回可复用的句柄，按指定返回类型生成函数描述符。结果按 (func, returnType, argTypes) 缓存，避免每次调用都重新生成 downcall 桩。 */
    public NativeFunc bind(String func, Class<?> returnType, Object... args) {
        BindKey key = new BindKey(func, returnType, argClasses(args));
        NativeFunc cached = funcCache.get(key);
        if (cached != null) {
            return cached;
        }
        MemorySegment addr = lookup.find(func).orElse(null);
        if (addr == null) {
            // 用函数名本身作为符号名再试一次
            addr = LINKER.defaultLookup().find(func).orElseThrow(
                    () -> new IllegalArgumentException("找不到函数: " + func));
        }
        FunctionDescriptor desc = descriptor(returnType, args);
        MethodHandle handle = LINKER.downcallHandle(addr, desc);
        NativeFunc nf = new NativeFunc(handle, desc);
        funcCache.put(key, nf, FUNC_CACHE_TTL);
        return nf;
    }

    @Override
    public void close() {
        funcCache.clear();
        arena.close();
    }

    /** 函数绑定的缓存键：函数名 + 返回类型 + 参数类型序列（参数类型决定 FunctionDescriptor 布局）。 */
    private record BindKey(String func, Class<?> returnType, List<Class<?>> argTypes) {
    }

    // ====== 内部 ======

    /**
     * 按返回类型与参数生成函数描述符。返回布局必须与实际 C 函数一致，
     * 否则 64 位返回值（指针/HANDLE）会被按 32 位读取而截断。
     */
    static FunctionDescriptor descriptor(Class<?> returnType, Object... args) {
        List<MemoryLayout> paramLayouts = new ArrayList<>();
        for (Object arg : args) {
            paramLayouts.add(layoutOf(arg));
        }
        MemoryLayout ret = returnLayout(returnType);
        if (ret == null) {
            return FunctionDescriptor.ofVoid(paramLayouts.toArray(MemoryLayout[]::new));
        }
        return FunctionDescriptor.of(ret, paramLayouts.toArray(MemoryLayout[]::new));
    }

    /** 把 Java 返回类型映射到 FFM 值布局；void 返回 null。 */
    private static MemoryLayout returnLayout(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == int.class) {
            return ValueLayout.JAVA_INT;
        }
        if (returnType == long.class) {
            return ValueLayout.JAVA_LONG;
        }
        if (returnType == double.class) {
            return ValueLayout.JAVA_DOUBLE;
        }
        if (returnType == float.class) {
            return ValueLayout.JAVA_FLOAT;
        }
        if (returnType == MemorySegment.class) {
            return ValueLayout.ADDRESS;
        }
        throw new IllegalArgumentException("不支持的返回类型: " + returnType);
    }

    static ValueLayout layoutOf(Object arg) {
        return switch (arg) {
            case Integer i -> ValueLayout.JAVA_INT;
            case Long l    -> ValueLayout.JAVA_LONG;
            case Float f   -> ValueLayout.JAVA_FLOAT;
            case Double d  -> ValueLayout.JAVA_DOUBLE;
            case Boolean b -> ValueLayout.JAVA_BYTE;
            case MemorySegment ignored -> ValueLayout.ADDRESS;
            case NativeString ignored -> ValueLayout.ADDRESS;
            case String s  -> throw new IllegalArgumentException(
                    "String 参数需用 NativeString 或 MemorySegment，不能直接传入");
            default -> throw new IllegalArgumentException("不支持的类型: " + arg.getClass());
        };
    }

    /** 返回实参对应的 Java 类型，用于缓存键（需与 {@link #layoutOf} 的映射一致）。 */
    private static Class<?> argClass(Object arg) {
        return switch (arg) {
            case Integer ignored -> int.class;
            case Long ignored    -> long.class;
            case Float ignored   -> float.class;
            case Double ignored  -> double.class;
            case Boolean ignored -> boolean.class;
            case MemorySegment ignored -> MemorySegment.class;
            case NativeString ignored  -> NativeString.class;
            case String s -> throw new IllegalArgumentException(
                    "String 参数需用 NativeString 或 MemorySegment，不能直接传入");
            default -> throw new IllegalArgumentException("不支持的类型: " + arg.getClass());
        };
    }

    /** 提取参数类型序列（不可变），用作绑定缓存键的一部分。 */
    private static List<Class<?>> argClasses(Object... args) {
        List<Class<?>> types = new ArrayList<>(args.length);
        for (Object a : args) {
            types.add(argClass(a));
        }
        return List.copyOf(types);
    }

    // ====== 可复用的函数句柄 ======

    /** 绑定后的本地函数，可反复调用。 */
    public static final class NativeFunc {
        private final MethodHandle handle;
        private final FunctionDescriptor desc;

        NativeFunc(MethodHandle handle, FunctionDescriptor desc) {
            this.handle = handle;
            this.desc = desc;
        }

        public int callInt(Object... args) {
            return call(int.class, args);
        }

        public long callLong(Object... args) {
            return call(long.class, args);
        }

        public double callDouble(Object... args) {
            return call(double.class, args);
        }

        public void callVoid(Object... args) {
            try {
                Object[] cooked = cookArgs(args);
                if (cooked.length == 0) handle.invoke();
                else handle.invokeWithArguments(cooked);
            } catch (Throwable e) {
                throw new RuntimeException("调用失败", e);
            }
        }

        public MemorySegment callPtr(Object... args) {
            return call(MemorySegment.class, args);
        }

        @SuppressWarnings("unchecked")
        private <T> T call(Class<T> type, Object... args) {
            try {
                Object[] cooked = cookArgs(args);
                Object result = cooked.length == 0
                        ? handle.invoke()
                        : handle.invokeWithArguments(cooked);
                return (T) result;
            } catch (Throwable e) {
                throw new RuntimeException("调用失败", e);
            }
        }

        private Object[] cookArgs(Object... args) {
            Object[] result = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = switch (args[i]) {
                    case MemorySegment s -> s;
                    case Integer n -> n;
                    case Long n -> n;
                    case Float n -> n;
                    case Double n -> n;
                    case Boolean b -> (byte) (b ? 1 : 0);
                    case NativeString ns -> ns.segment();
                    default -> throw new IllegalArgumentException(
                            "不支持参数类型: " + args[i].getClass());
                };
            }
            return result;
        }
    }
}
