package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256BaseTest {
    @Test
    void testToHexString() {
        Int256 value = Int256.of(~1119L);
        assertEquals("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffba0",
                value.toHexString());
    }

    @Test
    void testNot() {
        Int256 value1 = Int256.of(-2147483649L);
        Int256 value2 = Int256.of(2147483648L);
        assertEquals(
                value1,
                value2.not());
    }

    @Test
    void testNotFullWidth() {
        Int256 value1 = Int256.of(0L, -1L, 0L, -1L);
        Int256 value2 = Int256.of(-1L, 0L, -1L, 0L);
        assertEquals(
                value1,
                value2.not());
    }

    @Test
    void testAndFullWidth() {
        Int256 value1 = Int256.of(-1L, 0L, -1L, 0L);
        Int256 value2 = Int256.of(0L, -1L, -1L, -1L);
        Int256 expected = Int256.of(0L, 0L, -1L, 0L);

        assertEquals(
                expected,
                value1.and(value2));
    }

    @Test
    void testOrFullWidth() {
        Int256 value1 = Int256.of(-1L, 0L, -1L, 0L);
        Int256 value2 = Int256.of(0L, -1L, 0L, -1L);
        Int256 expected = Int256.of(-1L, -1L, -1L, -1L);

        assertEquals(
                expected,
                value1.or(value2));
    }

    @Test
    void testXorFullWidth() {
        Int256 value1 = Int256.of(-1L, 0L, -1L, 0L);
        Int256 value2 = Int256.of(0L, -1L, -1L, 0L);
        Int256 expected = Int256.of(-1L, -1L, 0L, 0L);

        assertEquals(
                expected,
                value1.xor(value2));
    }

    @Test
    void testFourBitOperation() {
        Int256 a = Int256.of(
                0x123456789ABCDEFL, -1L, 0L, Long.MIN_VALUE);
        Int256 b = Int256.of(
                -1L, 0x123456789ABCDEFL, Long.MAX_VALUE, 0L);

        // ~(a & b) == ~a | ~b
        assertEquals(
                a.and(b).not(),
                a.not().or(b.not()));

        // ~(a | b) == ~a & ~b
        assertEquals(
                a.or(b).not(),
                a.not().and(b.not()));

        // a ^ b == (a | b) & ~(a & b)
        assertEquals(
                a.xor(b),
                a.or(b).and(a.and(b).not()));
    }

    @Test
    void testMinMaxValue() {
        assertEquals(
                "0x7fffffffffffffff" + "ffffffffffffffff".repeat(3),
                Int256.MAX_VALUE.toHexString());

        assertEquals(
                "0x8000000000000000" + "0000000000000000".repeat(3),
                Int256.MIN_VALUE.toHexString());
    }

    @Test
    void testParseHexInt256() {
        assertEquals(
                Int256.of(255L),
                Int256.parseHexInt("0xff"));

        assertEquals(
                Int256.of(0L),
                Int256.parseHexInt("0x0"));

        assertEquals(
                Int256.MAX_VALUE,
                Int256.parseHexInt(
                        Int256.MAX_VALUE.toHexString()));

        assertEquals(
                Int256.MIN_VALUE,
                Int256.parseHexInt(
                        Int256.MIN_VALUE.toHexString()));

        // 超过 64 位，截断高位
        assertEquals(
                Int256.of(1L),
                Int256.parseHexInt(
                        "0x1" + "0".repeat(63) + "1"));

        // 不进行符号扩展
        assertEquals(
                Int256.of(15L),
                Int256.parseHexInt("0xf"));

        // 非法输入
        assertThrows(NumberFormatException.class,
                () -> Int256.parseHexInt("0x"));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseHexInt("FF"));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseHexInt("0xFF"));

        assertThrows(NumberFormatException.class,
                () -> Int256.parseHexInt("0x-1"));
    }
}
