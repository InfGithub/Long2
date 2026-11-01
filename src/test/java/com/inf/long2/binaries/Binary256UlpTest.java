package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;

class Binary256UlpTest {
    private static Binary256 exactPower(int exponent) {
        return Binary256Reference.round(false, BigInteger.ONE, BigInteger.ONE, exponent);
    }

    private static void assertUlp(Binary256 expected, Binary256 value) {
        BigInteger before = Binary256Reference.raw(value);
        assertEquals(Binary256Reference.raw(expected), Binary256Reference.raw(value.ulp()));
        assertEquals(before, Binary256Reference.raw(value));
    }

    private static Binary256 normalPower(int exponent) {
        return Binary256.fromRawBits((exponent + 262143L) << 44, 0, 0, 0);
    }

    @Test
    void handlesNaNsAndBothInfinities() {
        for (Binary256 nan : List.of(Binary256.NaN,
                Binary256.fromRawBits(0x7fff_f000_0000_0000L, 0, 0, 1),
                Binary256.fromRawBits(0xffff_f000_0000_0000L, 0, 0, 1),
                Binary256.fromRawBits(0x7fff_f800_0000_1234L, 5, 6, 7),
                Binary256.fromRawBits(0xffff_f800_0000_1234L, 5, 6, 7))) {
            assertUlp(Binary256.NaN, nan);
        }
        assertUlp(Binary256.POSITIVE_INFINITY, Binary256.POSITIVE_INFINITY);
        assertUlp(Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY);
    }

    @Test
    void returnsMinimumForZerosAndAllSubnormals() {
        assertUlp(Binary256.MIN_VALUE, Binary256.ZERO);
        assertUlp(Binary256.MIN_VALUE, Binary256.NEGATIVE_ZERO);
        assertUlp(Binary256.MIN_VALUE, Binary256.MIN_VALUE);
        assertUlp(Binary256.MIN_VALUE, Binary256.MIN_VALUE.negate());
        assertUlp(Binary256.MIN_VALUE, Binary256.MIN_NORMAL.nextDown());
        assertUlp(Binary256.MIN_VALUE, Binary256.MIN_NORMAL);
        Random random = new Random(0x51B01A256L);
        for (int i = 0; i < 1000; i++) {
            long top = (random.nextLong() & 0x0000_0fff_ffff_ffffL) | (random.nextBoolean() ? Long.MIN_VALUE : 0);
            assertUlp(Binary256.MIN_VALUE,
                    Binary256.fromRawBits(top, random.nextLong(), random.nextLong(), random.nextLong()));
        }
    }

    @Test
    void tracksBinadesAndTheSubnormalUlpTransition() {
        for (int exponent : new int[] {-262142, -262141, -261907, -261906, -261905,
                -237, -236, -1, 0, 1, 235, 236, 237, 262142, 262143}) {
            Binary256 power = normalPower(exponent);
            Binary256 expected = exactPower(exponent - 236);
            Binary256 below = power.nextDown();
            Binary256 belowExpected = exactPower(Math.max(exponent - 237, -262378));
            assertUlp(expected, power);
            assertUlp(expected, power.negate());
            assertUlp(expected, power.nextUp());
            assertUlp(expected, power.nextUp().negate());
            assertUlp(belowExpected, below);
            assertUlp(belowExpected, below.negate());
        }
        assertTrue(normalPower(-261907).ulp().isSubnormal());
        assertEquals(Binary256.MIN_NORMAL, normalPower(-261906).ulp());
    }

    @Test
    void keepsMaximumFiniteUlpFiniteAndIgnoresSign() {
        Binary256 expected = exactPower(261907);
        assertUlp(expected, Binary256.MAX_VALUE);
        assertUlp(expected, Binary256.MAX_VALUE.negate());
        assertTrue(Binary256.MAX_VALUE.ulp().isFinite());
        assertFalse(Binary256.MAX_VALUE.ulp().signBit());
        assertTrue(Binary256.MAX_VALUE.nextUp().isInfinite());
        Binary<Binary256> contract = Binary256.ONE;
        assertEquals(exactPower(-236), contract.ulp());
    }

    @Test
    void doesNotUseTheUpwardGapForNegativePowersOfTwo() {
        Binary256 negativeOne = Binary256.ONE.negate();
        Binary256 upwardGap = negativeOne.nextUp().subtract(negativeOne);
        assertEquals(exactPower(-237), upwardGap);
        assertEquals(exactPower(-236), negativeOne.ulp());
        assertNotEquals(upwardGap, negativeOne.ulp());
    }

    @Test
    void matchesAnIndependentReferenceAcrossRandomEncodings() {
        Random random = new Random(0x01A256L);
        for (int i = 0; i < 3000; i++) {
            long top = random.nextLong();
            int encodedExponent = (int) ((top >>> 44) & 0x7ffff);
            if (encodedExponent == 0x7ffff) {
                continue;
            }
            Binary256 value = Binary256.fromRawBits(top, random.nextLong(), random.nextLong(), random.nextLong());
            // 直接读取输入原始指数字段，用独立 BigInteger 舍入参考编码精确二次幂。
            // 不调用生产代码的 scaleExponent() 或 scalb() 来生成预期值。
            int exponent = encodedExponent == 0 ? -262378 : encodedExponent - 262143 - 236;
            assertUlp(exactPower(exponent), value);
            assertUlp(exactPower(exponent), value.negate());
            assertFalse(value.ulp().signBit());
            assertTrue(value.ulp().isFinite());
            assertFalse(value.ulp().isZero());
        }
    }
}
