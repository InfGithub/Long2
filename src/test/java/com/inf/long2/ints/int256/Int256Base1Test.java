package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256Base1Test {
    @Test
    void testCompareTo() {
        Int256 zero = Int256.of(0L);
        Int256 one = Int256.of(1L);
        Int256 negativeOne = Int256.of(-1L);

        // 相等
        assertEquals(0, zero.compareTo(Int256.of(0L)));
        assertEquals(0, one.compareTo(Int256.of(1L)));
        assertEquals(0, Int256.MAX_VALUE.compareTo(Int256.MAX_VALUE));

        // 基本有符号比较
        assertTrue(negativeOne.compareTo(zero) < 0);
        assertTrue(zero.compareTo(one) < 0);
        assertTrue(one.compareTo(negativeOne) > 0);

        // 256-bit 极值
        assertTrue(Int256.MIN_VALUE.compareTo(Int256.MAX_VALUE) < 0);
        assertTrue(Int256.MAX_VALUE.compareTo(Int256.MIN_VALUE) > 0);

        // 最高字段按有符号比较
        assertTrue(
                Int256.of(-1L, 0L, 0L, 0L).compareTo(
                        Int256.of(1L, 0L, 0L, 0L)) < 0);

        // 其余字段按无符号比较
        assertTrue(
                Int256.of(0L, -1L, 0L, 0L).compareTo(
                        Int256.of(0L, Long.MAX_VALUE, 0L, 0L)) > 0);

        assertTrue(
                Int256.of(0L, 0L, -1L, 0L).compareTo(
                        Int256.of(0L, 0L, Long.MAX_VALUE, 0L)) > 0);

        assertTrue(
                Int256.of(0L, 0L, 0L, -1L).compareTo(
                        Int256.of(0L, 0L, 0L, Long.MAX_VALUE)) > 0);

        // 高位字段优先决定大小
        assertTrue(
                Int256.of(0L, 1L, 0L, 0L).compareTo(
                        Int256.of(0L, 0L, -1L, -1L)) > 0);
    }

    @Test
    void testSignum() {
        assertEquals(0, Int256.ZERO.signum());
        assertEquals(1, Int256.of(1L).signum());
        assertEquals(-1, Int256.of(-1L).signum());

        // 256-bit 极值
        assertEquals(1, Int256.MAX_VALUE.signum());
        assertEquals(-1, Int256.MIN_VALUE.signum());

        // 非零值位于不同字段
        assertEquals(1,
                Int256.of(0L, 0L, 0L, Long.MIN_VALUE).signum());

        assertEquals(1,
                Int256.of(0L, 0L, -1L, 0L).signum());

        assertEquals(1,
                Int256.of(0L, -1L, 0L, 0L).signum());

        assertEquals(-1,
                Int256.of(Long.MIN_VALUE, 0L, 0L, 0L).signum());
    }

    @Test
    void testIsZero() {
        assertTrue(Int256.ZERO.isZero());
        assertTrue(Int256.of(0L, 0L, 0L, 0L).isZero());

        // 任意字段非零，整个整数都不是零
        assertFalse(Int256.of(1L, 0L, 0L, 0L).isZero());
        assertFalse(Int256.of(0L, 1L, 0L, 0L).isZero());
        assertFalse(Int256.of(0L, 0L, 1L, 0L).isZero());
        assertFalse(Int256.of(0L, 0L, 0L, 1L).isZero());

        // 负数及极值
        assertFalse(Int256.of(-1L).isZero());
        assertFalse(Int256.MIN_VALUE.isZero());
        assertFalse(Int256.MAX_VALUE.isZero());
    }

    @Test
    void testIsNegative() {
        assertFalse(Int256.ZERO.isNegative());
        assertFalse(Int256.of(1L).isNegative());
        assertTrue(Int256.of(-1L).isNegative());

        // 256-bit 极值
        assertFalse(Int256.MAX_VALUE.isNegative());
        assertTrue(Int256.MIN_VALUE.isNegative());

        // 只有最高 bit 决定符号
        assertFalse(
                Int256.of(0L, -1L, -1L, -1L).isNegative());

        assertFalse(
                Int256.of(Long.MAX_VALUE, -1L, -1L, -1L)
                        .isNegative());

        assertTrue(
                Int256.of(Long.MIN_VALUE, 0L, 0L, 1L)
                        .isNegative());
    }

    @Test
    void testBit() {
        Int256 value = Int256.of(
                Long.MIN_VALUE | 1L,
                Long.MIN_VALUE | 1L,
                Long.MIN_VALUE | 1L,
                Long.MIN_VALUE | 1L);

        // 四个 long 的最低位和最高位
        assertTrue(value.testBit(0));
        assertTrue(value.testBit(63));

        assertTrue(value.testBit(64));
        assertTrue(value.testBit(127));

        assertTrue(value.testBit(128));
        assertTrue(value.testBit(191));

        assertTrue(value.testBit(192));
        assertTrue(value.testBit(255));

        // 相邻的零位
        assertFalse(value.testBit(1));
        assertFalse(value.testBit(62));
        assertFalse(value.testBit(65));
        assertFalse(value.testBit(126));
        assertFalse(value.testBit(129));
        assertFalse(value.testBit(190));
        assertFalse(value.testBit(193));
        assertFalse(value.testBit(254));

        // 全零和全一
        for (int i = 0; i < 256; i++) {
            assertFalse(Int256.ZERO.testBit(i));
            assertTrue(Int256.of(-1L).testBit(i));
        }

        // 符号位
        assertFalse(Int256.MAX_VALUE.testBit(255));
        assertTrue(Int256.MIN_VALUE.testBit(255));

        // 非法索引
        assertThrows(IndexOutOfBoundsException.class,
                () -> value.testBit(-1));

        assertThrows(IndexOutOfBoundsException.class,
                () -> value.testBit(256));

        assertThrows(IndexOutOfBoundsException.class,
                () -> value.testBit(Integer.MIN_VALUE));

        assertThrows(IndexOutOfBoundsException.class,
                () -> value.testBit(Integer.MAX_VALUE));
    }
}
