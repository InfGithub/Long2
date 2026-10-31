package com.inf.long2.binaries;

import static com.inf.long2.binaries.Binary256CompatibilityFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.baseline.Binary256Baseline;
import com.inf.long2.ints.Int256;

/** 比较冻结基线的完整输出编码，NaN 的符号和 payload 差异也必须使测试失败。 */
class Binary256CompatibilityTest {
    private enum Arithmetic {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, IEEE_REMAINDER
    }

    private static Bits raw(Binary256 value) {
        return new Bits(value.getRawLong(0), value.getRawLong(1), value.getRawLong(2), value.getRawLong(3));
    }

    private static Bits raw(Binary256Baseline value) {
        return new Bits(value.getRawLong(0), value.getRawLong(1), value.getRawLong(2), value.getRawLong(3));
    }

    private static void assertRaw(Binary256Baseline expected, Binary256 actual, String operation) {
        assertEquals(raw(expected), raw(actual), operation);
    }

    private static <T extends Binary<T>> T calculate(T a, T b, Arithmetic operation) {
        return switch (operation) {
            case ADD -> a.add(b);
            case SUBTRACT -> a.subtract(b);
            case MULTIPLY -> a.multiply(b);
            case DIVIDE -> a.divide(b);
            case REMAINDER -> a.remainder(b);
            case IEEE_REMAINDER -> a.ieeeRemainder(b);
        };
    }

    private static void assertArithmetic(Bits a, Bits b) {
        Binary256 actualA = a.actual(), actualB = b.actual();
        Binary256Baseline expectedA = a.baseline(), expectedB = b.baseline();
        for (Arithmetic operation : Arithmetic.values()) {
            assertRaw(calculate(expectedA, expectedB, operation), calculate(actualA, actualB, operation),
                    operation + " a=" + a + " b=" + b);
        }
        assertEquals(expectedA.compareNumerically(expectedB), actualA.compareNumerically(actualB), "numeric comparison");
        assertEquals(expectedA.compareTo(expectedB), actualA.compareTo(actualB), "total comparison");
        assertEquals(expectedA.equals(expectedB), actualA.equals(actualB), "equality");
        assertEquals(a, raw(actualA), "receiver must remain unchanged");
        assertEquals(b, raw(actualB), "argument must remain unchanged");
    }

    private static void assertFma(Bits a, Bits b, Bits c) {
        Binary256 actualA = a.actual(), actualB = b.actual(), actualC = c.actual();
        assertRaw(a.baseline().fma(b.baseline(), c.baseline()), actualA.fma(actualB, actualC),
                "FMA a=" + a + " b=" + b + " c=" + c);
        assertEquals(a, raw(actualA), "FMA receiver must remain unchanged");
        assertEquals(b, raw(actualB), "FMA multiplier must remain unchanged");
        assertEquals(c, raw(actualC), "FMA addend must remain unchanged");
    }

    private static void assertPrimitives(Bits bits) {
        Binary256 actual = bits.actual();
        Binary256Baseline expected = bits.baseline();
        assertEquals(Float.floatToRawIntBits(expected.toFloat()), Float.floatToRawIntBits(actual.toFloat()),
                () -> "toFloat input=" + bits);
        assertEquals(Double.doubleToRawLongBits(expected.toDouble()), Double.doubleToRawLongBits(actual.toDouble()),
                () -> "toDouble input=" + bits);
        assertEquals(expected.toInt(), actual.toInt(), () -> "toInt input=" + bits);
        assertEquals(expected.toLong(), actual.toLong(), () -> "toLong input=" + bits);
        assertEquals(bits, raw(actual), "conversion must leave the receiver unchanged");
    }

    private static List<Bits> specialValues() {
        List<Bits> values = new ArrayList<>();
        for (Bits positive : List.of(ZERO, MINIMUM, MINIMUM_NORMAL, UNIT, MAXIMUM, INFINITY, NAN,
                new Bits(0x7ffff00000000000L, 0, 0, 1), new Bits(0x7ffff00000000001L, -1, 0, 0))) {
            values.add(positive);
            values.add(positive.negative());
        }
        return values;
    }

