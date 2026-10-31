package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.ints.Int256;

class Binary256Test {
    private static final BigInteger ONE = BigInteger.ONE;

    private static void assertValue(Binary256 expected, Binary256 actual) {
        assertEquals(Binary256Reference.raw(expected), Binary256Reference.raw(actual));
    }

    private static void assertDouble(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual));
    }

    @Test
    void checksLayoutClassificationAndDecomposition() {
        assertEquals(new Binary.Format(237, -262142, 262143), Binary256.FORMAT);
        assertEquals(0x3ffff00000000000L, Binary256.ONE.getRawLong(0));
        assertEquals(0x7fffefffffffffffL, Binary256.MAX_VALUE.getRawLong(0));
        assertEquals(-1L, Binary256.MAX_VALUE.getRawLong(3));
        assertTrue(Binary256.MIN_VALUE.isSubnormal());
        assertTrue(Binary256.MIN_NORMAL.isNormal());
        assertTrue(Binary256.POSITIVE_INFINITY.isInfinite());
        assertTrue(Binary256.NaN.isNaN());
        assertFalse(Binary256.NaN.signBit());
        assertTrue(Binary256.NEGATIVE_ZERO.signBit());
        assertEquals(-262378, Binary256.ZERO.scaleExponent());
        assertEquals(-262378, Binary256.MIN_NORMAL.scaleExponent());
        assertEquals(1L << 44, Binary256.MIN_NORMAL.getSignificandLong(0));
        assertEquals(1L, Binary256.MIN_VALUE.getSignificandLong(3));

        Random random = new Random(0x256B175L);
        for (int i = 0; i < 400; i++) {
            long[] raw = {random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()};
            Binary256 value = Binary256.fromRawBits(raw[0], raw[1], raw[2], raw[3]);
            for (int j = 0; j < 4; j++) {
                assertEquals(raw[j], value.getRawLong(j));
            }
            int categories = (value.isZero() ? 1 : 0) + (value.isNormal() ? 1 : 0)
                    + (value.isSubnormal() ? 1 : 0) + (value.isInfinite() ? 1 : 0) + (value.isNaN() ? 1 : 0);
            assertEquals(1, categories);
            if (value.isFinite()) {
                Binary256Reference.Dyadic expected = Binary256Reference.decode(value);
                BigInteger magnitude = BigInteger.ZERO;
                for (int j = 0; j < 4; j++) {
                    magnitude = magnitude.shiftLeft(64).or(UnsignedWordsTest.integer(new long[] {value.getSignificandLong(j)}));
                }
                assertEquals(expected.magnitude(), magnitude);
                assertEquals(expected.scale(), value.scaleExponent());
            }
        }
        assertThrows(IndexOutOfBoundsException.class, () -> Binary256.ONE.getRawLong(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Binary256.ONE.getRawLong(4));
        assertThrows(IndexOutOfBoundsException.class, () -> Binary256.ONE.getSignificandLong(4));
        assertThrows(IllegalStateException.class, () -> Binary256.NaN.scaleExponent());
        assertThrows(IllegalStateException.class, () -> Binary256.POSITIVE_INFINITY.getSignificandLong(0));
    }

    @Test
    void preservesPrimitiveValuesAndMatchesDirectPrimitiveConversions() {
        Random random = new Random(0x256D0B1EL);
        for (int i = 0; i < 1200; i++) {
            double value = Double.longBitsToDouble(random.nextLong());
            Binary256 binary = Binary256.of(value);
            assertDouble(value, binary.toDouble());
            assertEquals(Float.floatToIntBits((float) value), Float.floatToIntBits(binary.toFloat()));
            assertEquals((int) value, binary.toInt());
            assertEquals((long) value, binary.toLong());
            float single = Float.intBitsToFloat(random.nextInt());
            Binary256 fromSingle = Binary256.of(single);
            assertEquals(Float.floatToIntBits(single), Float.floatToIntBits(fromSingle.toFloat()));
            assertDouble(single, fromSingle.toDouble());
            long integer = random.nextLong();
            assertEquals(integer, Binary256.of(integer).toLong());
        }
        for (double value : new double[] {0, -0.0, Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertDouble(value, Binary256.of(value).toDouble());
            assertEquals((int) value, Binary256.of(value).toInt());
            assertEquals((long) value, Binary256.of(value).toLong());
        }
        assertEquals(Long.MIN_VALUE, Binary256.of(Long.MIN_VALUE).toLong());
        assertEquals(Long.MAX_VALUE, Binary256.of(Long.MAX_VALUE).toLong());
    }

    @Test
    void convertsInt256DirectlyAndHandlesMinimumMagnitude() {
        Random random = new Random(0x2561A7L);
        for (int i = 0; i < 250; i++) {
            Int256 value = Int256.of(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            BigInteger integer = new BigInteger(value.toHexString().substring(2), 16);
            if (value.isNegative()) {
                integer = integer.subtract(ONE.shiftLeft(256));
            }
            Binary256 actual = Binary256.of(value);
            assertValue(Binary256Reference.round(integer.signum() < 0, integer.abs(), ONE, 0), actual);
            assertDouble(integer.doubleValue(), actual.toDouble());
        }
        assertValue(Binary256.ONE.scalb(255).negate(), Binary256.of(Int256.MIN_VALUE));
        assertValue(Binary256.ZERO, Binary256.of(Int256.ZERO));
        assertValue(Binary256.ONE.negate(), Binary256.of(Int256.of(-1L)));
    }

    @Test
    void roundsDirectlyToFloatWithoutDoubleRounding() {
        long value = (1L << 54) + (1L << 30) + 1;
        Binary256 binary = Binary256.of(value);
        assertEquals(Float.floatToIntBits((float) value), Float.floatToIntBits(binary.toFloat()));
        assertNotEquals(Float.floatToIntBits((float) binary.toDouble()), Float.floatToIntBits(binary.toFloat()));
        assertEquals(Integer.MAX_VALUE, Binary256.ONE.scalb(64).add(Binary256.ONE).toInt());
        assertEquals(Long.MAX_VALUE, Binary256.ONE.scalb(64).add(Binary256.ONE).toLong());
        assertEquals(Integer.MIN_VALUE, Binary256.ONE.scalb(31).negate().toInt());
        assertEquals(Long.MIN_VALUE, Binary256.ONE.scalb(63).negate().toLong());
        assertEquals(123, Binary256.of(123.75).toInt());
        assertEquals(-123, Binary256.of(-123.75).toLong());
    }

    @Test
    void checksTiesOverflowAndGradualUnderflow() {
        Binary256 half = Binary256.ONE.scalb(-237);
        assertValue(Binary256.ONE, Binary256.ONE.add(half));
        assertValue(Binary256.ONE.nextUp().nextUp(), Binary256.ONE.nextUp().add(half));
        Binary256 overflowHalf = Binary256.ONE.scalb(261906);
        assertValue(Binary256.MAX_VALUE, Binary256.MAX_VALUE.add(overflowHalf.nextDown()));
        assertValue(Binary256.POSITIVE_INFINITY, Binary256.MAX_VALUE.add(overflowHalf));
        assertValue(Binary256.ZERO, Binary256.MIN_VALUE.divide(Binary256.of(2L)));
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.MIN_VALUE.negate().divide(Binary256.of(2L)));
        assertValue(Binary256.MIN_VALUE.add(Binary256.MIN_VALUE), Binary256.MIN_VALUE.multiply(Binary256.of(1.5)));
        Binary256 belowNormalMidpoint = Binary256.MIN_NORMAL.add(Binary256.MIN_NORMAL).subtract(Binary256.MIN_VALUE);
        assertValue(Binary256.MIN_NORMAL, belowNormalMidpoint.divide(Binary256.of(2L)));
        assertValue(Binary256.MIN_NORMAL, Binary256.MIN_NORMAL.nextDown().add(Binary256.MIN_VALUE));
        assertValue(Binary256.MAX_VALUE, Binary256.MAX_VALUE.add(Binary256.MIN_VALUE));
        assertValue(Binary256.MAX_VALUE, Binary256.MAX_VALUE.subtract(Binary256.MIN_VALUE));
        assertValue(Binary256.ONE.scalb(-236), Binary256.ONE.nextUp().subtract(Binary256.ONE));
    }

    @Test
    void fusesMultiplicationBeforeRoundingOverflowAndUnderflow() {
        assertValue(Binary256.MAX_VALUE, Binary256.MAX_VALUE.fma(Binary256.of(2L), Binary256.MAX_VALUE.negate()));
        assertValue(Binary256.POSITIVE_INFINITY, Binary256.MAX_VALUE.multiply(Binary256.of(2L)));
        Binary256 delta = Binary256.ONE.scalb(-119);
        Binary256 a = Binary256.ONE.add(delta), b = Binary256.ONE.subtract(delta);
        assertValue(Binary256.ONE.scalb(-238).negate(), a.fma(b, Binary256.ONE.negate()));
        assertValue(Binary256.ZERO, a.multiply(b).subtract(Binary256.ONE));
        assertValue(Binary256.MIN_VALUE.add(Binary256.MIN_VALUE),
                Binary256.MIN_VALUE.fma(Binary256.of(0.5), Binary256.MIN_VALUE));
        assertValue(Binary256.NEGATIVE_ZERO,
                Binary256.NEGATIVE_ZERO.fma(Binary256.ONE, Binary256.NEGATIVE_ZERO));
        assertValue(Binary256.ZERO, Binary256.ZERO.fma(Binary256.ONE, Binary256.NEGATIVE_ZERO));
    }

    @Test
    void comparesFiniteArithmeticToExactRationals() {
        Random random = new Random(0x256EAAC7L);
        for (int i = 0; i < 110; i++) {
            Binary256 a = randomFinite(random, i < 30), b = randomFinite(random, i < 30);
            Binary256 c = randomFinite(random, i < 30);
            assertValue(Binary256Reference.add(a, b), a.add(b));
            assertValue(Binary256Reference.add(a, b.negate()), a.subtract(b));
            assertValue(Binary256Reference.multiply(a, b), a.multiply(b));
            assertValue(Binary256Reference.divide(a, b), a.divide(b));
            assertValue(Binary256Reference.fma(a, b, c), a.fma(b, c));
            assertValue(Binary256Reference.remainder(a, b, false), a.remainder(b));
            assertValue(Binary256Reference.remainder(a, b, true), a.ieeeRemainder(b));
        }
    }

    @Test
    void computesSquareRootsUsingExactMidpointComparisons() {
        Random random = new Random(0x2565007L);
        for (int i = 0; i < 160; i++) {
            Binary256 value = randomFinite(random, true).abs();
            assertValue(Binary256Reference.sqrt(value), value.sqrt());
        }
        for (Binary256 value : new Binary256[] {Binary256.MIN_VALUE, Binary256.MIN_NORMAL,
                Binary256.MAX_VALUE, Binary256.ONE, Binary256.ONE.nextUp(), Binary256.ONE.nextDown()}) {
            assertValue(Binary256Reference.sqrt(value), value.sqrt());
        }
        assertValue(Binary256.of(3L), Binary256.of(9L).sqrt());
        assertValue(Binary256.ONE.scalb(-131189), Binary256.MIN_VALUE.sqrt());
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.NEGATIVE_ZERO.sqrt());
    }

    @Test
    void distinguishesRemainderRulesAndQuotientParity() {
        for (long a : new long[] {5, 7, 9, -5, -7, -9}) {
            for (long b : new long[] {2, 3, -2, -3}) {
                Binary256 x = Binary256.of(a), y = Binary256.of(b);
                assertDouble((double) a % b, x.remainder(y).toDouble());
                assertDouble(Math.IEEEremainder(a, b), x.ieeeRemainder(y).toDouble());
            }
        }
        Binary256 huge = Binary256.ONE.scalb(262143);
        Binary256 tinyDivisor = Binary256.fromRawBits(0, 0, 0, 3);
        assertValue(Binary256Reference.remainder(huge, tinyDivisor, true), huge.ieeeRemainder(tinyDivisor));
        assertValue(Binary256Reference.remainder(huge, tinyDivisor, false), huge.remainder(tinyDivisor));
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.of(-6L).remainder(Binary256.of(3L)));
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.of(-6L).ieeeRemainder(Binary256.of(3L)));
    }

    @Test
    void checksSpecialValueArithmeticAgainstJava() {
        double[] values = {Double.NEGATIVE_INFINITY, -2, -0.0, 0.0, 2, Double.POSITIVE_INFINITY, Double.NaN};
        for (double a : values) {
            Binary256 x = Binary256.of(a);
            assertDouble(Math.sqrt(a), x.sqrt().toDouble());
            for (double b : values) {
                Binary256 y = Binary256.of(b);
                assertDouble(a + b, x.add(y).toDouble());
                assertDouble(a - b, x.subtract(y).toDouble());
                assertDouble(a * b, x.multiply(y).toDouble());
                assertDouble(a / b, x.divide(y).toDouble());
                assertDouble(a % b, x.remainder(y).toDouble());
                assertDouble(Math.IEEEremainder(a, b), x.ieeeRemainder(y).toDouble());
                for (double c : values) {
                    assertDouble(Math.fma(a, b, c), x.fma(y, Binary256.of(c)).toDouble());
                }
            }
        }
    }

    @Test
    void checksComparisonEqualityAndNaNEncodingPolicy() {
        Binary256 rawNaN = Binary256.fromRawBits(0xfffff00000000000L, 1, 2, 3);
        assertTrue(rawNaN.isNaN());
        assertTrue(rawNaN.signBit());
        assertEquals(3, rawNaN.getRawLong(3));
        assertEquals(Binary256.NaN, rawNaN);
        assertEquals(Binary256.NaN.hashCode(), rawNaN.hashCode());
        assertValue(Binary256.NaN, rawNaN.add(Binary256.ONE));
        assertNotEquals(Binary256.ZERO, Binary256.NEGATIVE_ZERO);
        assertEquals(Binary.Comparison.EQUAL, Binary256.ZERO.compareNumerically(Binary256.NEGATIVE_ZERO));
        assertTrue(Binary256.NEGATIVE_ZERO.compareTo(Binary256.ZERO) < 0);
        assertEquals(Binary.Comparison.UNORDERED, rawNaN.compareNumerically(rawNaN));
        Binary256[] sorted = {Binary256.NEGATIVE_INFINITY, Binary256.of(-2L), Binary256.NEGATIVE_ZERO,
                Binary256.ZERO, Binary256.ONE, Binary256.POSITIVE_INFINITY, rawNaN};
        for (int i = 0; i < sorted.length; i++) {
            for (int j = 0; j < sorted.length; j++) {
                assertEquals(Integer.compare(i, j), Integer.signum(sorted[i].compareTo(sorted[j])));
                assertEquals(i == j, sorted[i].equals(sorted[j]));
            }
        }
        assertFalse(Binary256.ONE.equals(null));
        Object differentType = Long.valueOf(1L);
        assertFalse(Binary256.ONE.equals(differentType));
    }

    @Test
    void stepsAcrossWordNormalAndInfinityBoundaries() {
        assertValue(Binary256.MIN_NORMAL, Binary256.MIN_NORMAL.nextDown().nextUp());
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.MIN_VALUE.negate().nextUp());
        assertValue(Binary256.ZERO, Binary256.MIN_VALUE.nextDown());
        assertValue(Binary256.MIN_VALUE, Binary256.NEGATIVE_ZERO.nextUp());
        assertValue(Binary256.MIN_VALUE.negate(), Binary256.ZERO.nextDown());
        assertValue(Binary256.POSITIVE_INFINITY, Binary256.MAX_VALUE.nextUp());
        assertValue(Binary256.MAX_VALUE, Binary256.POSITIVE_INFINITY.nextDown());
        assertValue(Binary256.MAX_VALUE.negate(), Binary256.NEGATIVE_INFINITY.nextUp());
        assertValue(Binary256.NEGATIVE_INFINITY, Binary256.MAX_VALUE.negate().nextDown());
        assertValue(Binary256.NEGATIVE_INFINITY, Binary256.NEGATIVE_INFINITY.nextDown());
        Random random = new Random(0x25657EFL);
        for (int i = 0; i < 200; i++) {
            Binary256 value = randomFinite(random, true);
            assertTrue(value.nextUp().compareNumerically(value) == Binary.Comparison.GREATER);
            assertTrue(value.nextDown().compareNumerically(value) == Binary.Comparison.LESS);
            assertValue(value, value.nextUp().nextDown());
            assertValue(value, value.nextDown().nextUp());
        }
    }

    @Test
    void scalesWithLongExtremesAndCorrectSubnormalRounding() {
        assertValue(Binary256.POSITIVE_INFINITY, Binary256.ONE.scalb(Long.MAX_VALUE));
        assertValue(Binary256.ZERO, Binary256.ONE.scalb(Long.MIN_VALUE));
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.ONE.negate().scalb(Long.MIN_VALUE));
        assertValue(Binary256.NEGATIVE_ZERO, Binary256.NEGATIVE_ZERO.scalb(Long.MAX_VALUE));
        assertValue(Binary256.ONE, Binary256.MIN_VALUE.scalb(262378));
        assertValue(Binary256.MIN_VALUE, Binary256.ONE.scalb(-262378));
        assertValue(Binary256.ZERO, Binary256.ONE.scalb(-262379));
        Random random = new Random(0x2565CA1L);
        for (int i = 0; i < 100; i++) {
            Binary256 value = randomFinite(random, true);
            int shift = random.nextInt(1100000) - 550000;
            Binary256Reference.Dyadic x = Binary256Reference.decode(value);
            assertValue(Binary256Reference.round(x.negative(), x.magnitude(), ONE, x.scale() + shift), value.scalb(shift));
        }
    }

    @Test
    void convertsForeignPrecisionAndPreservesLowTailInformation() {
        Random random = new Random(0x256C055L);
        for (int i = 0; i < 100; i++) {
            BigInteger magnitude = new BigInteger(800, random).setBit(799);
            boolean negative = random.nextBoolean();
            long scale = i < 20 ? -262378L - 800 + i : random.nextInt(1000) - 1000;
            assertValue(Binary256Reference.round(negative, magnitude, ONE, scale),
                    Binary256.of(source(negative, magnitude, scale)));
        }
        BigInteger midpoint = ONE.shiftLeft(799).add(ONE.shiftLeft(562));
        for (int delta = -1; delta <= 1; delta++) {
            BigInteger magnitude = midpoint.add(BigInteger.valueOf(delta));
            assertValue(Binary256Reference.round(false, magnitude, ONE, -799), Binary256.of(source(false, magnitude, -799)));
        }
        assertValue(Binary256.ZERO, Binary256.of(source(false, ONE.shiftLeft(799), -263178)));
        assertValue(Binary256.MIN_VALUE, Binary256.of(source(false, ONE.shiftLeft(799).add(ONE), -263178)));
    }

    @Test
    void streamsVeryLargeSourcePrecisionWithoutAllocatingItsSignificand() {
        int precision = Integer.MAX_VALUE;
        int groups = (int) ((precision + 63L) / 64);
        Binary<?> source = (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isNaN", "isInfinite", "isZero", "signBit" -> false;
                    case "format" -> new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    case "scaleExponent" -> 1L - precision;
                    case "getSignificandLong" -> {
                        int group = groups - 1 - (int) arguments[0];
                        long word = 0;
                        for (long bit : new long[] {precision - 1L, precision - 238L, 0}) {
                            if (bit / 64 == group) {
                                word |= 1L << (bit & 63);
                            }
                        }
                        yield word;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertValue(Binary256.ONE.nextUp(), Binary256.of(source));
    }

    @Test
    void rejectsNullArgumentsEvenForSpecialReceivers() {
        assertThrows(NullPointerException.class, () -> Binary256.of((Int<?>) null));
        assertThrows(NullPointerException.class, () -> Binary256.of((Binary<?>) null));
        for (Binary256 value : new Binary256[] {Binary256.ONE, Binary256.NaN, Binary256.POSITIVE_INFINITY}) {
            assertThrows(NullPointerException.class, () -> value.add(null));
            assertThrows(NullPointerException.class, () -> value.subtract(null));
            assertThrows(NullPointerException.class, () -> value.multiply(null));
            assertThrows(NullPointerException.class, () -> value.divide(null));
            assertThrows(NullPointerException.class, () -> value.remainder(null));
            assertThrows(NullPointerException.class, () -> value.ieeeRemainder(null));
            assertThrows(NullPointerException.class, () -> value.fma(null, Binary256.ZERO));
            assertThrows(NullPointerException.class, () -> value.fma(Binary256.ONE, null));
            assertThrows(NullPointerException.class, () -> value.compareTo(null));
            assertThrows(NullPointerException.class, () -> value.compareNumerically(null));
        }
    }

    private static Binary256 randomFinite(Random random, boolean wide) {
        int exponent = wide ? random.nextInt(0x7ffff) : 262143 + random.nextInt(1001) - 500;
        long top = (random.nextBoolean() ? Long.MIN_VALUE : 0) | ((long) exponent << 44)
                | (random.nextLong() & 0x00000fffffffffffL);
        return Binary256.fromRawBits(top, random.nextLong(), random.nextLong(), random.nextLong());
    }

    private static Binary<?> source(boolean negative, BigInteger magnitude, long scale) {
        int precision = Math.max(2, magnitude.bitLength());
        int groups = (precision + 63) / 64;
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isNaN", "isInfinite" -> false;
                    case "isZero" -> magnitude.signum() == 0;
                    case "signBit" -> negative;
                    case "format" -> new Binary.Format(precision, -1000000, 1000000);
                    case "scaleExponent" -> scale;
                    case "getSignificandLong" -> magnitude.shiftRight((groups - 1 - (int) arguments[0]) * 64).longValue();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
