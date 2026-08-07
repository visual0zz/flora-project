package com.flora.os.natives.ffm;

import org.junit.jupiter.api.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibTest {

    @Test
    void intReturnLayout() {
        FunctionDescriptor d = NativeLib.descriptor(int.class);
        assertEquals(ValueLayout.JAVA_INT, d.returnLayout().orElseThrow());
        assertTrue(d.argumentLayouts().isEmpty());
    }

    @Test
    void longReturnLayoutNotTruncatedToInt() {
        // 关键回归：曾把返回布局硬写为 JAVA_INT，导致 64 位返回值被截断。
        FunctionDescriptor d = NativeLib.descriptor(long.class);
        assertEquals(ValueLayout.JAVA_LONG, d.returnLayout().orElseThrow());
    }

    @Test
    void pointerReturnLayoutIsAddress() {
        FunctionDescriptor d = NativeLib.descriptor(MemorySegment.class);
        assertEquals(ValueLayout.ADDRESS, d.returnLayout().orElseThrow());
    }

    @Test
    void doubleReturnLayout() {
        FunctionDescriptor d = NativeLib.descriptor(double.class);
        assertEquals(ValueLayout.JAVA_DOUBLE, d.returnLayout().orElseThrow());
    }

    @Test
    void voidReturnHasNoLayout() {
        FunctionDescriptor d = NativeLib.descriptor(void.class);
        assertTrue(d.returnLayout().isEmpty());
    }

    @Test
    void argumentLayoutsArePreserved() {
        FunctionDescriptor d = NativeLib.descriptor(long.class, 1L, 2, true);
        assertEquals(ValueLayout.JAVA_LONG, d.returnLayout().orElseThrow());
        assertEquals(3, d.argumentLayouts().size());
        assertEquals(ValueLayout.JAVA_LONG, d.argumentLayouts().get(0));
        assertEquals(ValueLayout.JAVA_INT, d.argumentLayouts().get(1));
        assertEquals(ValueLayout.JAVA_BYTE, d.argumentLayouts().get(2));
    }
}