    private static List<Bits> boundaryValues() {
        List<Bits> values = new ArrayList<>(specialValues());
        BigInteger[] fractions = {BigInteger.ZERO, ONE, ONE.shiftLeft(44).subtract(ONE), ONE.shiftLeft(44),
                ONE.shiftLeft(64).subtract(ONE), ONE.shiftLeft(64), ONE.shiftLeft(128).subtract(ONE),
                ONE.shiftLeft(128), ONE.shiftLeft(192).subtract(ONE), ONE.shiftLeft(192), FRACTION_MASK};
        int[] exponents = {-262142, -262141, -1075, -1074, -1023, -1022, -150, -149, -127, -126,
                -1, 0, 1, 23, 24, 30, 31, 32, 52, 53, 62, 63, 64, 126, 127, 128, 1022, 1023, 1024, 262142, 262143};
        for (int exponent : exponents) {
            for (BigInteger fraction : fractions) {
                Bits value = normal(false, exponent, fraction);
                values.add(value);
                values.add(value.negative());
            }
        }
        for (int bit : new int[] {0, 1, 43, 44, 63, 64, 127, 128, 191, 192, 234, 235}) {
            Bits value = bits(ONE.shiftLeft(bit));
            values.add(value);
            values.add(value.negative());
        }
        values.add(bits(FRACTION_MASK));
        values.add(bits(FRACTION_MASK).negative());
        return values;
    }

    @Test
    void preservesAllConstantEncodings() {
        assertEquals(Binary256Baseline.FORMAT, Binary256.FORMAT);
        Binary256Baseline[] expected = {Binary256Baseline.ZERO, Binary256Baseline.NEGATIVE_ZERO,
                Binary256Baseline.ONE, Binary256Baseline.MIN_VALUE, Binary256Baseline.MIN_NORMAL,
                Binary256Baseline.MAX_VALUE, Binary256Baseline.POSITIVE_INFINITY,
                Binary256Baseline.NEGATIVE_INFINITY, Binary256Baseline.NaN};
        Binary256[] actual = {Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE, Binary256.MIN_VALUE,
                Binary256.MIN_NORMAL, Binary256.MAX_VALUE, Binary256.POSITIVE_INFINITY,
                Binary256.NEGATIVE_INFINITY, Binary256.NaN};
        for (int i = 0; i < expected.length; i++) {
            assertRaw(expected[i], actual[i], "constant " + i);
        }
    }

