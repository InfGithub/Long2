package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256NegateTest {
    @Test
    void testNegate() {
        Int256 zero = Int256.of(0L);
        Int256 one = Int256.of(1L);
        Int256 negativeOne = Int256.of(-1L);

        // 基本取负
        assertEquals(zero, zero.negate());
        assertEquals(negativeOne, one.negate());
        assertEquals(one, negativeOne.negate());

        // long 边界
        assertEquals(
                Int256.of(0L, 0L, 0L, Long.MIN_VALUE),
                Int256.of(Long.MIN_VALUE).negate());

        // 完整 256-bit
        Int256 value = Int256.of(
                0x123456789ABCDEFL, -1L, Long.MIN_VALUE, 12345L);

        assertEquals(value, value.negate().negate());
        assertEquals(zero, value.add(value.negate()));

        // 256-bit 边界回绕
        assertEquals(Int256.MIN_VALUE,
                Int256.MIN_VALUE.negate());

        assertEquals(
                Int256.MIN_VALUE.add(one),
                Int256.MAX_VALUE.negate());
    }
}
