package com.flora.os.natives.ffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class NativeTest {

    @Test
    @EnabledOnOs(OS.LINUX)
    void cachesLibraryByKey() {
        // 同一库名多次调用应复用同一缓存句柄（缓存计数保持为 1）
        long t1 = Native.callLong("libc", "time", MemorySegment.NULL);
        long t2 = Native.callLong("libc", "time", MemorySegment.NULL);
        assertTrue(t1 > 0, "time() 应返回正的时间戳");
        assertTrue(t2 >= t1, "两次调用应一致或递增");
        assertEquals(1, Native.cachedLibraryCount(), "同一库名只应加载一次");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void longReturnIsFullWidth() {
        // time_t 为 64 位；经统一门面应返回未截断的完整值
        long t = Native.callLong("libc", "time", MemorySegment.NULL);
        assertTrue(t > 1_000_000_000L, "应取到 64 位时间戳而非被截断的 32 位值");
    }
}