    @Test
    void pinsCanonicalNaNAndSignedZeroRatherThanOnlyNumericEquality() {
        for (Bits encoding : specialValues()) {
            Binary256 value = encoding.actual();
            if (encoding.baseline().isNaN()) {
                assertEquals(NAN, raw(value.negate()));
                assertEquals(NAN, raw(value.abs()));
                assertEquals(NAN, raw(value.scalb(0)));
                assertEquals(NAN, raw(value.nextUp()));
                assertEquals(NAN, raw(value.nextDown()));
                assertEquals(NAN, raw(Binary256.of((Binary<?>) value)));
                assertEquals(0x7fc00000, Float.floatToRawIntBits(value.toFloat()));
                assertEquals(0x7ff8000000000000L, Double.doubleToRawLongBits(value.toDouble()));
            }
        }
        assertEquals(ZERO, raw(ZERO.negative().actual().add(ZERO.actual())));
        assertEquals(ZERO.negative(), raw(ZERO.negative().actual().add(ZERO.negative().actual())));
        assertEquals(ZERO.negative(), raw(MINIMUM.negative().actual().divide(Binary256.of(2L))));
        assertEquals(0x80000000, Float.floatToRawIntBits(ZERO.negative().actual().toFloat()));
        assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(ZERO.negative().actual().toDouble()));
    }

    @Test
    void preservesClassificationsDecompositionUnaryOperationsAndHashes() {
        List<Bits> values = boundaryValues();
        Random random = new Random(0x256C1A551F1L);
        for (int i = 0; i < 180; i++) {
            values.add(new Bits(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        }
        Object unrelated = Long.valueOf(1L);
        for (Bits bits : values) {
            Binary256 actual = bits.actual();
            Binary256Baseline expected = bits.baseline();
            assertEquals(expected.format(), actual.format());
            assertEquals(expected.signBit(), actual.signBit());
            assertEquals(expected.isNaN(), actual.isNaN());
            assertEquals(expected.isInfinite(), actual.isInfinite());
            assertEquals(expected.isFinite(), actual.isFinite());
            assertEquals(expected.isZero(), actual.isZero());
            assertEquals(expected.isNormal(), actual.isNormal());
            assertEquals(expected.isSubnormal(), actual.isSubnormal());
            assertEquals(expected.hashCode(), actual.hashCode(), () -> "hash input=" + bits);
            assertEquals(expected.equals(null), actual.equals(null));
            assertEquals(expected.equals(unrelated), actual.equals(unrelated));
            if (expected.isFinite()) {
                assertEquals(expected.scaleExponent(), actual.scaleExponent());
                for (int group = 0; group < 4; group++) {
                    assertEquals(expected.getSignificandLong(group), actual.getSignificandLong(group));
                }
            }
            assertRaw(expected.negate(), actual.negate(), "negate " + bits);
            assertRaw(expected.abs(), actual.abs(), "abs " + bits);
            assertRaw(expected.sqrt(), actual.sqrt(), "sqrt " + bits);
            assertRaw(expected.nextUp(), actual.nextUp(), "nextUp " + bits);
            assertRaw(expected.nextDown(), actual.nextDown(), "nextDown " + bits);
            assertPrimitives(bits);
            assertEquals(bits, raw(actual), "unary operations must leave the receiver unchanged");
        }
    }

    @Test
    void preservesSpecialValueArithmeticAndEveryFmaSignCombination() {
        List<Bits> values = specialValues();
        for (Bits a : values) {
            for (Bits b : values) {
                assertArithmetic(a, b);
                for (Bits c : values) {
                    assertFma(a, b, c);
                }
            }
        }
    }

    @Test
    void preservesRandomArithmeticForNearbyAndDistantExponents() {
        Random random = new Random(0x256A8174E71CL);
        int[] edges = {-262143, -262142, -262141, -1075, -150, -1, 0, 1, 127, 1023, 262142, 262143};
        for (int i = 0; i < 320; i++) {
            int ea = i < 100 ? edges[random.nextInt(edges.length)] : random.nextInt(524286) - 262142;
            int eb = i < 100 ? edges[random.nextInt(edges.length)] : i < 220 ? ea : random.nextInt(1001) - 500;
            int ec = i < 100 ? edges[random.nextInt(edges.length)] : random.nextInt(1001) - 500;
            Bits a = randomFinite(random, ea), b = randomFinite(random, eb), c = randomFinite(random, ec);
            assertArithmetic(a, b);
            assertFma(a, b, c);
        }
    }

    @Test
    void preservesAdditionAtExponentGapAndBinadeBoundaries() {
        int[] gaps = {236, 237, 238, 239, 240, 474, 475, 476, 1000, 200000, 524285};
        for (int ea : new int[] {-262142, -262141, -1022, -126, -1, 0, 1, 1023, 262143}) {
            for (int gap : gaps) {
                int eb = ea - gap;
                if (eb < -262378) {
                    continue;
                }
                Bits b = eb == -262378 ? MINIMUM : exact(BigInteger.valueOf(3), eb - 1L);
                for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
                    Bits a = normal(false, ea, fraction);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertRaw(x.baseline().add(y.baseline()), x.actual().add(y.actual()), "add gap=" + gap);
                            assertRaw(y.baseline().add(x.baseline()), y.actual().add(x.actual()), "reverse add gap=" + gap);
                            assertRaw(x.baseline().subtract(y.baseline()), x.actual().subtract(y.actual()), "subtract gap=" + gap);
                            assertRaw(y.baseline().subtract(x.baseline()), y.actual().subtract(x.actual()), "reverse subtract gap=" + gap);
                        }
                    }
                }
            }
        }
        Bits belowHalf = exact(BigInteger.valueOf(3), -239);
        assertEquals(bits(ONE.shiftLeft(236).multiply(BigInteger.valueOf(262143)).subtract(ONE)),
                raw(UNIT.actual().subtract(belowHalf.actual())));
    }

    @Test
    void preservesArithmeticMidpointsOverflowThresholdAndGradualUnderflow() {
        for (int exponent : new int[] {-262141, -1022, -126, -1, 0, 1, 127, 1023, 262143}) {
            Bits half = power(exponent - 237);
            BigInteger halfRaw = new BigInteger(half.toString(), 16);
            for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
                Bits a = normal(false, exponent, fraction);
                for (int delta = -1; delta <= 1; delta++) {
                    Bits b = bits(halfRaw.add(BigInteger.valueOf(delta)));
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertRaw(x.baseline().add(y.baseline()), x.actual().add(y.actual()),
                                    "addition midpoint exponent=" + exponent + " delta=" + delta + " a=" + x + " b=" + y);
                            assertRaw(x.baseline().subtract(y.baseline()), x.actual().subtract(y.actual()),
                                    "subtraction midpoint exponent=" + exponent + " delta=" + delta + " a=" + x + " b=" + y);
                        }
                    }
                }
            }
        }
        Bits two = exact(BigInteger.TWO, 0), threeHalves = exact(BigInteger.valueOf(3), -1);
        for (Bits value : List.of(MINIMUM, bits(BigInteger.valueOf(3)), bits(FRACTION_MASK),
                MINIMUM_NORMAL, normal(false, -262142, FRACTION_MASK))) {
            assertArithmetic(value, two);
            assertArithmetic(value.negative(), two);
            assertArithmetic(value, threeHalves);
            assertArithmetic(value.negative(), threeHalves);
        }
    }

    @Test
    void preservesFmaMidpointTailsCancellationAndOverflowRecovery() {
        Bits b = exact(ONE.shiftLeft(119).add(ONE), -119);
        for (int shift : new int[] {-100000, -1, 0, 1, 100000}) {
            Bits a = exact(ONE.shiftLeft(118).add(ONE), shift - 118L);
            for (Bits x : List.of(a, a.negative())) {
                for (Bits y : List.of(b, b.negative())) {
                    for (Bits z : List.of(MINIMUM, MINIMUM.negative(), ZERO, ZERO.negative())) {
                        assertFma(x, y, z);
                    }
                    for (int gap : new int[] {238, 239, 240, 474, 475, 476}) {
                        for (int exponent : new int[] {shift - gap, shift + gap}) {
                            Bits z = power(exponent);
                            assertFma(x, y, z);
                            assertFma(x, y, z.negative());
                        }
                    }
                }
            }
        }
        assertFma(MAXIMUM, exact(BigInteger.TWO, 0), MAXIMUM.negative());
        assertFma(MAXIMUM, MAXIMUM, MAXIMUM.negative());
        assertFma(MINIMUM, power(-1), MINIMUM);
        assertFma(MINIMUM, power(-1), MINIMUM.negative());
        Bits a = exact(ONE.shiftLeft(118).add(ONE), -118);
        BigInteger lower = ONE.shiftLeft(236).multiply(BigInteger.valueOf(262143))
                .add(ONE.shiftLeft(118)).add(ONE.shiftLeft(117));
        assertEquals(bits(lower), raw(a.actual().multiply(b.actual())));
        assertEquals(bits(lower.add(ONE)), raw(a.actual().fma(b.actual(), MINIMUM.actual())));
    }

    @Test
    void preservesRemainderQuotientParityAndVeryLargeExponentDifferences() {
        for (int exponent : new int[] {-262142, -100000, -1, 0, 1, 100000, 262143}) {
            for (int divisor : new int[] {1, 2, 3, 5, 7}) {
                Bits a = power(exponent);
                Bits b = bits(BigInteger.valueOf(divisor));
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertRaw(x.baseline().remainder(y.baseline()), x.actual().remainder(y.actual()), "wide remainder");
                        assertRaw(x.baseline().ieeeRemainder(y.baseline()), x.actual().ieeeRemainder(y.actual()), "wide IEEE remainder");
                    }
                }
            }
        }
        for (int numerator : new int[] {1, 3, 5, 7, 9, 11}) {
            for (int denominator : new int[] {2, 3, 4, 6}) {
                Bits a = exact(BigInteger.valueOf(numerator), 0), b = exact(BigInteger.valueOf(denominator), 0);
                assertArithmetic(a, b);
                assertArithmetic(a.negative(), b);
            }
        }
    }

    @Test
    void preservesPrimitiveConstructorsIncludingRawNaNsAndIntegerExtremes() {
        long[] doubles = {0, Long.MIN_VALUE, 1, Long.MIN_VALUE | 1, 0x000fffffffffffffL,
                0x0010000000000000L, 0x7fefffffffffffffL, 0xffefffffffffffffL,
                0x7ff0000000000000L, 0xfff0000000000000L, 0x7ff8000000000000L,
                0x7ff0000000000001L, 0xfff0000000000001L, 0xffffffffffffffffL};
        for (long bits : doubles) {
            double value = Double.longBitsToDouble(bits);
            assertRaw(Binary256Baseline.of(value), Binary256.of(value), "of double raw=" + Long.toHexString(bits));
        }
        int[] singles = {0, Integer.MIN_VALUE, 1, Integer.MIN_VALUE | 1, 0x007fffff, 0x00800000,
                0x7f7fffff, 0xff7fffff, 0x7f800000, 0xff800000, 0x7fc00000, 0x7f800001, 0xff800001, -1};
        for (int bits : singles) {
            float value = Float.intBitsToFloat(bits);
            assertRaw(Binary256Baseline.of(value), Binary256.of(value), "of float raw=" + Integer.toHexString(bits));
        }
        for (long value : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            assertRaw(Binary256Baseline.of(value), Binary256.of(value), "of long=" + value);
        }
        Random random = new Random(0x256C0A57L);
        for (int i = 0; i < 800; i++) {
            long integer = random.nextLong();
            double binary64 = Double.longBitsToDouble(random.nextLong());
            float binary32 = Float.intBitsToFloat(random.nextInt());
            assertRaw(Binary256Baseline.of(integer), Binary256.of(integer), "random long=" + integer);
            assertRaw(Binary256Baseline.of(binary64), Binary256.of(binary64), "random double");
            assertRaw(Binary256Baseline.of(binary32), Binary256.of(binary32), "random float");
        }
    }

    private record Dyadic(BigInteger magnitude, long scale) {
    }

    private static Dyadic primitive(long bits, int precision, int emin, int emax, int exponentBits) {
        int fractionBits = precision - 1;
        long exponent = bits >>> fractionBits;
        if (exponent == (1L << exponentBits) - 1) {
            return new Dyadic(ONE, emax + 1L);
        }
        BigInteger fraction = BigInteger.valueOf(bits & ((1L << fractionBits) - 1));
        return exponent == 0 ? new Dyadic(fraction, (long) emin - fractionBits)
                : new Dyadic(fraction.setBit(fractionBits), exponent - emax - fractionBits);
    }

    private static void assertPrimitiveMidpoints(int precision, int emin, int emax, int exponentBits) {
        int fractionBits = precision - 1;
        long hidden = 1L << fractionBits;
        long infinity = ((1L << exponentBits) - 1) << fractionBits;
        List<Long> lower = new ArrayList<>(List.of(0L, 1L, 2L, 3L, hidden - 2, hidden - 1, hidden, hidden + 1,
                ((long) emax << fractionBits) - 1, (long) emax << fractionBits, infinity - 3, infinity - 2, infinity - 1));
        Random random = new Random(0x256A1D901A7L + precision);
        for (int i = 0; i < 100; i++) {
            lower.add(random.nextLong(infinity));
        }
        for (long bits : lower) {
            Dyadic a = primitive(bits, precision, emin, emax, exponentBits);
            Dyadic b = primitive(bits + 1, precision, emin, emax, exponentBits);
            long scale = Math.min(a.scale(), b.scale());
            BigInteger sum = a.magnitude().shiftLeft(Math.toIntExact(a.scale() - scale))
                    .add(b.magnitude().shiftLeft(Math.toIntExact(b.scale() - scale)));
            Bits midpoint = exact(sum, scale - 1);
            BigInteger midpointRaw = new BigInteger(midpoint.toString(), 16);
            for (int delta = -1; delta <= 1; delta++) {
                Bits value = bits(midpointRaw.add(BigInteger.valueOf(delta)));
                assertPrimitives(value);
                assertPrimitives(value.negative());
            }
        }
    }

    @Test
    void preservesFloatAndDoubleMidpointsAndBothImmediateSourceNeighbors() {
        assertPrimitiveMidpoints(24, -126, 127, 8);
        assertPrimitiveMidpoints(53, -1022, 1023, 11);
    }

    @Test
    void preservesIntegerTruncationAndClampingOnBothSidesOfEveryEndpoint() {
        for (BigInteger integer : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(31).subtract(ONE), ONE.shiftLeft(31),
                ONE.shiftLeft(31).add(ONE), ONE.shiftLeft(63).subtract(ONE), ONE.shiftLeft(63), ONE.shiftLeft(63).add(ONE))) {
            for (int fractionalBits : new int[] {2, 160}) {
                for (int delta = -3; delta <= 3; delta++) {
                    BigInteger magnitude = integer.shiftLeft(fractionalBits).add(BigInteger.valueOf(delta));
                    if (magnitude.signum() >= 0) {
                        Bits value = exact(magnitude, -fractionalBits);
                        assertPrimitives(value);
                        assertPrimitives(value.negative());
                    }
                }
            }
        }
    }

    @Test
    void preservesScalingAtLongExtremesAndRoundingTransitionExponents() {
        long[] factors = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -524522, -524521, -262380, -262379, -262378,
                -237, -1, 0, 1, 237, 262142, 262143, 524521, 524522, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        List<Bits> values = new ArrayList<>(specialValues());
        for (Bits value : List.of(bits(FRACTION_MASK), normal(false, 0, ONE), normal(false, -1, FRACTION_MASK))) {
            values.add(value);
            values.add(value.negative());
        }
        for (Bits bits : values) {
            Binary256Baseline expected = bits.baseline();
            Binary256 actual = bits.actual();
            for (long factor : factors) {
                assertRaw(expected.scalb(factor), actual.scalb(factor), "scalb factor=" + factor + " input=" + bits);
            }
            if (expected.isFinite() && !expected.isZero()) {
                BigInteger magnitude = BigInteger.ZERO;
                for (int group = 0; group < 4; group++) {
                    magnitude = magnitude.shiftLeft(64).or(new BigInteger(Long.toUnsignedString(expected.getSignificandLong(group))));
                }
                long exponent = expected.scaleExponent() + magnitude.bitLength() - 1;
                for (long boundary : new long[] {-262379 - exponent, -262142 - exponent, 262143 - exponent}) {
                    for (int delta = -1; delta <= 1; delta++) {
                        long factor = boundary + delta;
                        assertRaw(expected.scalb(factor), actual.scalb(factor), "scalb transition factor=" + factor + " input=" + bits);
                    }
                }
            }
        }
    }

    private static void assertForeign(Binary<?> source, String label) {
        assertRaw(Binary256Baseline.of(source), Binary256.of(source), label);
    }

    @Test
    void preservesSameFormatConversionsForAllSpecialValuesAndPayloads() {
        for (Bits bits : boundaryValues()) {
            assertRaw(Binary256Baseline.of((Binary<?>) bits.baseline()), Binary256.of((Binary<?>) bits.actual()),
                    "same format=" + bits);
            assertForeign(bits.actual(), "foreign implementation with Binary256 format=" + bits);
        }
    }

    @Test
    void preservesForeignPrecisionsWordBoundariesAndLowStickyTails() {
        int[] precisions = {2, 3, 23, 24, 53, 63, 64, 65, 127, 128, 129, 191, 192, 193,
                235, 236, 237, 238, 239, 240, 255, 256, 257, 511, 512, 513, 799, 800, 801, 4096};
        int[] exponents = {-262380, -262379, -262378, -262143, -262142, -1, 0, 1, 262143, 262144};
        Random random = new Random(0x256F0AE16AL);
        for (int precision : precisions) {
            Binary.Format format = new Binary.Format(precision, -400000, 400000);
            for (int exponent : exponents) {
                for (BigInteger magnitude : List.of(ONE.shiftLeft(precision - 1), ONE.shiftLeft(precision).subtract(ONE),
                        new BigInteger(precision, random).setBit(precision - 1))) {
                    for (boolean negative : new boolean[] {false, true}) {
                        assertForeign(binary(format, negative, magnitude, (long) exponent - precision + 1),
                                "foreign precision=" + precision + " exponent=" + exponent + " negative=" + negative);
                    }
                }
            }
            if (precision >= 238) {
                BigInteger midpoint = ONE.shiftLeft(precision - 1).add(ONE.shiftLeft(precision - 238));
                for (int delta = -1; delta <= 1; delta++) {
                    BigInteger magnitude = midpoint.add(BigInteger.valueOf(delta));
                    for (boolean negative : new boolean[] {false, true}) {
                        assertForeign(binary(format, negative, magnitude, 1L - precision),
                                "foreign midpoint precision=" + precision + " delta=" + delta);
                    }
                }
            }
            for (int emin : new int[] {-262379, -262378, -262142, 0}) {
                Binary.Format subnormalFormat = new Binary.Format(precision, emin, 400000);
                for (BigInteger magnitude : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(precision - 1).subtract(ONE))) {
                    for (boolean negative : new boolean[] {false, true}) {
                        assertForeign(binary(subnormalFormat, negative, magnitude, (long) emin - precision + 1),
                                "foreign subnormal precision=" + precision + " emin=" + emin + " negative=" + negative);
                    }
                }
            }
        }
    }

    @Test
    void preservesForeignFormatsWithExtremeExponentAndPrecisionParameters() {
        for (int precision : new int[] {2, 237, 800, 65536}) {
            Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
            BigInteger magnitude = ONE.shiftLeft(precision - 1).add(ONE);
            for (int exponent : new int[] {Integer.MIN_VALUE, -262379, 0, 262143, Integer.MAX_VALUE}) {
                for (boolean negative : new boolean[] {false, true}) {
                    assertForeign(binary(format, negative, magnitude, (long) exponent - precision + 1),
                            "extreme format precision=" + precision + " exponent=" + exponent);
                }
            }
        }
        for (int exponent : new int[] {Integer.MIN_VALUE, -262379, -262378, 0, 262143, Integer.MAX_VALUE}) {
            assertForeign(sparseBinary(exponent), "sparse maximum precision exponent=" + exponent);
        }
    }

    private static void assertInteger(Int<?> source, String label) {
        assertRaw(Binary256Baseline.of(source), Binary256.of(source), label);
    }

    @Test
    void preservesConcreteInt256AndGenericWidthsIncludingMinimumNegativeMagnitudes() {
        for (Int256 value : new Int256[] {Int256.ZERO, Int256.ONE, Int256.MIN_VALUE, Int256.MAX_VALUE, Int256.of(-1L)}) {
            assertInteger(value, "Int256 endpoint");
        }
        Random random = new Random(0x2561A7C0A7L);
        for (int i = 0; i < 150; i++) {
            assertInteger(Int256.of(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()), "random Int256");
        }
        for (int width : new int[] {64, 128, 256, 320, 1024, 65536, 262144, 262208}) {
            BigInteger minimum = ONE.shiftLeft(width - 1).negate();
            List<BigInteger> values = new ArrayList<>(List.of(BigInteger.ZERO, ONE, ONE.negate(), minimum,
                    minimum.add(ONE), minimum.negate().subtract(ONE)));
            if (width > 237) {
                for (int delta = 0; delta <= 4; delta++) {
                    BigInteger value = ONE.shiftLeft(237).add(BigInteger.valueOf(delta));
                    values.add(value);
                    values.add(value.negate());
                }
            }
            for (BigInteger value : values) {
                assertInteger(integer(width, value), "generic Int width=" + width + " bitLength=" + value.bitLength());
            }
        }
    }

    @Test
    void preservesIntegerConversionsAtTheFiniteOverflowMidpoint() {
        BigInteger midpoint = ONE.shiftLeft(262144).subtract(ONE.shiftLeft(261906));
        for (int delta = -1; delta <= 1; delta++) {
            BigInteger value = midpoint.add(BigInteger.valueOf(delta));
            assertInteger(integer(262208, value), "integer overflow midpoint delta=" + delta);
            assertInteger(integer(262208, value.negate()), "negative integer overflow midpoint delta=" + delta);
        }
    }

    private static void assertFailure(Class<? extends Throwable> exception, Executable expected, Executable actual, String label) {
        assertThrowsExactly(exception, expected, "baseline " + label);
        assertThrowsExactly(exception, actual, label);
    }

    @Test
    void preservesNullFailuresBeforeSpecialValueShortcuts() {
        assertFailure(NullPointerException.class, () -> Binary256Baseline.of((Binary<?>) null), () -> Binary256.of((Binary<?>) null), "of Binary null");
        assertFailure(NullPointerException.class, () -> Binary256Baseline.of((Int<?>) null), () -> Binary256.of((Int<?>) null), "of Int null");
        for (Bits bits : specialValues()) {
            Binary256 actual = bits.actual();
            Binary256Baseline expected = bits.baseline();
            for (Arithmetic operation : Arithmetic.values()) {
                assertFailure(NullPointerException.class, () -> calculate(expected, null, operation),
                        () -> calculate(actual, null, operation), operation + " null receiver=" + bits);
            }
            assertFailure(NullPointerException.class, () -> expected.fma(null, Binary256Baseline.ZERO), () -> actual.fma(null, Binary256.ZERO), "FMA null multiplier");
            assertFailure(NullPointerException.class, () -> expected.fma(Binary256Baseline.ZERO, null), () -> actual.fma(Binary256.ZERO, null), "FMA null addend");
            assertFailure(NullPointerException.class, () -> expected.compareTo(null), () -> actual.compareTo(null), "compareTo null");
            assertFailure(NullPointerException.class, () -> expected.compareNumerically(null), () -> actual.compareNumerically(null), "compareNumerically null");
        }
    }

    @Test
    void preservesInvalidIndexAndNonfiniteDecompositionExceptionPrecedence() {
        for (Bits bits : specialValues()) {
            Binary256 actual = bits.actual();
            Binary256Baseline expected = bits.baseline();
            for (int index : new int[] {Integer.MIN_VALUE, -1, 4, Integer.MAX_VALUE}) {
                assertFailure(IndexOutOfBoundsException.class, () -> expected.getRawLong(index), () -> actual.getRawLong(index), "raw index=" + index);
                Class<? extends Throwable> exception = expected.isFinite() ? IndexOutOfBoundsException.class : IllegalStateException.class;
                assertFailure(exception, () -> expected.getSignificandLong(index), () -> actual.getSignificandLong(index), "significand index=" + index);
            }
            if (!expected.isFinite()) {
                assertFailure(IllegalStateException.class, expected::scaleExponent, actual::scaleExponent, "nonfinite scale");
                assertFailure(IllegalStateException.class, () -> expected.getSignificandLong(0), () -> actual.getSignificandLong(0), "nonfinite valid index");
            }
        }
    }
}
