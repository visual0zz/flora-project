package com.flora.ffi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 本地动态库调用包装。封装 JDK FFM API，提供轻量调用接口。
 *
 * <pre>{@code
 * // 一句话调用（查找 + 加载 + 调用，适合低频）
 * int pid = NativeLib.callInt("kernel32", "GetCurrentProcessId");
 *
 * // 预加载后调用（适合复用）
 * try (NativeLib lib = NativeLib.load("kernel32")) {
 *     int pid = lib.callInt("GetCurrentProcessId");
 * }
 * }</pre>
 */
public final class NativeLib implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();

    private final Arena arena;
    private final SymbolLookup lookup;

    private NativeLib(Arena arena, SymbolLookup lookup) {
        this.arena = arena;
        this.lookup = lookup;
    }

    // ====== 静态一句话调用 ======

    public static int callInt(String lib, String func, Object... args) {
        try (NativeLib nl = load(lib)) {
            return nl.callInt(func, args);
        }
    }

    public static long callLong(String lib, String func, Object... args) {
        try (NativeLib nl = load(lib)) {
            return nl.callLong(func, args);
        }
    }

    public static double callDouble(String lib, String func, Object... args) {
        try (NativeLib nl = load(lib)) {
            return nl.callDouble(func, args);
        }
    }

    public static void callVoid(String lib, String func, Object... args) {
        try (NativeLib nl = load(lib)) {
            nl.callVoid(func, args);
        }
    }

    // ====== 加载库 ======

    /** 加载本地动态库。返回的 {@link NativeLib} 可复用。 */
    public static NativeLib load(String libName) {
        Arena arena = Arena.ofConfined();
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
        return bind(func, args).callInt(args);
    }

    public long callLong(String func, Object... args) {
        return bind(func, args).callLong(args);
    }

    public double callDouble(String func, Object... args) {
        return bind(func, args).callDouble(args);
    }

    public void callVoid(String func, Object... args) {
        bind(func, args).callVoid(args);
    }

    public MemorySegment callPtr(String func, Object... args) {
        return bind(func, args).callPtr(args);
    }

    /** 绑定函数并返回可复用的句柄。 */
    public NativeFunc bind(String func, Object... args) {
        MemorySegment addr = lookup.find(func).orElse(null);
        if (addr == null) {
            // 用函数名本身作为符号名再试一次
            addr = LINKER.defaultLookup().find(func).orElseThrow(
                    () -> new IllegalArgumentException("找不到函数: " + func));
        }
        FunctionDescriptor desc = descriptor(args);
        MethodHandle handle = LINKER.downcallHandle(addr, desc);
        return new NativeFunc(handle, desc);
    }

    @Override
    public void close() {
        arena.close();
    }

    // ====== 内部 ======

    private static FunctionDescriptor descriptor(Object... args) {
        List<MemoryLayout> paramLayouts = new ArrayList<>();
        for (Object arg : args) {
            paramLayouts.add(layoutOf(arg));
        }
        return FunctionDescriptor.of(ValueLayout.JAVA_INT,
                paramLayouts.toArray(MemoryLayout[]::new));
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
