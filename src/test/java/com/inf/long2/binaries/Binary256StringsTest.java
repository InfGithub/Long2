package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class Binary256StringsTest {
    private static final BigInteger ONE = BigInteger.ONE;

    private static void assertRaw(Binary256 expected, Binary256 actual) {
        assertEquals(Binary256Reference.raw(expected), Binary256Reference.raw(actual));
    }

    private static Binary256 reference(String text) {
        BigDecimal decimal = new BigDecimal(text);
        BigInteger coefficient = decimal.unscaledValue().abs();
        int scale = decimal.scale();
        boolean negative = text.startsWith("-");
        return scale >= 0
                ? Binary256Reference.round(negative, coefficient, BigInteger.TEN.pow(scale), 0)
                : Binary256Reference.round(negative, coefficient.multiply(BigInteger.TEN.pow(-scale)), ONE, 0);
    }

    private static BigDecimal exact(Binary256 value) {
        Binary256Reference.Dyadic dyadic = Binary256Reference.decode(value);
        BigInteger magnitude = dyadic.magnitude();
        int scale = Math.toIntExact(dyadic.scale());
        BigDecimal decimal = scale >= 0 ? new BigDecimal(magnitude.shiftLeft(scale))
                : new BigDecimal(magnitude.multiply(BigInteger.valueOf(5).pow(-scale)), -scale);
        return dyadic.negative() ? decimal.negate() : decimal;
    }

    @Test
    void spellsOrdinaryScientificAndSpecialValues() {
        String[][] examples = {
                {"0", "0", "0.0e0"}, {"-0", "-0", "-0.0e0"},
                {"1", "1", "1.0e0"}, {"-1", "-1", "-1.0e0"},
                {"123.5", "123.5", "1.235e2"}, {"0.00125", "0.00125", "1.25e-3"},
                {"1000", "1000", "1.0e3"}, {"0.1", "0.1", "1.0e-1"},
                {"+00012.500", "12.5", "1.25e1"}, {".5", "0.5", "5.0e-1"},
                {"1.", "1", "1.0e0"}, {"1E+3", "1000", "1.0e3"},
                {"NaN", "NaN", "NaN"}, {"Infinity", "Infinity", "Infinity"},
                {"+Infinity", "Infinity", "Infinity"}, {"-Infinity", "-Infinity", "-Infinity"}
        };
        for (String[] example : examples) {
            Binary256 value = Binary256.of(example[0]);
            assertEquals(example[1], value.toString(), example[0]);
            assertEquals(example[2], value.toScientificString(), example[0]);
        }
        assertEquals("NaN", Binary256.fromRawBits(-1, -1, -1, -1).toString());
        assertEquals("NaN", Binary256.fromRawBits(0x7fff_f000_0000_0000L, 0, 0, 1).toScientificString());
        assertRaw(Binary256.NEGATIVE_ZERO, Binary256.of("-0.000e999999999999999999999"));
    }

    @Test
    void rejectsInvalidTextAndChecksTheWholeInput() {
        assertThrows(NullPointerException.class, () -> Binary256.of((String) null));
        for (String text : List.of("", "+", "-", ".", "+.", "e1", "1e", "1e+", "1e-", "1e2e3",
                "1..0", "1.0.0", "1e1.0", " 1", "1 ", "1\n", "1_000", "1,000", "0x1p0", "1f", "1d",
                "nan", "infinity", "INF", "-NaN", "+NaN", "１２", "١", "1e99999999999999999999x",
                "0e99999999999999999999x", "1e-9999999999999999999x", "--1", "++1", "1\u0000")) {
            assertThrows(NumberFormatException.class, () -> Binary256.of(text), text);
        }
    }

    @Test
    void handlesUnboundedExponentTextAndCompensatingZeros() {
        String huge = "9".repeat(300);
        assertRaw(Binary256.POSITIVE_INFINITY, Binary256.of("1e" + huge));
        assertRaw(Binary256.NEGATIVE_INFINITY, Binary256.of("-1e" + huge));
        assertRaw(Binary256.ZERO, Binary256.of("1e-" + huge));
        assertRaw(Binary256.NEGATIVE_ZERO, Binary256.of("-1e-" + huge));
        assertRaw(Binary256.ONE, Binary256.of("1e+" + "0".repeat(300)));
        assertRaw(Binary256.ONE, Binary256.of("0." + "0".repeat(10000) + "1e10001"));
        assertRaw(Binary256.ONE, Binary256.of("1" + "0".repeat(10000) + "e-10000"));
        String coefficient = "123456789".repeat(300);
        String text = coefficient + "e-2699";
        assertRaw(reference(text), Binary256.of(text));
    }

    @Test
    void parsesAgainstIndependentRationalRounding() {
        Random random = new Random(0xDEC256L);
        for (int i = 0; i < 300; i++) {
            BigInteger coefficient = new BigInteger(1 + random.nextInt(1000), random);
            String text = (random.nextBoolean() ? "-" : "") + coefficient + "e" + (random.nextInt(1501) - 750);
            assertRaw(reference(text), Binary256.of(text));
        }
        for (String text : List.of("1e78913", "2e78913", "1e78914", "1e-78983", "1e-78984",
                "0.99999999999999999999999999999999999999999999999999999999999999999999999999",
                "1.00000000000000000000000000000000000000000000000000000000000000000000000001")) {
            assertRaw(reference(text), Binary256.of(text));
            assertRaw(reference("-" + text), Binary256.of("-" + text));
        }
    }

    /** 精确中点及其两侧，覆盖偶数取舍、跨 binade、上溢、下溢和次正规边界。 */
    @Test
    void roundsExactMidpointsAndTheirNeighbors() {
        for (Binary256 lower : List.of(Binary256.ONE.nextDown(), Binary256.ONE, Binary256.ONE.nextUp(),
                Binary256.MIN_NORMAL.nextDown(), Binary256.MIN_NORMAL, Binary256.MIN_VALUE,
                Binary256.MIN_VALUE.nextUp(), Binary256.MAX_VALUE)) {
            BigDecimal a = exact(lower);
            BigDecimal b = lower.equals(Binary256.MAX_VALUE)
                    ? new BigDecimal(ONE.shiftLeft(262144)) : exact(lower.nextUp());
            BigDecimal midpoint = a.add(b).divide(BigDecimal.TWO);
            BigDecimal epsilon = BigDecimal.ONE.scaleByPowerOfTen(-midpoint.scale() - 1);
            for (BigDecimal decimal : List.of(midpoint.subtract(epsilon), midpoint, midpoint.add(epsilon))) {
                String text = decimal.toString();
                assertRaw(reference(text), Binary256.of(text));
                assertRaw(reference("-" + text), Binary256.of("-" + text));
            }
        }
        BigDecimal halfwayToMinimum = exact(Binary256.MIN_VALUE).divide(BigDecimal.TWO);
        assertRaw(Binary256.ZERO, Binary256.of(halfwayToMinimum.toString()));
        assertRaw(Binary256.NEGATIVE_ZERO, Binary256.of("-" + halfwayToMinimum));
    }

    private static void assertRoundTrip(Binary256 value) {
        String plain = value.toString(), scientific = value.toScientificString();
        if (value.isNaN()) {
            assertEquals("NaN", plain);
            assertEquals("NaN", scientific);
            return;
        }
        assertRaw(value, Binary256.of(plain));
        assertRaw(value, Binary256.of(scientific));
        if (value.isFinite()) {
            assertTrue(plain.matches("-?(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?"), plain);
            assertTrue(scientific.matches("-?[0-9]\\.[0-9]+e(0|-?[1-9][0-9]*)"), scientific);
            BigDecimal ordinary = new BigDecimal(plain), normalized = new BigDecimal(scientific);
            assertEquals(0, ordinary.compareTo(normalized));
            assertTrue(normalized.stripTrailingZeros().precision() <= 73);
        }
    }

    @Test
    void roundTripsRawValuesAcrossTheCompleteExponentRange() {
        for (Binary256 value : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE,
                Binary256.ONE.nextDown(), Binary256.ONE.nextUp(), Binary256.MIN_VALUE,
                Binary256.MIN_NORMAL.nextDown(), Binary256.MIN_NORMAL, Binary256.MIN_NORMAL.nextUp(),
                Binary256.MAX_VALUE, Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY, Binary256.NaN)) {
            assertRoundTrip(value);
            assertRoundTrip(value.negate());
        }
        Random random = new Random(0x57A1A6256L);
        for (int i = 0; i < 120; i++) {
            assertRoundTrip(Binary256.fromRawBits(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        }
        assertTrue(Binary256.MAX_VALUE.toString().length() > 78000);
        assertTrue(Binary256.MIN_VALUE.toString().length() > 78000);
        assertTrue(Binary256.MIN_VALUE.toScientificString().length() < 85);
    }

    /** 独立参考：按有效数字数目枚举十进制网格，验证最短性、最近者及十进制偶数取舍。 */
    private static BigDecimal shortestReference(Binary256 value) {
        BigDecimal x = exact(value).abs();
        int exponent = x.precision() - x.scale() - 1;
        for (int precision = 1; precision <= 73; precision++) {
            int scale = precision - exponent - 1;
            BigDecimal nearest = x.setScale(scale, RoundingMode.HALF_EVEN);
            BigDecimal step = BigDecimal.ONE.scaleByPowerOfTen(-scale), best = null;
            for (BigDecimal candidate : List.of(nearest.subtract(step), nearest, nearest.add(step))) {
                if (candidate.signum() <= 0 || candidate.stripTrailingZeros().precision() > precision) {
                    continue;
                }
                Binary256 rounded = reference(candidate.toString());
                if (!Binary256Reference.raw(value.abs()).equals(Binary256Reference.raw(rounded))) {
                    continue;
                }
                int distance = best == null ? -1 : candidate.subtract(x).abs().compareTo(best.subtract(x).abs());
                if (best == null || distance < 0 || (distance == 0 && !candidate.unscaledValue().testBit(0))) {
                    best = candidate;
                }
            }
            if (best != null) {
                return value.signBit() ? best.negate() : best;
            }
        }
        throw new AssertionError("No decimal representation within 73 significant digits");
    }

    @Test
    void emitsTheNearestShortestDecimalAgainstAnIndependentSearch() {
        Random random = new Random(0xC105E57L);
        for (int i = 0; i < 150; i++) {
            long top = ((long) (262143 + random.nextInt(4001) - 2000) << 44)
                    | (random.nextLong() & 0x0000_0fff_ffff_ffffL);
            if (random.nextBoolean()) {
                top |= Long.MIN_VALUE;
            }
            Binary256 value = Binary256.fromRawBits(top, random.nextLong(), random.nextLong(), random.nextLong());
            BigDecimal expected = shortestReference(value).stripTrailingZeros();
            assertEquals(expected.toPlainString(), value.toString());
            assertEquals(0, expected.compareTo(new BigDecimal(value.toScientificString())));
        }
        for (int exponent = -100; exponent <= 100; exponent++) {
            Binary256 power = Binary256.ONE.scalb(exponent);
            for (Binary256 value : List.of(power.nextDown(), power, power.nextUp())) {
                assertEquals(shortestReference(value).stripTrailingZeros().toPlainString(), value.toString());
            }
        }
    }

    @Test
    void shortestIntervalsIncludeTheExtremeNormalAndSubnormalBoundaries() {
        for (Binary256 value : List.of(Binary256.MIN_VALUE, Binary256.MIN_VALUE.nextUp(),
                Binary256.fromRawBits(0, 0, 0, 12345), Binary256.MIN_NORMAL.nextDown(),
                Binary256.MIN_NORMAL, Binary256.MIN_NORMAL.nextUp(), Binary256.MAX_VALUE)) {
            BigDecimal x = exact(value);
            BigDecimal lower = x.add(exact(value.nextDown())).divide(BigDecimal.TWO);
            BigDecimal upper = x.add(value.equals(Binary256.MAX_VALUE)
                    ? new BigDecimal(ONE.shiftLeft(262144)) : exact(value.nextUp())).divide(BigDecimal.TWO);
            boolean inclusive = (value.getSignificandLong(3) & 1) == 0;
            BigDecimal emitted = new BigDecimal(value.toScientificString()).stripTrailingZeros();
            int lc = emitted.compareTo(lower), uc = emitted.compareTo(upper);
            assertTrue((lc > 0 || (inclusive && lc == 0)) && (uc < 0 || (inclusive && uc == 0)));
            int digits = emitted.precision(), exponent = digits - emitted.scale() - 1;
            if (digits == 1) {
                continue;
            }
            // 枚举相邻数量级的少一位网格；若均无整数系数，更少位数也不可能落在区间内。
            for (int e = exponent - 1; e <= exponent + 1; e++) {
                int quantum = e - digits + 2;
                BigDecimal low = lower.scaleByPowerOfTen(-quantum), high = upper.scaleByPowerOfTen(-quantum);
                BigInteger first = low.setScale(0, RoundingMode.CEILING).toBigIntegerExact();
                BigInteger last = high.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
                if (!inclusive && low.compareTo(new BigDecimal(first)) == 0) {
                    first = first.add(ONE);
                }
                if (!inclusive && high.compareTo(new BigDecimal(last)) == 0) {
                    last = last.subtract(ONE);
                }
                first = first.max(BigInteger.TEN.pow(digits - 2));
                last = last.min(BigInteger.TEN.pow(digits - 1).subtract(ONE));
                assertTrue(first.compareTo(last) > 0, "A shorter decimal exists for " + emitted);
            }
        }
    }
}
