package com.inf.long2.binaries;

import static com.inf.long2.binaries.Binary256CompatibilityFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void preservesSquareRootRoundingAtBinadeEdgesAndExtremes() {
        for (int exponent : new int[] {-262142, -262141, -1, 0, 1, 262142, 262143}) {
            for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(235), FRACTION_MASK.subtract(ONE), FRACTION_MASK)) {
                Bits input = normal(false, exponent, fraction);
                assertRaw(input.baseline().sqrt(), input.actual().sqrt(), "sqrt binade edge=" + input);
            }
        }
        for (int bit : new int[] {0, 1, 63, 64, 127, 128, 191, 192, 235}) {
            Bits input = bits(ONE.shiftLeft(bit));
            assertRaw(input.baseline().sqrt(), input.actual().sqrt(), "sqrt subnormal=" + input);
        }
    }

    private static void assertSquareRoot(Bits bits) {
        Binary256 input = bits.actual(), result = input.sqrt();
        assertRaw(bits.baseline().sqrt(), result, "sqrt input=" + bits);
        assertEquals(raw(Binary256Reference.sqrt(input)), raw(result));
        assertEquals(bits, raw(input));
    }

    private static void assertScaledSquareRoot(BigInteger square, BigInteger root, long scale) {
        Bits input = exact(square, scale);
        assertSquareRoot(input);
        if ((scale & 1) == 0) {
            assertEquals(exact(root, scale / 2), raw(input.actual().sqrt()));
        }
    }

    @Test
    void preservesSmallSquareRootsAcrossNormalAndSubnormalScales() {
        long[] roots = {1, 2, 3, 5, 7, 31, 32, 255, 65535, 65536,
                0x7fff_ffffL, 0x8000_0000L, 0x8000_0001L, 3_037_000_499L, 0xffff_ffffL};
        for (long value : roots) {
            BigInteger root = BigInteger.valueOf(value), square = root.multiply(root);
            int length = square.bitLength();
            for (int shift = 0; shift <= 236 - length; shift++) {
                assertScaledSquareRoot(square, root, -262378L + shift);
            }
            long normalScale = -262142L - length + 1, maximumScale = 262143L - length + 1;
            for (long scale : new long[] {normalScale, normalScale + 1, -1000, -1, 0, 1, 1000,
                    maximumScale - 1, maximumScale}) {
                assertScaledSquareRoot(square, root, scale);
            }
        }
    }

    @Test
    void preservesSquareRootNearMissesAndTheReducedMagnitudeWidthBoundary() {
        List<BigInteger> roots = List.of(ONE, BigInteger.valueOf(3), BigInteger.valueOf(31),
                ONE.shiftLeft(31).subtract(ONE), ONE.shiftLeft(31).add(ONE), ONE.shiftLeft(32).subtract(ONE),
                ONE.shiftLeft(32), ONE.shiftLeft(32).add(ONE), ONE.shiftLeft(63).subtract(ONE),
                ONE.shiftLeft(64).subtract(ONE), ONE.shiftLeft(118).add(ONE));
        for (BigInteger root : roots) {
            BigInteger square = root.multiply(root);
            for (int delta : new int[] {-16, -8, -1, 0, 1, 8, 16}) {
                BigInteger magnitude = square.add(BigInteger.valueOf(delta));
                if (magnitude.signum() <= 0) {
                    continue;
                }
                for (long scale : new long[] {-262378, -1, 0, 1, 262144L - magnitude.bitLength()}) {
                    assertSquareRoot(exact(magnitude, scale));
                }
            }
        }
    }

    @Test
    void preservesRandomLowWidthSquareRootsAcrossScaleParityAndRange() {
        Random random = new Random(0x256E7AC75007L);
        for (int i = 0; i < 5000; i++) {
            int length = i % 64 + 1;
            BigInteger magnitude = new BigInteger(length, random).setBit(length - 1).setBit(0);
            long scale = i % 3 == 0 ? -262378L + random.nextInt(237 - length)
                    : (long) random.nextInt(-262142, 262144) - length + 1;
            assertSquareRoot(exact(magnitude, scale));
        }
    }

    @Test
    void preservesNewtonSquareRootsAcrossEverySubnormalMagnitudeWidth() {
        Random random = new Random(0x2565007B175L);
        for (int length = 1; length <= 236; length++) {
            for (BigInteger magnitude : List.of(ONE.shiftLeft(length - 1), ONE.shiftLeft(length).subtract(ONE),
                    new BigInteger(length, random).setBit(length - 1))) {
                assertSquareRoot(bits(magnitude));
            }
        }
    }

    @Test
    void preservesNewtonSquareRootRoundingBoundariesAcrossExponentParityAndRange() {
        for (BigInteger radicand : squareRootRoundingBoundaries()) {
            int parity = radicand.bitLength() - 473;
            BigInteger magnitude = radicand.shiftRight(236 + parity);
            assertEquals(radicand, magnitude.shiftLeft(236 + parity));
            for (int exponent : new int[] {-262142, -262141, -1023, -1022, -1, 0, 1, 1022, 1023, 262142, 262143}) {
                if ((exponent & 1) == parity) {
                    assertSquareRoot(normal(false, exponent, magnitude.and(FRACTION_MASK)));
                }
            }
        }
    }

    @Test
    void preservesNewtonFirstDivisionAdjacentRootsAcrossExponentRange() {
        for (long seed : new long[] {0x8000_0001L, 2_500_000_000L, 3_000_000_000L, 0x1_0000_0000L}) {
            BigInteger estimate = BigInteger.valueOf(seed).shiftLeft(205);
            int parity = seed == 0x1_0000_0000L ? 1 : 0;
            BigInteger magnitude = estimate.pow(2).shiftRight(236 + parity).subtract(ONE);
            for (int delta : new int[] {-2, -1, 0, 1}) {
                BigInteger candidate = magnitude.add(BigInteger.valueOf(delta));
                if (candidate.bitLength() != 237) {
                    continue;
                }
                for (int exponent : new int[] {-262142, -262141, -1023, -1022, -1, 0, 1, 1022, 1023, 262142, 262143}) {
                    if ((exponent & 1) == parity) {
                        assertSquareRoot(normal(false, exponent, candidate.and(FRACTION_MASK)));
                    }
                }
            }
        }
    }

    @Test
    void preservesNewtonShortDivisionSeedCarryBoundaryForNormalAndSubnormalInputs() {
        BigInteger boundary = ONE.shiftLeft(32).subtract(ONE).pow(2);
        for (BigInteger prefix : List.of(boundary.subtract(ONE), boundary, boundary.add(ONE), ONE.shiftLeft(64).subtract(ONE))) {
            for (int width : new int[] {64, 96, 128, 192, 236, 237}) {
                int tailBits = width - 64;
                for (BigInteger tail : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(tailBits).subtract(ONE))) {
                    if (tail.bitLength() > tailBits) {
                        continue;
                    }
                    BigInteger magnitude = prefix.shiftLeft(tailBits).or(tail);
                    if (width < 237) {
                        assertSquareRoot(bits(magnitude));
                    } else {
                        for (int exponent : new int[] {-262141, -1023, -1, 1, 1023, 262143}) {
                            assertSquareRoot(normal(false, exponent, magnitude.and(FRACTION_MASK)));
                        }
                    }
                }
            }
        }
    }

    @Test
    void preserves128PrefixSquareRootsAndNonzeroTailsAcrossExponentRange() {
        for (BigInteger root : List.of(ONE.shiftLeft(63).add(ONE), new BigInteger("12345678901234567891"),
                ONE.shiftLeft(64).subtract(ONE))) {
            BigInteger square = root.pow(2);
            int tailBits = 237 - square.bitLength(), parity = square.bitLength() - 127;
            for (BigInteger tail : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(63), ONE.shiftLeft(64),
                    ONE.shiftLeft(tailBits - 1), ONE.shiftLeft(tailBits).subtract(ONE))) {
                BigInteger magnitude = square.shiftLeft(tailBits).or(tail);
                for (int exponent : new int[] {-262142, -262141, -1023, -1022, -1, 0, 1, 1022, 1023, 262142, 262143}) {
                    if ((exponent & 1) == parity) {
                        assertSquareRoot(normal(false, exponent, magnitude.and(FRACTION_MASK)));
                        if (tail.signum() == 0) {
                            assertScaledSquareRoot(square, root, (long) exponent - 236 + tailBits);
                        }
                    }
                }
            }
            for (int shift : new int[] {0, 1, 31, 32, 63, 64, 107, 108}) {
                if (square.bitLength() + shift <= 236) {
                    BigInteger magnitude = square.shiftLeft(shift);
                    assertScaledSquareRoot(square, root, -262378L + shift);
                    assertSquareRoot(bits(magnitude.add(ONE)));
                }
            }
        }
    }

    @Test
    void preserves64BitNewtonSeedCarriesAndFirstAdjacentRoots() {
        for (BigInteger seed : List.of(ONE.shiftLeft(63).add(ONE), new BigInteger("10000000000000000000"),
                new BigInteger("12000000000000000000"), ONE.shiftLeft(64))) {
            BigInteger square = seed.shiftLeft(173).pow(2);
            int parity = square.bitLength() > 473 ? 1 : 0;
            BigInteger magnitude = square.shiftRight(236 + parity).subtract(ONE);
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger candidate = magnitude.add(BigInteger.valueOf(delta));
                if (candidate.bitLength() != 237) {
                    continue;
                }
                for (int exponent : new int[] {-262142, -262141, -1023, -1022, -1, 0, 1, 1022, 1023, 262142, 262143}) {
                    if ((exponent & 1) == parity) {
                        assertSquareRoot(normal(false, exponent, candidate.and(FRACTION_MASK)));
                    }
                }
            }
        }
    }

    @Test
    void preservesTwoDigitNewtonTrialsAndSingleDigitSeedsAcrossScales() {
        for (BigInteger magnitude : List.of(ONE, BigInteger.valueOf(31), BigInteger.valueOf(197), ONE.shiftLeft(31).subtract(ONE),
                ONE.shiftLeft(32).subtract(ONE), ONE.shiftLeft(33).subtract(ONE), ONE.shiftLeft(64).subtract(ONE))) {
            for (long scale : new long[] {-262378, -1024, -1, 0, 1, 262144L - magnitude.bitLength()}) {
                assertSquareRoot(exact(magnitude, scale));
            }
        }
    }

    @Test
    void preservesSquareRootShortAndZeroRemaindersAcrossExponentRange() {
        List<BigInteger> magnitudes = new ArrayList<>();
        for (String magnitude : new String[] {
                "13331ad4ac5e3c4a8d92fb5eb5b9a38d4fd25edef3dd02de6eca0bf76248",
                "1791641e19c01c6463d8486d457be5237c97072cb93ccc188b55ac7fe543",
                "12ed5505e18cd1553d93205a31fc2cda2a1f0140961a28e00c4873eeadcb",
                "11756d75569a298566fe9470f95faf5ca713c03b0dea1c1e775e49edce47"}) {
            magnitudes.add(new BigInteger(magnitude, 16));
        }
        magnitudes.add(ONE.shiftLeft(64).add(ONE).pow(2).shiftLeft(108));
        for (BigInteger magnitude : magnitudes) {
            for (int exponent : new int[] {-262142, -1022, -2, 0, 2, 1022, 262142}) {
                assertSquareRoot(normal(false, exponent, magnitude.and(FRACTION_MASK)));
            }
        }
    }

    @Test
    void preservesSquareRootRoundingCarriesAcrossThreeLowWords() {
        for (long value : new long[] {0x8000_0001L, 2_500_000_000L, 3_000_000_000L}) {
            BigInteger seed = BigInteger.valueOf(value), estimate = seed.shiftLeft(205);
            BigInteger magnitude = seed.pow(2).shiftLeft(174).subtract(ONE);
            BigInteger root = estimate.subtract(ONE);
            BigInteger remainder = magnitude.shiftLeft(236).subtract(root.pow(2));
            assertTrue(remainder.compareTo(root) > 0);
            assertEquals(ONE.shiftLeft(192).subtract(ONE), root.and(ONE.shiftLeft(192).subtract(ONE)));
            for (int exponent : new int[] {-262142, -1022, -2, 0, 2, 1022, 262142}) {
                Bits input = normal(false, exponent, magnitude.and(FRACTION_MASK));
                assertSquareRoot(input);
                assertEquals(exact(seed, exponent / 2L - 31), raw(input.actual().sqrt()));
            }
        }
    }

    @Test
    void preservesRandomNewtonSquareRootsAcrossTheFullFiniteRange() {
        Random random = new Random(0x2565A7EF10A7L);
        for (int i = 0; i < 5000; i++) {
            Bits input = i % 4 == 0 ? bits(new BigInteger(236, random).setBit(0))
                    : normal(false, random.nextInt(-262142, 262144), new BigInteger(236, random));
            assertSquareRoot(input);
        }
    }

    private static void assertMultiply(Bits a, Bits b) {
        Binary256 x = a.actual(), y = b.actual();
        assertRaw(a.baseline().multiply(b.baseline()), x.multiply(y), "multiply a=" + a + " b=" + b);
        assertEquals(a, raw(x));
        assertEquals(b, raw(y));
    }

    private static List<BigInteger> sparseFractions() {
        List<BigInteger> fractions = new ArrayList<>();
        BigInteger word = ONE.shiftLeft(64).subtract(ONE);
        for (int mask = 1; mask < 16; mask++) {
            BigInteger fraction = BigInteger.ZERO;
            for (int group = 0; group < 4; group++) {
                if ((mask & (1 << group)) != 0) {
                    fraction = fraction.or(word.shiftLeft(group * 64));
                }
            }
            fractions.add(fraction.and(FRACTION_MASK));
        }
        return fractions;
    }

    private static void assertProductAndRoundingResidual(Bits a, Bits b) {
        assertMultiply(a, b);
        Binary256 x = a.actual(), y = b.actual();
        assertEquals(raw(Binary256Reference.multiply(x, y)), raw(x.multiply(y)));
        // 抵消已舍入的乘积，使乘积低位的进位和 sticky 差异能够影响最终编码。
        Bits addend = raw(a.baseline().multiply(b.baseline()).negate());
        assertFma(a, b, addend);
        assertEquals(raw(Binary256Reference.fma(x, y, addend.actual())), raw(x.fma(y, addend.actual())));
    }

    private static void assertFiniteMultiply(Bits a, Bits b) {
        assertMultiply(a, b);
        assertEquals(raw(Binary256Reference.multiply(a.actual(), b.actual())), raw(a.actual().multiply(b.actual())));
    }

    @Test
    void preservesCompactedProductsAtEveryTrailingBitCountAndWordBoundary() {
        int[] widths = {1, 2, 53, 63, 64, 65, 119, 127, 128, 129, 177, 191, 192, 193, 237};
        for (int shift = 0; shift <= 236; shift++) {
            int width = 237 - shift;
            BigInteger magnitude = ONE.shiftLeft(width).subtract(ONE);
            if (width > 2 && (shift & 1) != 0) {
                magnitude = magnitude.subtract(BigInteger.TWO);
            }
            Bits a = exact(magnitude, 1L - width);
            for (int otherWidth : widths) {
                BigInteger other = ONE.shiftLeft(otherWidth).subtract(ONE);
                if (otherWidth > 2 && (otherWidth & 1) == 0) {
                    other = other.subtract(BigInteger.TWO);
                }
                Bits b = exact(other, 1L - otherWidth);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertFiniteMultiply(x, y);
                        assertFiniteMultiply(y, x);
                    }
                }
            }
        }
    }

    @Test
    void preservesScalarProductsAtSubnormalMidpointsAndBothLongWordBoundaries() {
        List<BigInteger> magnitudes = List.of(BigInteger.valueOf(3), BigInteger.valueOf(5), BigInteger.valueOf(7),
                ONE.shiftLeft(63).subtract(ONE), ONE.shiftLeft(63).add(ONE), ONE.shiftLeft(64).subtract(ONE));
        int[] discardedBits = {-1, 0, 1, 2, 31, 32, 63, 64, 65, 95, 96, 127, 128, 129};
        for (int discarded : discardedBits) {
            long scale = -262378L - discarded;
            long ascale = Math.floorDiv(scale, 2), bscale = scale - ascale;
            for (BigInteger am : magnitudes) {
                for (BigInteger bm : magnitudes) {
                    Bits a = exact(am, ascale), b = exact(bm, bscale);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertFiniteMultiply(x, y);
                            assertFiniteMultiply(y, x);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesRandomCompactedProductsAcrossRoundingAndExponentEdges() {
        Random random = new Random(0x256C0A7AC7L);
        int[] widths = {2, 3, 53, 63, 64, 65, 106, 118, 119, 127, 128, 129, 176, 177, 178, 191, 192, 193, 236, 237};
        int[] exponents = {-262381, -262380, -262379, -262378, -262377, -262143, -262142, -262141,
                -1, 0, 1, 262142, 262143, 262144};
        for (int i = 0; i < 2000; i++) {
            int awidth = widths[i % widths.length], bwidth = widths[random.nextInt(widths.length)];
            BigInteger am = new BigInteger(awidth, random).setBit(awidth - 1).setBit(0);
            BigInteger bm = new BigInteger(bwidth, random).setBit(bwidth - 1).setBit(0);
            int sum = exponents[i % exponents.length], ae = Math.floorDiv(sum, 2), be = sum - ae;
            Bits a = exact(am, (long) ae - awidth + 1), b = exact(bm, (long) be - bwidth + 1);
            if (random.nextBoolean()) {
                a = a.negative();
            }
            if (random.nextBoolean()) {
                b = b.negative();
            }
            assertFiniteMultiply(a, b);
            assertFiniteMultiply(b, a);
        }
    }

    @Test
    void preservesSparseProductsAndFmaResidualsAcrossAllZeroGroupPatterns() {
        List<BigInteger> fractions = sparseFractions();
        for (BigInteger af : fractions) {
            for (BigInteger bf : fractions) {
                Bits a = normal(false, 0, af), b = normal(false, 0, bf);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertProductAndRoundingResidual(x, y);
                    }
                }
            }
        }
    }

    @Test
    void preservesSubnormalSparseProductsAndFmaResidualsAcrossOperandWordLengths() {
        List<BigInteger> fractions = sparseFractions();
        for (BigInteger af : fractions) {
            for (BigInteger bf : fractions) {
                Bits a = bits(af), b = normal(false, 262143, bf);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertProductAndRoundingResidual(x, y);
                        assertProductAndRoundingResidual(y, x);
                    }
                }
            }
        }
    }

    @Test
    void preservesMultiplicationAtGuaranteedOverflowAndUnderflowThresholds() {
        for (int sum : new int[] {-262382, -262381, -262380, -262379, -262378, 262142, 262143, 262144, 262145}) {
            int ea = Math.floorDiv(sum, 2), eb = sum - ea;
            for (BigInteger af : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(235), FRACTION_MASK)) {
                for (BigInteger bf : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(235), FRACTION_MASK)) {
                    Bits a = normal(false, ea, af), b = normal(false, eb, bf);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertMultiply(x, y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesMultiplicationBySignedPowersOfTwoInBothOperandOrders() {
        List<Bits> values = new ArrayList<>(specialValues());
        for (int exponent : new int[] {-262142, -237, -1, 0, 1, 237, 262143}) {
            values.add(normal(false, exponent, ONE));
            values.add(normal(false, exponent, FRACTION_MASK));
        }
        for (int bit : new int[] {0, 1, 63, 64, 127, 128, 191, 192, 235}) {
            values.add(bits(ONE.shiftLeft(bit).add(ONE)));
        }
        for (int exponent : new int[] {-262378, -262377, -262142, -1, 0, 1, 262142, 262143}) {
            Bits unit = power(exponent);
            for (Bits input : values) {
                for (Bits a : List.of(input, input.negative())) {
                    for (Bits b : List.of(unit, unit.negative())) {
                        assertMultiply(a, b);
                        assertMultiply(b, a);
                    }
                }
            }
        }
    }

    @Test
    void preservesFmaWithSignedUnitMultipliersIncludingRawNaNsAndZeros() {
        List<Bits> values = new ArrayList<>(specialValues());
        for (int exponent : new int[] {-262142, -1, 0, 1, 262143}) {
            values.add(normal(false, exponent, ONE));
            values.add(normal(true, exponent, FRACTION_MASK));
        }
        for (Bits a : values) {
            for (Bits c : values) {
                assertFma(a, UNIT, c);
                assertFma(a, UNIT.negative(), c);
            }
        }
    }

    @Test
    void preservesRemaindersAroundMagnitudeAndHalfMagnitudeShortcuts() {
        for (int exponent : new int[] {-262141, -1, 0, 1, 262143}) {
            for (BigInteger fraction : List.of(BigInteger.ZERO, ONE.shiftLeft(235), FRACTION_MASK)) {
                Bits divisor = normal(false, exponent, fraction);
                for (Bits center : List.of(divisor, normal(false, exponent - 1, fraction))) {
                    BigInteger encoding = new BigInteger(center.toString(), 16);
                    for (int delta : new int[] {-1, 0, 1}) {
                        Bits a = bits(encoding.add(BigInteger.valueOf(delta)));
                        for (Bits x : List.of(a, a.negative())) {
                            for (Bits y : List.of(divisor, divisor.negative())) {
                                assertRemainders(x, y, "magnitude shortcut");
                            }
                        }
                    }
                }
            }
        }
        for (int exponent : new int[] {-262378, -262143, -262142, -200000, 0, 100000, 262143}) {
            Bits divisor = power(exponent);
            for (Bits a : List.of(MINIMUM, bits(BigInteger.valueOf(3)), bits(FRACTION_MASK),
                    normal(false, -262142, ONE), normal(false, 0, FRACTION_MASK), MAXIMUM)) {
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(divisor, divisor.negative())) {
                        assertRemainders(x, y, "power-of-two divisor");
                    }
                }
            }
        }
    }

    @Test
    void preservesInt256RoundingForEveryMagnitudeLengthAndCarryPosition() {
        for (int length = 1; length <= 255; length++) {
            BigInteger top = ONE.shiftLeft(length - 1);
            List<BigInteger> magnitudes = new ArrayList<>(List.of(top.subtract(ONE), top, top.add(ONE),
                    ONE.shiftLeft(length).subtract(ONE)));
            int discarded = length - 237;
            if (discarded > 0) {
                BigInteger half = ONE.shiftLeft(discarded - 1);
                for (int carry : new int[] {1, 63, 64, 127, 128, 191, 192, 236, 237}) {
                    BigInteger retained = ONE.shiftLeft(236).or(ONE.shiftLeft(carry).subtract(ONE));
                    for (int delta : new int[] {-1, 0, 1}) {
                        magnitudes.add(retained.shiftLeft(discarded).add(half).add(BigInteger.valueOf(delta)));
                    }
                }
            }
            for (BigInteger magnitude : magnitudes) {
                if (magnitude.bitLength() > 255) {
                    continue;
                }
                for (boolean negative : new boolean[] {false, true}) {
                    BigInteger signed = negative ? magnitude.negate() : magnitude;
                    Int256 source = Int256.of(signed.shiftRight(192).longValue(), signed.shiftRight(128).longValue(),
                            signed.shiftRight(64).longValue(), signed.longValue());
                    Binary256 actual = Binary256.of(source);
                    assertRaw(Binary256Baseline.of(source), actual, "Int256 length=" + length);
                    assertEquals(raw(Binary256Reference.round(negative && magnitude.signum() != 0, magnitude, ONE, 0)), raw(actual));
                    for (int group = 0; group < 4; group++) {
                        assertEquals(signed.shiftRight((3 - group) * 64).longValue(), source.getLong(group));
                    }
                }
            }
        }
        assertInteger(Int256.MIN_VALUE, "Int256 minimum magnitude");
    }

    @Test
    void preservesWideIntegerRoundingAcrossEveryWordOfTheRetainedWindow() {
        for (int length : new int[] {238, 255, 256, 257, 300, 474, 475, 511, 512, 513, 712, 713, 1024}) {
            int discarded = length - 237, width = ((length + 64) / 64) * 64;
            for (int carry : new int[] {1, 63, 64, 127, 128, 191, 192, 236, 237}) {
                BigInteger retained = ONE.shiftLeft(236).or(ONE.shiftLeft(carry).subtract(ONE));
                BigInteger center = retained.shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                    for (boolean negative : new boolean[] {false, true}) {
                        Int<?> source = integer(width, negative ? magnitude.negate() : magnitude);
                        Binary256 actual = Binary256.of(source);
                        assertRaw(Binary256Baseline.of(source), actual, "wide integer rounding length=" + length);
                        assertEquals(raw(Binary256Reference.round(negative, magnitude, ONE, 0)), raw(actual));
                    }
                }
            }
        }
    }

    @Test
    void preservesDirectRoundPackAtCarryUnderflowAndOverflowMidpoints() {
        List<BigInteger> retained = List.of(BigInteger.ZERO, ONE, BigInteger.TWO, ONE.shiftLeft(63).subtract(ONE),
                ONE.shiftLeft(64).subtract(ONE), ONE.shiftLeft(128).subtract(ONE), ONE.shiftLeft(192).subtract(ONE),
                ONE.shiftLeft(236).subtract(ONE), ONE.shiftLeft(236), ONE.shiftLeft(237).subtract(ONE));
        for (long quantum : new long[] {-262378, 0, 261907}) {
            for (int discarded : new int[] {1, 2, 63, 64, 65, 127, 128, 192, 236, 237, 474}) {
                for (BigInteger prefix : retained) {
                    BigInteger center = prefix.shiftLeft(discarded).add(ONE.shiftLeft(discarded - 1));
                    for (int delta : new int[] {-1, 0, 1}) {
                        BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                        if (magnitude.signum() == 0) {
                            continue;
                        }
                        int padding = magnitude.bitLength() < 2 ? 1 : 0;
                        BigInteger sourceMagnitude = magnitude.shiftLeft(padding);
                        Binary.Format format = new Binary.Format(sourceMagnitude.bitLength(), -400000, 400000);
                        for (boolean negative : new boolean[] {false, true}) {
                            long scale = quantum - discarded;
                            Binary<?> source = binary(format, negative, sourceMagnitude, scale - padding);
                            Binary256 actual = Binary256.of(source);
                            assertRaw(Binary256Baseline.of(source), actual, "roundPack quantum=" + quantum + " dropped=" + discarded);
                            assertEquals(raw(Binary256Reference.round(negative, magnitude, ONE, scale)), raw(actual));
                        }
                    }
                }
            }
        }
    }

    @Test
    void boundsGenericIntegerBitReadsWhilePreservingMidpointTails() {
        for (int length : new int[] {1, 63, 64, 237, 238, 239, 240, 255, 256, 257, 4096, 65536, 262144}) {
            int width = ((length + 64) / 64) * 64;
            List<BigInteger> values = new ArrayList<>(List.of(ONE.shiftLeft(length - 1), ONE.shiftLeft(length).subtract(ONE)));
            if (length > 239) {
                for (int parity : new int[] {0, 1}) {
                    BigInteger prefix = ONE.shiftLeft(236).add(BigInteger.valueOf(parity));
                    BigInteger midpoint = prefix.shiftLeft(length - 237).setBit(length - 238);
                    for (int delta : new int[] {-1, 0, 1}) {
                        values.add(midpoint.add(BigInteger.valueOf(delta)));
                    }
                }
            }
            for (BigInteger magnitude : values) {
                for (boolean negative : new boolean[] {false, true}) {
                    BigInteger signed = negative ? magnitude.negate() : magnitude;
                    int[] reads = new int[2];
                    Binary256 actual = Binary256.of(integer(width, signed, reads));
                    assertRaw(Binary256Baseline.of(integer(width, signed)), actual, "bounded Int length=" + length);
                    assertTrue(reads[0] <= 239, "testBit reads=" + reads[0]);
                    assertTrue(reads[1] <= 1, "bitCount reads=" + reads[1]);
                    if (length <= 239) {
                        assertEquals(0, reads[1]);
                    }
                }
            }
        }
    }

    @Test
    void boundsGenericMinimumNegativeMagnitudeReads() {
        for (int width : new int[] {64, 256, 320, 65536, 262144}) {
            BigInteger minimum = ONE.shiftLeft(width - 1).negate();
            int[] reads = new int[2];
            assertRaw(Binary256Baseline.of(integer(width, minimum)), Binary256.of(integer(width, minimum, reads)),
                    "minimum Int width=" + width);
            assertTrue(reads[0] <= 239);
            assertEquals(0, reads[1]);
        }
    }

    @Test
    void stopsMaximumPrecisionSubnormalReadsAtTheZeroRoundingBoundary() {
        int precision = Integer.MAX_VALUE;
        Binary.Format format = new Binary.Format(precision, 0, 0);
        long scale = 1L - precision, guard = -262379L - scale;
        int groups = (int) ((precision + 63L) / 64), guardGroup = groups - 1 - (int) (guard / 64);
        for (boolean negative : new boolean[] {false, true}) {
            int[] reads = new int[1];
            Binary<?> below = sparseBinary(format, negative, scale, new long[] {0}, index -> {
                assertTrue(index <= guardGroup, "irrelevant low group=" + index);
                reads[0]++;
            });
            assertEquals(negative ? ZERO.negative() : ZERO, raw(Binary256.of(below)));
            assertTrue(reads[0] <= guardGroup + 1);
            Binary<?> above = sparseBinary(format, negative, scale, new long[] {guard, 0}, index ->
                    assertTrue(index <= guardGroup + 4 || index == groups - 1, "unexpected group=" + index));
            assertEquals(negative ? MINIMUM.negative() : MINIMUM, raw(Binary256.of(above)));
        }
    }

    @Test
    void skipsSignificandReadsWhenTheSourceRangeAlreadyDeterminesTheResult() {
        int precision = Integer.MAX_VALUE;
        Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
        for (int exponent : new int[] {Integer.MIN_VALUE, -262380, 262144, Integer.MAX_VALUE}) {
            for (boolean negative : new boolean[] {false, true}) {
                Binary<?> source = sparseBinary(format, negative, (long) exponent - precision + 1,
                        new long[] {precision - 1L}, index -> { throw new AssertionError("unnecessary read=" + index); });
                Bits expected = exponent < -262379 ? ZERO : INFINITY;
                assertEquals(negative ? expected.negative() : expected, raw(Binary256.of(source)));
            }
        }
        Binary<?> tiny = sparseBinary(format, false, (long) Integer.MIN_VALUE - precision + 1,
                new long[] {0}, index -> { throw new AssertionError("unnecessary read=" + index); });
        assertEquals(ZERO, raw(Binary256.of(tiny)));
    }

    @Test
    void preservesSubnormalSourceValuesOnBothSidesOfTheMinimumRoundingBit() {
        for (int precision : new int[] {240, 255, 256, 257, 511, 512, 65536}) {
            for (int emin : new int[] {-262379, -262378, -262315, -262314, -262313, -262142, 0}) {
                Binary.Format format = new Binary.Format(precision, emin, 0);
                long scale = (long) emin - precision + 1, guard = -262379L - scale;
                if (guard < 0 || guard >= precision) {
                    continue;
                }
                BigInteger center = ONE.shiftLeft((int) guard);
                for (int delta : new int[] {-1, 0, 1}) {
                    BigInteger magnitude = center.add(BigInteger.valueOf(delta));
                    for (boolean negative : new boolean[] {false, true}) {
                        Binary<?> source = binary(format, negative, magnitude, scale);
                        Binary256 actual = Binary256.of(source);
                        assertRaw(Binary256Baseline.of(source), actual, "subnormal source precision=" + precision + " emin=" + emin);
                        assertEquals(raw(Binary256Reference.round(negative, magnitude, ONE, scale)), raw(actual));
                    }
                }
            }
        }
    }

    private static void assertAddSubtract(Bits a, Bits b) {
        Binary256 x = a.actual(), y = b.actual();
        assertRaw(a.baseline().add(b.baseline()), x.add(y), "aligned add a=" + a + " b=" + b);
        assertRaw(a.baseline().subtract(b.baseline()), x.subtract(y), "aligned subtract a=" + a + " b=" + b);
        assertEquals(raw(Binary256Reference.add(x, y)), raw(x.add(y)));
        assertEquals(raw(Binary256Reference.add(x, y.negate())), raw(x.subtract(y)));
        assertEquals(a, raw(x));
        assertEquals(b, raw(y));
    }

    @Test
    void preservesSingleWorkspaceAdditionAcrossAllAlignmentBitsAndBorrowDirections() {
        for (int distance = 0; distance <= 240; distance++) {
            for (BigInteger af : List.of(BigInteger.ZERO, FRACTION_MASK)) {
                for (BigInteger bf : List.of(BigInteger.ZERO, FRACTION_MASK)) {
                    Bits a = normal(false, 0, af), b = normal(false, -distance, bf);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertAddSubtract(x, y);
                            assertAddSubtract(y, x);
                        }
                    }
                }
            }
        }
        for (int exponent : new int[] {-262142, -262141, 262142, 262143}) {
            for (int distance : new int[] {0, 1, 63, 64, 65, 127, 128, 191, 192, 237, 238, 239}) {
                int otherExponent = exponent - distance;
                if (otherExponent < -262378) {
                    continue;
                }
                Bits a = normal(false, exponent, FRACTION_MASK), b = power(otherExponent);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertAddSubtract(x, y);
                        assertAddSubtract(y, x);
                    }
                }
            }
        }
    }

    @Test
    void preservesFieldMultiplicationAcrossLimbCarriesAndSubnormalInputWidths() {
        List<BigInteger> fractions = new ArrayList<>(List.of(BigInteger.ZERO, ONE, FRACTION_MASK));
        for (int bit : new int[] {63, 64, 127, 128, 191, 192, 235}) {
            fractions.add(ONE.shiftLeft(bit));
            fractions.add(ONE.shiftLeft(bit).subtract(ONE));
        }
        for (BigInteger af : fractions) {
            for (BigInteger bf : fractions) {
                Bits a = normal(false, 0, af), b = normal(false, 0, bf);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(b, b.negative())) {
                        assertMultiply(x, y);
                        assertEquals(raw(Binary256Reference.multiply(x.actual(), y.actual())), raw(x.actual().multiply(y.actual())));
                        assertFma(x, y, UNIT);
                        assertFma(x, y, UNIT.negative());
                    }
                }
            }
        }
        for (int length : new int[] {1, 2, 63, 64, 65, 127, 128, 129, 191, 192, 193, 235, 236}) {
            Bits a = bits(ONE.shiftLeft(length).subtract(ONE)), b = normal(false, 262143, FRACTION_MASK);
            Bits c = power(length - 236);
            for (Bits x : List.of(a, a.negative())) {
                for (Bits y : List.of(b, b.negative())) {
                    assertMultiply(x, y);
                    assertMultiply(y, x);
                    assertFma(x, y, c);
                    assertFma(x, y, c.negative());
                    assertFma(y, x, c);
                    assertFma(y, x, c.negative());
                }
            }
        }
    }

    @Test
    void preservesSingleWorkspaceFmaAcrossEveryShiftAndBothSubtractionDirections() {
        for (BigInteger af : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
            for (BigInteger bf : List.of(ONE, FRACTION_MASK)) {
                Bits a = normal(false, 0, af), b = normal(false, 0, bf);
                BigInteger product = ONE.shiftLeft(236).add(af).multiply(ONE.shiftLeft(236).add(bf));
                int productExponent = product.bitLength() - 473;
                for (int gap = -238; gap <= 474; gap++) {
                    Bits c = normal(false, productExponent - gap, FRACTION_MASK);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits z : List.of(c, c.negative())) {
                            assertFma(x, b, z);
                            assertEquals(raw(Binary256Reference.fma(x.actual(), b.actual(), z.actual())),
                                    raw(x.actual().fma(b.actual(), z.actual())));
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesExactCancellationAndAliasedInputsAcrossRepeatedCalls() {
        for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, ONE.shiftLeft(63), ONE.shiftLeft(128), FRACTION_MASK)) {
            for (int exponent : new int[] {-262142, -1, 0, 1, 262142}) {
                Bits a = normal(false, exponent, fraction), c = normal(true, exponent + 1, fraction);
                assertFma(a, power(1), c);
                assertFma(a.negative(), power(1), normal(false, exponent + 1, fraction));
                Binary256 value = a.actual();
                Binary256Baseline baseline = a.baseline();
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertRaw(baseline.add(baseline), value.add(value), "aliased addition");
                    assertRaw(baseline.subtract(baseline), value.subtract(value), "aliased subtraction");
                    assertRaw(baseline.multiply(baseline), value.multiply(value), "aliased multiplication");
                    assertRaw(baseline.fma(baseline, baseline), value.fma(value, value), "aliased FMA");
                    assertEquals(a, raw(value));
                }
            }
        }
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
    void preservesFmaOverflowMidpointWithBothTinyTailDirections() {
        Bits a = exact(ONE.shiftLeft(119).subtract(ONE), 130953);
        Bits b = exact(ONE.shiftLeft(119).add(ONE), 130953);
        for (Bits x : List.of(a, a.negative())) {
            for (Bits y : List.of(b, b.negative())) {
                boolean negative = x.baseline().signBit() ^ y.baseline().signBit();
                for (Bits tail : List.of(MINIMUM, MINIMUM.negative(), ZERO, ZERO.negative())) {
                    assertFma(x, y, tail);
                }
                Bits increasing = negative ? MINIMUM.negative() : MINIMUM;
                Bits decreasing = negative ? MINIMUM : MINIMUM.negative();
                assertEquals(negative ? INFINITY.negative() : INFINITY, raw(x.actual().fma(y.actual(), increasing.actual())));
                assertEquals(negative ? MAXIMUM.negative() : MAXIMUM, raw(x.actual().fma(y.actual(), decreasing.actual())));
            }
        }
    }

    @Test
    void preservesFmaJustAboveAndBelowMidpointsAtFullProductPrecision() {
        BigInteger modulus = ONE.shiftLeft(237), half = ONE.shiftLeft(236);
        for (int delta : new int[] {-1, 1}) {
            BigInteger a = null, b = null;
            for (int odd = 1; odd <= 63; odd += 2) {
                BigInteger candidate = modulus.subtract(BigInteger.valueOf(odd));
                BigInteger partner = half.add(BigInteger.valueOf(delta)).multiply(candidate.modInverse(modulus)).mod(modulus);
                if (partner.bitLength() == 237 && candidate.multiply(partner).bitLength() == 474) {
                    a = candidate;
                    b = partner;
                    break;
                }
            }
            if (a == null) {
                throw new AssertionError("No full-precision midpoint-neighbor product");
            }
            assertEquals(half.add(BigInteger.valueOf(delta)), a.multiply(b).mod(modulus));
            Bits left = exact(a, -236), right = exact(b, -236);
            for (int gap : new int[] {472, 473, 474, 475, 476}) {
                for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
                    Bits addend = normal(false, 1 - gap, fraction);
                    for (Bits x : List.of(left, left.negative())) {
                        for (Bits y : List.of(right, right.negative())) {
                            assertFma(x, y, addend);
                            assertFma(x, y, addend.negative());
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesFmaAtBothDominanceThresholdsAndTheExtremeExponentGaps() {
        Bits multiplier = exact(BigInteger.valueOf(3), -1);
        for (int gap : new int[] {237, 238, 239, 240, 473, 474, 475, 476, 200000}) {
            for (BigInteger fraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
                Bits productSource = normal(false, -gap, fraction);
                for (Bits x : List.of(productSource, productSource.negative())) {
                    for (Bits y : List.of(multiplier, multiplier.negative())) {
                        assertFma(x, y, UNIT);
                        assertFma(x, y, UNIT.negative());
                        assertFma(UNIT, y, x);
                        assertFma(UNIT.negative(), y, x);
                    }
                }
            }
        }
        for (Bits a : List.of(MINIMUM, MINIMUM.negative(), MAXIMUM, MAXIMUM.negative())) {
            for (Bits b : List.of(MINIMUM, MINIMUM.negative(), UNIT, UNIT.negative())) {
                for (Bits c : List.of(MINIMUM, MINIMUM.negative(), MAXIMUM, MAXIMUM.negative())) {
                    assertFma(a, b, c);
                }
            }
        }
    }

    @Test
    void preservesFmaExactAlignmentWithShortAndFullLengthSignificands() {
        List<Bits> subnormals = List.of(MINIMUM, bits(BigInteger.valueOf(3)), bits(ONE.shiftLeft(63)),
                bits(ONE.shiftLeft(128).add(ONE)), bits(FRACTION_MASK));
        for (Bits a : subnormals) {
            Binary256Baseline input = a.baseline();
            int length = 0;
            for (int group = 0; group < 4; group++) {
                long word = input.getSignificandLong(group);
                if (word != 0) {
                    length = (3 - group) * 64 + 64 - Long.numberOfLeadingZeros(word);
                    break;
                }
            }
            int sourceExponent = -262378 + length - 1;
            for (int multiplierExponent : new int[] {0, 1, 236, 237, 474, 475}) {
                Bits b = normal(false, multiplierExponent, FRACTION_MASK);
                int productExponentLower = sourceExponent + multiplierExponent;
                for (int difference : new int[] {-239, -238, 0, 238, 474, 475}) {
                    int addendExponent = productExponentLower - difference;
                    if (addendExponent < -262378 || addendExponent > 262143) {
                        continue;
                    }
                    Bits c = power(addendExponent);
                    assertFma(a, b, c);
                    assertFma(a, b, c.negative());
                    assertFma(a.negative(), b, c);
                    assertFma(a.negative(), b, c.negative());
                }
            }
        }
    }

    @Test
    void preservesFmaBorrowPropagationAcrossTheWholeAlignmentWorkspace() {
        Bits smallA = normal(false, -131308, FRACTION_MASK);
        Bits smallB = normal(false, -131309, FRACTION_MASK);
        Bits largeA = normal(false, 0, FRACTION_MASK);
        Bits largeB = normal(false, 0, FRACTION_MASK);
        Bits distant = normal(false, -473, FRACTION_MASK);
        for (Bits x : List.of(smallA, smallA.negative())) {
            for (Bits y : List.of(smallB, smallB.negative())) {
                assertFma(x, y, MINIMUM);
                assertFma(x, y, MINIMUM.negative());
            }
        }
        for (Bits x : List.of(largeA, largeA.negative())) {
            for (Bits y : List.of(largeB, largeB.negative())) {
                assertFma(x, y, distant);
                assertFma(x, y, distant.negative());
            }
        }
    }

    @Test
    void preservesFmaAcrossTheEightAndTwelveWordWorkspaceBoundary() {
        for (BigInteger leftFraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
            for (BigInteger rightFraction : List.of(BigInteger.ZERO, ONE, FRACTION_MASK)) {
                Bits a = normal(false, 0, leftFraction), b = normal(false, 0, rightFraction);
                for (int gap : new int[] {273, 274, 275, 276}) {
                    Bits c = normal(false, 1 - gap, FRACTION_MASK);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertFma(x, y, c);
                            assertFma(x, y, c.negative());
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesFmaDeepCancellationNormalSubnormalAndZeroResults() {
        for (int exponent : new int[] {-262378, -262143, -262142, -1, 0, 1, 262142, 262143}) {
            Bits a = power(exponent);
            assertFma(a, UNIT, a.negative());
            assertFma(a.negative(), UNIT, a);
        }
        for (int exponent : new int[] {-262142, -262141, -262140, -261905, -261904, -100000, 0, 100000, 262143}) {
            Bits a = exact(ONE.shiftLeft(119).add(ONE), exponent - 119L);
            Bits b = exact(ONE.shiftLeft(119).subtract(ONE), -119);
            Bits c = power(exponent);
            assertFma(a, b, c.negative());
            assertFma(a.negative(), b, c);
        }
        for (Bits addend : List.of(ZERO, ZERO.negative(), MINIMUM, MINIMUM.negative())) {
            assertFma(MINIMUM, power(-1), addend);
            assertFma(MINIMUM.negative(), power(-1), addend);
        }
    }

    @Test
    void preservesRandomFmaAcrossBothFixedWorkspaceThresholds() {
        Random random = new Random(0x256F0AF17EDL);
        int[] differences = {-1000, -240, -239, -238, -1, 0, 1, 237, 238, 473, 474, 475, 476, 1000};
        for (int i = 0; i < 900; i++) {
            int ea = random.nextInt(-120000, 120001), eb = random.nextInt(-120000, 120001);
            Bits a = randomFinite(random, ea), b = randomFinite(random, eb);
            int ec = ea + eb - differences[i % differences.length];
            Bits c = randomFinite(random, ec);
            assertFma(a, b, c);
        }
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

    private static void assertRemainders(Bits a, Bits b, String label) {
        Binary256 actualA = a.actual(), actualB = b.actual();
        assertRaw(a.baseline().remainder(b.baseline()), actualA.remainder(actualB),
                label + " remainder a=" + a + " b=" + b);
        assertRaw(a.baseline().ieeeRemainder(b.baseline()), actualA.ieeeRemainder(actualB),
                label + " IEEE remainder a=" + a + " b=" + b);
        assertEquals(a, raw(actualA), "remainder receiver must remain unchanged");
        assertEquals(b, raw(actualB), "remainder divisor must remain unchanged");
    }

    @Test
    void preservesRemaindersAcrossDirectAndModularReductionBoundaries() {
        int[] differences = {-3, -2, -1, 0, 1, 63, 64, 65, 236, 237, 238, 239, 1000, 524285};
        List<BigInteger> fractions = List.of(BigInteger.ZERO, ONE, FRACTION_MASK);
        for (int ea : new int[] {-262142, 0, 262143}) {
            for (int difference : differences) {
                int eb = ea - difference;
                if (eb < -262142 || eb > 262143) {
                    continue;
                }
                for (BigInteger af : fractions) {
                    for (BigInteger bf : fractions) {
                        Bits a = normal(false, ea, af), b = normal(false, eb, bf);
                        for (Bits x : List.of(a, a.negative())) {
                            for (Bits y : List.of(b, b.negative())) {
                                assertRemainders(x, y, "scale difference=" + difference);
                            }
                        }
                    }
                }
            }
        }
        for (int difference : new int[] {0, 1, 236, 237, 238, 239, 1000, 524285}) {
            for (BigInteger fraction : fractions) {
                Bits a = normal(false, -262142 + difference, fraction);
                for (BigInteger significand : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(3), FRACTION_MASK)) {
                    Bits b = bits(significand);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertRemainders(x, y, "subnormal divisor scale difference=" + difference);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesSquareRootRoundingNearScaledPerfectSquares() {
        for (int bit : new int[] {0, 1, 31, 32, 63, 64, 65, 117, 118}) {
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger root = ONE.shiftLeft(bit).add(BigInteger.valueOf(delta));
                if (root.signum() == 0) {
                    continue;
                }
                BigInteger square = root.multiply(root);
                for (int exponent : new int[] {-262142, -262141, -1022, -1, 0, 1, 1023, 262142, 262143}) {
                    Bits center = exact(square, (long) exponent - square.bitLength() + 1);
                    BigInteger encoding = new BigInteger(center.toString(), 16);
                    for (int neighbor = -1; neighbor <= 1; neighbor++) {
                        Bits input = bits(encoding.add(BigInteger.valueOf(neighbor)));
                        Binary256 actual = input.actual();
                        assertRaw(input.baseline().sqrt(), actual.sqrt(), "sqrt input=" + input);
                        assertEquals(input, raw(actual), "square root receiver must remain unchanged");
                    }
                }
            }
        }
    }

    @Test
    void preservesRandomDivisionAndRemaindersAcrossExponentAndWordBoundaries() {
        Random random = new Random(0x256D1A1DE7L);
        int[] edges = {-262143, -262142, -262141, -1, 0, 1, 236, 237, 238, 262142, 262143};
        for (int i = 0; i < 650; i++) {
            int ea = i < 200 ? edges[random.nextInt(edges.length)] : random.nextInt(-262142, 262144);
            int eb = i < 200 ? edges[random.nextInt(edges.length)] : Math.clamp(ea + random.nextInt(-300, 301), -262142, 262143);
            Bits a = randomFinite(random, ea), b = randomFinite(random, eb);
            assertRaw(a.baseline().divide(b.baseline()), a.actual().divide(b.actual()), "division a=" + a + " b=" + b);
            assertRemainders(a, b, "random division operands");
        }
    }

    private static void assertDivision(Bits a, Bits b) {
        Binary256 x = a.actual(), y = b.actual();
        Binary256 actual = x.divide(y);
        assertRaw(a.baseline().divide(b.baseline()), actual, "grouped divide a=" + a + " b=" + b);
        assertEquals(raw(Binary256Reference.divide(x, y)), raw(actual));
        assertEquals(a, raw(x));
        assertEquals(b, raw(y));
    }

    @Test
    void preservesGroupedDivisionAcrossAllSubnormalSignificandWidths() {
        for (int length = 1; length <= 237; length++) {
            for (BigInteger magnitude : List.of(ONE.shiftLeft(length - 1), ONE.shiftLeft(length).subtract(ONE))) {
                Bits divisor = exact(magnitude, -262378);
                for (Bits a : List.of(MINIMUM, bits(FRACTION_MASK), MINIMUM_NORMAL)) {
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(divisor, divisor.negative())) {
                            assertDivision(x, y);
                            assertRemainders(x, y, "divisor significand length=" + length);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesGroupedDivisionAtUnderflowTiesAndTheMinimumNormalCarry() {
        List<BigInteger> lower = new ArrayList<>(List.of(BigInteger.ZERO, ONE, BigInteger.TWO, BigInteger.valueOf(3)));
        for (int bit : new int[] {63, 64, 127, 128, 191, 192, 235, 236}) {
            lower.add(ONE.shiftLeft(bit).subtract(ONE));
        }
        Bits divisor = power(1);
        for (BigInteger retained : lower) {
            BigInteger midpoint = retained.shiftLeft(1).add(ONE);
            for (int delta : new int[] {-1, 0, 1}) {
                Bits a = exact(midpoint.add(BigInteger.valueOf(delta)), -262378);
                for (Bits x : List.of(a, a.negative())) {
                    for (Bits y : List.of(divisor, divisor.negative())) {
                        assertDivision(x, y);
                    }
                }
            }
        }
    }

    @Test
    void preservesGroupedDivisionAtNormalAndOverflowExponentEdges() {
        int[][] exponents = {{-262142, 0}, {-262142, 1}, {-262142, 237}, {0, 0}, {0, -1},
                {262142, -1}, {262143, 0}, {262143, -1}, {262143, -2}};
        for (int[] pair : exponents) {
            for (BigInteger af : List.of(BigInteger.ZERO, ONE, FRACTION_MASK.subtract(ONE), FRACTION_MASK)) {
                for (BigInteger bf : List.of(BigInteger.ZERO, ONE, FRACTION_MASK.subtract(ONE), FRACTION_MASK)) {
                    Bits a = normal(false, pair[0], af), b = normal(false, pair[1], bf);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(b, b.negative())) {
                            assertDivision(x, y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesLargeExponentRemaindersWithGroupedOddAndEvenModuli() {
        for (int length : new int[] {31, 32, 33, 63, 64, 65, 127, 128, 129, 191, 192, 193, 235, 236}) {
            Bits divisor = bits(ONE.shiftLeft(length).subtract(ONE));
            for (int exponent : new int[] {-262142, -262141, -1, 0, 262143}) {
                for (BigInteger fraction : List.of(ONE, FRACTION_MASK)) {
                    Bits a = normal(false, exponent, fraction);
                    for (Bits x : List.of(a, a.negative())) {
                        for (Bits y : List.of(divisor, divisor.negative())) {
                            assertRemainders(x, y, "modular grouped divisor length=" + length);
                        }
                    }
                }
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

    @Test
    void preservesEveryPrimitiveMagnitudeLengthAndAdjacentBitPatterns() {
        for (int bit = 0; bit < 64; bit++) {
            long power = 1L << bit;
            for (long value : new long[] {power - 1, power, power + 1, -power - 1, -power, -power + 1}) {
                assertRaw(Binary256Baseline.of(value), Binary256.of(value), "long magnitude boundary bit=" + bit);
            }
        }
        for (int bit = 0; bit < 23; bit++) {
            int power = 1 << bit;
            for (int encoding : new int[] {power - 1, power, power + 1}) {
                for (int sign : new int[] {0, Integer.MIN_VALUE}) {
                    float value = Float.intBitsToFloat(encoding | sign);
                    assertRaw(Binary256Baseline.of(value), Binary256.of(value), "float subnormal bit=" + bit);
                }
            }
        }
        for (int bit = 0; bit < 52; bit++) {
            long power = 1L << bit;
            for (long encoding : new long[] {power - 1, power, power + 1}) {
                for (long sign : new long[] {0, Long.MIN_VALUE}) {
                    double value = Double.longBitsToDouble(encoding | sign);
                    assertRaw(Binary256Baseline.of(value), Binary256.of(value), "double subnormal bit=" + bit);
                }
            }
        }
    }

    @Test
    void preservesFmaAtBothSidesOfTheGuaranteedOverflowExponent() {
        List<BigInteger> fractions = List.of(BigInteger.ZERO, ONE.shiftLeft(234), ONE.shiftLeft(235), FRACTION_MASK);
        for (int leftExponent : new int[] {262142, 262143}) {
            for (int rightExponent : new int[] {0, 1, 2, 3}) {
                for (BigInteger leftFraction : fractions) {
                    for (BigInteger rightFraction : fractions) {
                        Bits a = normal(false, leftExponent, leftFraction);
                        Bits b = normal(false, rightExponent, rightFraction);
                        for (Bits x : List.of(a, a.negative())) {
                            for (Bits y : List.of(b, b.negative())) {
                                assertFma(x, y, MAXIMUM);
                                assertFma(x, y, MAXIMUM.negative());
                            }
                        }
                    }
                }
            }
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
    void preservesRandomPrimitiveRoundingWithinTargetExponentRanges() {
        Random random = new Random(0x256901A17AEL);
        for (int i = 0; i < 4000; i++) {
            int exponent = switch (i % 3) {
                case 0 -> random.nextInt(-151, 130);
                case 1 -> random.nextInt(-1076, 1026);
                default -> random.nextInt(-2, 66);
            };
            assertPrimitives(randomFinite(random, exponent));
        }
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
    void preservesForeignMidpointTailsAcrossSourceGroupBoundaries() {
        for (int precision : new int[] {240, 241, 255, 256, 257, 319, 320, 321, 511, 512, 513, 800, 801, 4096}) {
            Binary.Format format = new Binary.Format(precision, -400000, 400000);
            BigInteger top = ONE.shiftLeft(precision - 1);
            BigInteger guard = ONE.shiftLeft(precision - 238);
            List<BigInteger> prefixes = List.of(top, top.add(guard), top.add(guard).setBit(precision - 237),
                    top.add(guard).setBit(precision - 239));
            int dropped = precision - 239;
            for (BigInteger prefix : prefixes) {
                for (int tail : new int[] {-1, 0, Math.min(63, dropped - 1), Math.min(64, dropped - 1), dropped - 1}) {
                    BigInteger magnitude = tail < 0 ? prefix : prefix.setBit(tail);
                    for (int exponent : new int[] {-262379, -262378, 0, 262143}) {
                        for (boolean negative : new boolean[] {false, true}) {
                            assertForeign(binary(format, negative, magnitude, (long) exponent - precision + 1),
                                    "source tail precision=" + precision + " tail=" + tail + " exponent=" + exponent + " negative=" + negative);
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesForeignNormalAndSubnormalValuesAtEveryGroupAlignment() {
        Random random = new Random(0x256A116A1E17L);
        for (int precision = 2; precision <= 1025; precision++) {
            Binary.Format format = new Binary.Format(precision, -262142, 262143);
            BigInteger normal = new BigInteger(precision, random).setBit(precision - 1);
            BigInteger subnormal = new BigInteger(random.nextInt(precision), random);
            int exponent = switch (precision % 4) {
                case 0 -> -262142;
                case 1 -> -1;
                case 2 -> 0;
                default -> 262143;
            };
            for (boolean negative : new boolean[] {false, true}) {
                assertForeign(binary(format, negative, normal, (long) exponent - precision + 1),
                        "normal group alignment precision=" + precision + " negative=" + negative);
                assertForeign(binary(format, negative, subnormal, -262141L - precision),
                        "subnormal group alignment precision=" + precision + " negative=" + negative);
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
    void preservesInt256NegativeMagnitudeCarriesAcrossAllFourWords() {
        for (int bit : new int[] {0, 1, 19, 44, 45, 63, 64, 65, 127, 128, 191, 192, 236, 237, 238, 254, 255}) {
            for (int delta = -1; delta <= 1; delta++) {
                BigInteger magnitude = ONE.shiftLeft(bit).add(BigInteger.valueOf(delta));
                for (BigInteger value : List.of(magnitude, magnitude.negate())) {
                    Int256 source = Int256.of(value.shiftRight(192).longValue(), value.shiftRight(128).longValue(),
                            value.shiftRight(64).longValue(), value.longValue());
                    assertInteger(source, "Int256 carry boundary bit=" + bit + " delta=" + delta);
                }
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
