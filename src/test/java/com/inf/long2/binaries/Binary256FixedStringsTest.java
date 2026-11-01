package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class Binary256FixedStringsTest {
    /** 从原始编码独立重建精确值，不经生产代码的十进制转换。 */
    private static BigDecimal exactMagnitude(Binary256 value) {
        Binary256Reference.Dyadic dyadic = Binary256Reference.decode(value);
        BigInteger magnitude = dyadic.magnitude();
        int scale = Math.toIntExact(dyadic.scale());
        return scale >= 0 ? new BigDecimal(magnitude.shiftLeft(scale))
                : new BigDecimal(magnitude.multiply(BigInteger.valueOf(5).pow(-scale)), -scale);
    }

    private static void assertReference(Binary256 value, int fractionDigits) {
        BigDecimal exact = exactMagnitude(value);
        String sign = value.signBit() ? "-" : "";
        assertEquals(sign + exact.setScale(fractionDigits, RoundingMode.HALF_EVEN).toPlainString(),
                value.toString(fractionDigits));
        BigDecimal rounded = exact.round(new MathContext(fractionDigits + 1, RoundingMode.HALF_EVEN));
        int exponent = rounded.signum() == 0 ? 0 : rounded.precision() - rounded.scale() - 1;
        String mantissa = rounded.scaleByPowerOfTen(-exponent)
                .setScale(fractionDigits, RoundingMode.HALF_EVEN).toPlainString();
        assertEquals(sign + mantissa + "e" + exponent, value.toScientificString(fractionDigits));
    }

    @Test
    void spellsFixedFractionsAndScientificCarries() {
        String[][] examples = {
                {"123.5", "2", "123.50", "1.24e2"}, {"1", "3", "1.000", "1.000e0"},
                {"2.5", "0", "2", "2e0"}, {"3.5", "0", "4", "4e0"},
                {"-2.5", "0", "-2", "-2e0"}, {"-3.5", "0", "-4", "-4e0"},
                {"9.99609375", "2", "10.00", "1.00e1"},
                {"99.96875", "2", "99.97", "1.00e2"},
                {"99.5", "0", "100", "1e2"}, {"1000", "2", "1000.00", "1.00e3"},
                {"-0.00125", "2", "-0.00", "-1.25e-3"},
                {"0.125", "2", "0.12", "1.25e-1"}, {"0.375", "2", "0.38", "3.75e-1"},
                {"0.125", "1", "0.1", "1.2e-1"},
                {"0", "0", "0", "0e0"}, {"-0", "0", "-0", "-0e0"},
                {"0", "2", "0.00", "0.00e0"}, {"-0", "2", "-0.00", "-0.00e0"}
        };
        for (String[] example : examples) {
            Binary256 value = Binary256.of(example[0]);
            int precision = Integer.parseInt(example[1]);
            assertEquals(example[2], value.toString(precision), example[0]);
            assertEquals(example[3], value.toScientificString(precision), example[0]);
        }
    }

    @Test
    void roundsFromTheStoredValueInsteadOfTheShortestString() {
        Binary256 value = Binary256.of("0.1");
        assertEquals("0.1", value.toString());
        assertReference(value, 120);
        assertNotEquals("0.1" + "0".repeat(119), value.toString(120));
        assertNotEquals("1." + "0".repeat(120) + "e-1", value.toScientificString(120));
        // 最短表示恰为十次幂时，真实指数可能低一；足够精度时必须使用真实尾数。
        for (String text : List.of("1e-1000", "1e-100", "1e100", "1e1000")) {
            assertReference(Binary256.of(text), 100);
        }
    }

    @Test
    void distinguishesExactMidpointsFromTheirBinaryNeighbors() {
        for (String text : List.of("0.125", "0.375", "1.25", "1.75", "2.5", "3.5", "9.5", "99.5")) {
            Binary256 midpoint = Binary256.of(text);
            for (Binary256 value : List.of(midpoint.nextDown(), midpoint, midpoint.nextUp())) {
                for (int digits : new int[] {0, 1, 2}) {
                    assertReference(value, digits);
                    assertReference(value.negate(), digits);
                }
            }
        }
    }

    @Test
    void handlesSpecialValuesAndValidatesPrecisionFirst() {
        for (Binary256 value : List.of(Binary256.NaN, Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY)) {
            for (int digits : new int[] {0, 2, Integer.MAX_VALUE}) {
                assertEquals(value.toString(), value.toString(digits));
                assertEquals(value.toScientificString(), value.toScientificString(digits));
            }
        }
        for (Binary256 value : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE,
                Binary256.NaN, Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY)) {
            for (int digits : new int[] {-1, Integer.MIN_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> value.toString(digits));
                assertThrows(IllegalArgumentException.class, () -> value.toScientificString(digits));
            }
        }
        Binary256 negativeNaN = Binary256.fromRawBits(-1, -1, -1, -1);
        assertEquals("NaN", negativeNaN.toString(2));
        assertEquals("NaN", negativeNaN.toScientificString(2));
    }

    @Test
    void rejectsLengthsThatCannotFitWithoutIntegerOverflow() {
        for (Binary256 value : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE,
                Binary256.MIN_VALUE, Binary256.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> value.toString(Integer.MAX_VALUE));
            assertThrows(IllegalArgumentException.class, () -> value.toScientificString(Integer.MAX_VALUE));
        }
        assertThrows(IllegalArgumentException.class, () -> Binary256.NEGATIVE_ZERO.toString(Integer.MAX_VALUE - 2));
        assertThrows(IllegalArgumentException.class, () -> Binary256.MAX_VALUE.toString(Integer.MAX_VALUE - 100));
        assertThrows(IllegalArgumentException.class, () -> Binary256.MIN_VALUE.toScientificString(Integer.MAX_VALUE - 5));
    }

    @Test
    void padsBeyondTheExactExpansionWithoutScalingByTheRequestedPrecision() {
        int digits = 1_000_000;
        assertEquals("1." + "0".repeat(digits), Binary256.ONE.toString(digits));
        assertEquals("1." + "0".repeat(digits) + "e0", Binary256.ONE.toScientificString(digits));
        Binary256 tenth = Binary256.of("0.1");
        BigDecimal exact = exactMagnitude(tenth).stripTrailingZeros();
        String plain = tenth.toString(digits);
        assertEquals(exact.toPlainString() + "0".repeat(digits - exact.scale()), plain);
        assertEquals(digits + 2, plain.length());
        String mantissa = exact.scaleByPowerOfTen(1).toPlainString();
        String scientific = tenth.toScientificString(digits);
        assertEquals(mantissa + "0".repeat(digits - mantissa.length() + 2) + "e-1", scientific);
    }

    @Test
    void matchesIndependentDecimalRoundingForRandomValues() {
        Random random = new Random(0xF1CED256L);
        int[] precisions = {0, 1, 2, 8, 70, 71, 72, 73, 80, 200, 600};
        for (int i = 0; i < 300; i++) {
            long top = ((long) (262143 + random.nextInt(6001) - 3000) << 44)
                    | (random.nextLong() & 0x0000_0fff_ffff_ffffL);
            if (random.nextBoolean()) {
                top |= Long.MIN_VALUE;
            }
            Binary256 value = Binary256.fromRawBits(top, random.nextLong(), random.nextLong(), random.nextLong());
            BigInteger before = Binary256Reference.raw(value);
            assertReference(value, precisions[random.nextInt(precisions.length)]);
            assertEquals(before, Binary256Reference.raw(value));
        }
    }

    @Test
    void coversExtremeExponentsAndSubnormalBoundaries() {
        for (Binary256 value : List.of(Binary256.MIN_VALUE, Binary256.MIN_VALUE.nextUp(),
                Binary256.fromRawBits(0, 0, 0, 12345), Binary256.MIN_NORMAL.nextDown(),
                Binary256.MIN_NORMAL, Binary256.MIN_NORMAL.nextUp(), Binary256.MAX_VALUE)) {
            for (int digits : new int[] {0, 2, 72, 80}) {
                assertReference(value, digits);
                assertReference(value.negate(), digits);
            }
        }
        // 精确展开终止点前后，同时验证分母为二次幂的舍入路径和直接补零路径。
        for (int digits : new int[] {262377, 262378, 262379}) {
            assertReference(Binary256.MIN_VALUE, digits);
        }
        assertTrue(Binary256.MAX_VALUE.toString(0).length() > 78000);
        assertEquals("0", Binary256.MIN_VALUE.toString(0));
    }
}
