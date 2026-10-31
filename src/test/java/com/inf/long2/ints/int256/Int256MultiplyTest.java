package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256MultiplyTest {

    @Test
    void testMultiplyRandom() {
        Random random = new Random(0x256A11L);
        BigInteger mask = BigInteger.ONE.shiftLeft(256)
                .subtract(BigInteger.ONE);

        for (int i = 0; i < 10000; i++) {
            Int256 a = Int256.of(
                    random.nextLong(), random.nextLong(),
                    random.nextLong(), random.nextLong());
            Int256 b = Int256.of(
                    random.nextLong(), random.nextLong(),
                    random.nextLong(), random.nextLong());

            BigInteger bigA = new BigInteger(
                    a.toHexString().substring(2), 16);
            BigInteger bigB = new BigInteger(
                    b.toHexString().substring(2), 16);

            BigInteger product = bigA.multiply(bigB).and(mask);
            Int256 expected = Int256.parseHexInt(
                    "0x" + product.toString(16));

            assertEquals(expected, a.multiply(b));
            assertEquals(expected, b.multiply(a));
        }
    }

    @Test
    void testMultiplyBoundary() {
        Int256 zero = Int256.of(0L);
        Int256 one = Int256.of(1L);
        Int256 two = Int256.of(2L);
        Int256 negativeOne = Int256.of(-1L);

        // 基本运算
        assertEquals(zero, zero.multiply(zero));
        assertEquals(zero, zero.multiply(negativeOne));
        assertEquals(one, one.multiply(one));
        assertEquals(one, negativeOne.multiply(negativeOne));
        assertEquals(Int256.of(-2L), negativeOne.multiply(two));

        // long 边界
        assertEquals(
                Int256.of(0L, 0L, 0L, -2L),
                Int256.of(Long.MAX_VALUE).multiply(two));

        assertEquals(
                Int256.of(0L, 0L, 0L, Long.MIN_VALUE),
                Int256.of(Long.MIN_VALUE).multiply(negativeOne));

        // 64-bit 乘法产生高位结果
        Int256 max64 = Int256.of(0L, 0L, 0L, -1L);
        assertEquals(
                Int256.of(0L, 0L, -2L, 1L),
                max64.multiply(max64));

        // 跨越 128-bit 边界
        Int256 pow64 = Int256.of(0L, 0L, 1L, 0L);
        Int256 pow128 = Int256.of(0L, 1L, 0L, 0L);

        assertEquals(pow128, pow64.multiply(pow64));
        assertEquals(zero, pow128.multiply(pow128));

        // 多组部分积与连续 carry
        Int256 max192 = Int256.of(0L, -1L, -1L, -1L);
        assertEquals(
                Int256.of(-2L, 0L, 0L, 1L),
                max192.multiply(max192));

        // 完整 256-bit 溢出回绕
        assertEquals(
                Int256.of(-2L),
                Int256.MAX_VALUE.multiply(two));

        assertEquals(zero,
                Int256.MIN_VALUE.multiply(two));

        assertEquals(Int256.MIN_VALUE,
                Int256.MIN_VALUE.multiply(negativeOne));

        assertEquals(zero,
                Int256.MIN_VALUE.multiply(Int256.MIN_VALUE));

        assertEquals(one,
                Int256.MAX_VALUE.multiply(Int256.MAX_VALUE));
    }
}
