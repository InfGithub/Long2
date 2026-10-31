package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

class UnsignedWordsTest {
    private record ScaledRoot(long[] workspace) {
        long[] value() {
            return Arrays.copyOfRange(workspace, 0, 4);
        }

        long[] remainder() {
            return Arrays.copyOfRange(workspace, 4, 8);
        }
    }

    static BigInteger integer(long[] words) {
        BigInteger result = BigInteger.ZERO;
        for (int i = words.length - 1; i >= 0; i--) {
            BigInteger word = BigInteger.valueOf(words[i] & Long.MAX_VALUE);
            if (words[i] < 0) {
                word = word.setBit(63);
            }
            result = result.shiftLeft(64).or(word);
        }
        return result;
    }

    static long[] words(BigInteger value) {
        long[] result = new long[(value.bitLength() + 63) / 64];
        for (int i = 0; i < result.length; i++) {
            result[i] = value.shiftRight(i * 64).longValue();
        }
        return result;
    }

    @Test
    void checksArithmeticAndInputOwnership() {
        Random random = new Random(0x256A817L);
        for (int i = 0; i < 400; i++) {
            BigInteger a = i < 10 ? BigInteger.ONE.shiftLeft(i * 64).subtract(BigInteger.ONE)
                    : new BigInteger(random.nextInt(640), random);
            BigInteger b = new BigInteger(random.nextInt(640), random);
            long[] aw = words(a), bw = words(b);
            long[] ac = aw.clone(), bc = bw.clone();
            assertEquals(a.multiply(b), integer(UnsignedWords.multiply(aw, bw)));
            assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(UnsignedWords.compare(aw, bw)));
            if (a.compareTo(b) >= 0) {
                assertEquals(a.subtract(b), integer(UnsignedWords.subtract(aw, bw)));
            }
            if (b.signum() != 0) {
                UnsignedWords.Division result = UnsignedWords.divide(aw, bw);
                assertEquals(a.divide(b), integer(result.quotient()));
                assertEquals(a.remainder(b), integer(result.remainder()));
            }
            assertArrayEquals(ac, aw);
            assertArrayEquals(bc, bw);
        }
        assertThrows(ArithmeticException.class, () -> UnsignedWords.divide(new long[] {1}, new long[0]));
    }

    @Test
    void checksShiftsAndDiscardedBitsAtWordBoundaries() {
        Random random = new Random(0x256517L);
        int[] distances = {0, 1, 31, 32, 63, 64, 65, 127, 128, 237, 256, 511, 512, 513, 1024};
        for (int i = 0; i < 50; i++) {
            BigInteger value = new BigInteger(512, random);
            long[] words = words(value);
            for (int distance : distances) {
                assertEquals(value.shiftLeft(distance), integer(UnsignedWords.left(words, distance)));
                assertEquals(value.and(BigInteger.ONE.shiftLeft(distance).subtract(BigInteger.ONE)).signum() != 0,
                        UnsignedWords.anyLow(words, distance));
            }
            assertEquals(value.signum() != 0, UnsignedWords.anyLow(words, Long.MAX_VALUE));
            assertEquals(value.bitLength(), UnsignedWords.bitLength(Arrays.copyOf(words, words.length + 2)));
        }
    }

    @Test
    void checksUnsignedLongSquareRootsAtEveryWidthAndAroundExactSquares() {
        Random random = new Random(0x256500764L);
        for (int length = 0; length <= 64; length++) {
            BigInteger high = length == 0 ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(length - 1);
            for (BigInteger magnitude : new BigInteger[] {high, BigInteger.ONE.shiftLeft(length).subtract(BigInteger.ONE),
                    new BigInteger(length, random)}) {
                assertEquals(magnitude.sqrt().longValueExact(), UnsignedWords.sqrtUnsignedLong(magnitude.longValue()));
            }
        }
        for (long value : new long[] {0, 1, 2, 31, 65535, 65536, 0x7fff_ffffL, 0x8000_0000L,
                3_000_000_000L, 0xffff_ffffL}) {
            BigInteger square = BigInteger.valueOf(value).pow(2);
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger magnitude = square.add(BigInteger.valueOf(delta));
                if (magnitude.signum() >= 0) {
                    assertEquals(magnitude.sqrt().longValueExact(), UnsignedWords.sqrtUnsignedLong(magnitude.longValue()));
                }
            }
        }
    }

    private static ScaledRoot assertScaledRoot(BigInteger magnitude, int shift) {
        long[] workspace = new long[8];
        Arrays.fill(workspace, -1L);
        return assertScaledRoot(magnitude, shift, workspace);
    }

    private static ScaledRoot assertScaledRoot(BigInteger magnitude, int shift, long[] workspace) {
        BigInteger input = magnitude.shiftLeft(shift);
        assertTrue(magnitude.signum() > 0 && magnitude.bitLength() <= 237);
        assertTrue(input.bitLength() == 473 || input.bitLength() == 474);
        int comparison = UnsignedWords.sqrtScaled(magnitude.longValue(), magnitude.shiftRight(64).longValue(),
                magnitude.shiftRight(128).longValue(), magnitude.shiftRight(192).longValue(), shift, workspace);
        ScaledRoot actual = new ScaledRoot(workspace);
        BigInteger[] expected = input.sqrtAndRemainder();
        BigInteger root = integer(actual.value()), remainder = integer(actual.remainder());
        assertEquals(expected[0], root, "scaled root input=" + input.toString(16));
        assertEquals(expected[1], remainder, "scaled residual input=" + input.toString(16));
        assertEquals(expected[1].compareTo(expected[0]), comparison, "scaled residual comparison input=" + input.toString(16));
        assertEquals(input, root.multiply(root).add(remainder));
        assertTrue(remainder.signum() >= 0 && remainder.compareTo(root.shiftLeft(1).add(BigInteger.ONE)) < 0);
        return actual;
    }

    private static void assertUnsigned128Root(BigInteger input) {
        assertTrue(input.bitLength() == 127 || input.bitLength() == 128);
        assertEquals(input.sqrt().longValue(), UnsignedWords.sqrtUnsigned128(input.longValue(), input.shiftRight(64).longValue()),
                "unsigned 128-bit root input=" + input.toString(16));
    }

    @Test
    void checksUnsigned128RootsAroundSquaresCarriesAndCandidateCorrections() {
        BigInteger limit = BigInteger.ONE.shiftLeft(64);
        for (BigInteger root : new BigInteger[] {BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE),
                new BigInteger("12345678901234567891"), limit.subtract(BigInteger.TWO), limit.subtract(BigInteger.ONE)}) {
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger input = root.pow(2).add(BigInteger.valueOf(delta));
                if (input.bitLength() >= 127) {
                    assertUnsigned128Root(input);
                }
            }
        }
        BigInteger carryBoundary = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE).pow(2).shiftLeft(64);
        for (int delta : new int[] {-1, 0, 1}) {
            assertUnsigned128Root(carryBoundary.add(BigInteger.valueOf(delta)));
        }
        assertUnsigned128Root(BigInteger.valueOf(31).shiftLeft(122));
        assertUnsigned128Root(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE));
        Random random = new Random(0x2561285405L);
        for (int i = 0; i < 10000; i++) {
            assertUnsigned128Root(new BigInteger(128, random).setBit(126));
        }
    }

    @Test
    void checksScaledNewtonExact128PrefixesAndEveryNonzeroTailBit() {
        for (BigInteger root : new BigInteger[] {BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE),
                new BigInteger("12345678901234567891"), BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)}) {
            BigInteger prefix = root.pow(2);
            int tailBits = 237 - prefix.bitLength();
            for (int bits : new int[] {0, 1, 31, 32, 63, 64, 65, 109, 110}) {
                if (bits <= tailBits) {
                    ScaledRoot exact = assertScaledRoot(prefix.shiftLeft(bits), 346 - bits);
                    assertEquals(root.shiftLeft(173), integer(exact.value()));
                    assertArrayEquals(new long[4], exact.remainder());
                }
            }
            BigInteger magnitude = prefix.shiftLeft(tailBits);
            int shift = 346 - tailBits;
            ScaledRoot earlier = assertScaledRoot(magnitude, shift);
            long[] savedRoot = earlier.value().clone(), savedResidual = earlier.remainder().clone();
            for (int bit = 0; bit < tailBits; bit++) {
                assertScaledRoot(magnitude.setBit(bit), shift);
            }
            assertScaledRoot(magnitude.add(BigInteger.ONE.shiftLeft(tailBits).subtract(BigInteger.ONE)), shift);
            assertArrayEquals(savedRoot, earlier.value());
            assertArrayEquals(savedResidual, earlier.remainder());
        }
    }

    @Test
    void checksScaledNewton64BitSeedsAtFirstAdjacentRootsAndCarries() {
        for (BigInteger seed : new BigInteger[] {BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE),
                new BigInteger("10000000000000000000"), new BigInteger("12000000000000000000"), BigInteger.ONE.shiftLeft(64)}) {
            BigInteger estimate = seed.shiftLeft(173);
            int shift = estimate.pow(2).bitLength() > 473 ? 237 : 236;
            BigInteger magnitude = estimate.pow(2).shiftRight(shift).subtract(BigInteger.ONE);
            BigInteger input = magnitude.shiftLeft(shift);
            assertEquals(seed, input.shiftRight(346).sqrt().add(BigInteger.ONE));
            BigInteger[] division = input.divideAndRemainder(estimate);
            assertEquals(estimate.subtract(BigInteger.ONE), division[0]);
            ScaledRoot earlier = assertScaledRoot(magnitude, shift);
            assertEquals(division[0], integer(earlier.value()));
            assertEquals(division[1].add(division[0]), integer(earlier.remainder()));
            long[] savedRoot = earlier.value().clone(), savedResidual = earlier.remainder().clone();
            assertScaledRoot(BigInteger.ONE.shiftLeft(236).add(BigInteger.ONE), 236);
            assertArrayEquals(savedRoot, earlier.value());
            assertArrayEquals(savedResidual, earlier.remainder());
        }
    }

    @Test
    void checksScaledNewtonTwoDigitTrialCorrectionsAndRetainedSingleDigitSeeds() {
        // 197 的前缀触发两次估商修正，33 位全一幅值触发钳制；31 位全一幅值保留单组除法。
        long[] magnitudes = {1, 31, 197, 0x7fff_ffffL, 0xffff_ffffL, 0x1_ffff_ffffL, -1L};
        int[] shifts = {473, 468, 465, 443, 442, 441, 410};
        for (int i = 0; i < magnitudes.length; i++) {
            BigInteger magnitude = integer(new long[] {magnitudes[i]});
            assertScaledRoot(magnitude, shifts[i]);
        }
    }

    @Test
    void checksScaledNewtonRootsForEveryMagnitudeWidthAndExponentParity() {
        Random random = new Random(0x2565CA1EDL);
        for (int length = 1; length <= 237; length++) {
            BigInteger high = BigInteger.ONE.shiftLeft(length - 1);
            for (BigInteger magnitude : new BigInteger[] {high, high.shiftLeft(1).subtract(BigInteger.ONE),
                    new BigInteger(length, random).setBit(length - 1)}) {
                for (int parity = 0; parity <= 1; parity++) {
                    assertScaledRoot(magnitude, 473 - length + parity);
                }
            }
        }
    }

    @Test
    void checksScaledNewtonSeedsAroundUnsignedPrefixSquaresAndSeedCarries() {
        long[] roots = {1L << 31, (1L << 31) + 1, 3_000_000_000L, 3_100_000_000L, 0xffff_ffffL};
        for (long root : roots) {
            BigInteger square = BigInteger.valueOf(root).pow(2);
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger prefix = square.add(BigInteger.valueOf(delta));
                int prefixBits = prefix.bitLength();
                if (prefixBits != 63 && prefixBits != 64) {
                    continue;
                }
                int tailBits = 237 - prefixBits;
                for (BigInteger tail : new BigInteger[] {BigInteger.ZERO, BigInteger.ONE,
                        BigInteger.ONE.shiftLeft(tailBits).subtract(BigInteger.ONE)}) {
                    BigInteger magnitude = prefix.shiftLeft(tailBits).add(tail);
                    assertScaledRoot(magnitude, prefixBits + 173);
                }
            }
        }
    }

    @Test
    void checksScaledNewtonFirstDivisionAdjacentRootsAndOwnedResiduals() {
        for (long seed : new long[] {0x8000_0001L, 2_500_000_000L, 3_000_000_000L, 0x1_0000_0000L}) {
            BigInteger estimate = BigInteger.valueOf(seed).shiftLeft(205);
            int shift = seed == 0x1_0000_0000L ? 237 : 236;
            BigInteger input = estimate.pow(2).subtract(BigInteger.ONE.shiftLeft(shift));
            BigInteger[] division = input.divideAndRemainder(estimate);
            BigInteger expectedRoot = estimate.subtract(BigInteger.ONE);
            assertEquals(expectedRoot, division[0]);
            ScaledRoot result = assertScaledRoot(input.shiftRight(shift), shift);
            assertEquals(expectedRoot, integer(result.value()));
            assertEquals(division[1].add(expectedRoot), integer(result.remainder()));
            assertEquals(seed == 0x1_0000_0000L ? 0 : 1,
                    integer(result.remainder()).compareTo(expectedRoot));
            long[] savedRoot = result.value().clone(), savedResidual = result.remainder().clone();
            assertScaledRoot(BigInteger.ONE.shiftLeft(236).add(BigInteger.TWO), 236);
            assertArrayEquals(savedRoot, result.value());
            assertArrayEquals(savedResidual, result.remainder());
        }
    }

    @Test
    void checksScaledNewtonRootsAtThe64BitPrefixCarryBoundary() {
        BigInteger boundary = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE).pow(2);
        for (int width : new int[] {64, 65, 96, 127, 128, 129, 173, 192, 236, 237}) {
            int tailBits = width - 64;
            BigInteger tailMask = BigInteger.ONE.shiftLeft(tailBits).subtract(BigInteger.ONE);
            for (BigInteger prefix : new BigInteger[] {boundary.subtract(BigInteger.ONE), boundary,
                    boundary.add(BigInteger.ONE), BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)}) {
                for (BigInteger tail : new BigInteger[] {BigInteger.ZERO, tailMask}) {
                    BigInteger magnitude = prefix.shiftLeft(tailBits).or(tail);
                    BigInteger input = magnitude.shiftLeft(474 - width);
                    BigInteger seed = input.shiftRight(410).sqrt().add(BigInteger.ONE);
                    assertEquals(prefix.compareTo(boundary) < 0 ? 32 : 33, seed.bitLength());
                    if (seed.bitLength() == 33) {
                        assertEquals(BigInteger.ZERO, input.remainder(seed.shiftLeft(205)));
                    }
                    assertScaledRoot(magnitude, 474 - width);
                }
            }
        }
    }

    @Test
    void checksScaledNewtonShortDivisionWithUnsignedPairsAndNonzeroResiduals() {
        Random random = new Random(0x25654047L);
        boolean unsignedPair = false, highResidual = false;
        for (int width = 1; width <= 237; width++) {
            for (int parity = 0; parity <= 1; parity++) {
                BigInteger magnitude = new BigInteger(width, random).setBit(width - 1);
                int shift = 473 - width + parity;
                BigInteger input = magnitude.shiftLeft(shift);
                BigInteger seed = input.shiftRight(346).sqrt().add(BigInteger.ONE);
                if (seed.bitLength() == 64) {
                    BigInteger reduced = input.shiftRight(173);
                    highResidual |= reduced.remainder(seed).bitLength() == 64;
                    for (int digit = 0; digit < 8; digit++) {
                        unsignedPair |= reduced.shiftRight((digit + 1) * 32).remainder(seed).bitLength() == 64;
                    }
                }
                assertScaledRoot(magnitude, shift);
            }
        }
        assertTrue(unsignedPair && highResidual);
    }

    @Test
    void checksScaledNewtonResidualsAtExactSquaresRoundingEdgesAndAdjacentCycles() {
        boolean exact = false, below = false, boundary = false, above = false, cycle = false;
        for (BigInteger input : Binary256CompatibilityFixtures.squareRootRoundingBoundaries()) {
            int shift = input.bitLength() - 237;
            assertTrue(input.getLowestSetBit() >= shift);
            ScaledRoot result = assertScaledRoot(input.shiftRight(shift), shift);
            BigInteger root = integer(result.value()), residual = integer(result.remainder());
            exact |= residual.signum() == 0;
            below |= residual.equals(root.subtract(BigInteger.TWO));
            boundary |= residual.equals(root);
            above |= residual.equals(root.add(BigInteger.TWO));
            cycle |= residual.equals(root.shiftLeft(1));
        }
        // 缩放后的输入为偶数，R-S 也为偶数，S±2 是该输入域中最邻近的两侧。
        assertTrue(exact && below && boundary && above && cycle);
    }

    @Test
    void checksRandomScaledNewtonRootsAndEarlierResultOwnership() {
        BigInteger high = BigInteger.ONE.shiftLeft(236);
        ScaledRoot earlier = assertScaledRoot(high.add(BigInteger.TWO), 236);
        long[] savedRoot = earlier.value().clone(), savedResidual = earlier.remainder().clone();
        Random random = new Random(0x2565A7E7L);
        for (int i = 0; i < 16000; i++) {
            int length = i % 4 == 0 ? i % 237 + 1 : 237;
            BigInteger magnitude = new BigInteger(length, random).setBit(length - 1);
            assertScaledRoot(magnitude, 473 - length + (i & 1));
        }
        assertArrayEquals(savedRoot, earlier.value());
        assertArrayEquals(savedResidual, earlier.remainder());
    }

    @Test
    void checksScaledNewtonShortRemaindersAndZeroWithoutTrimming() {
        String[] magnitudes = {
                "13331ad4ac5e3c4a8d92fb5eb5b9a38d4fd25edef3dd02de6eca0bf76248",
                "1791641e19c01c6463d8486d457be5237c97072cb93ccc188b55ac7fe543",
                "12ed5505e18cd1553d93205a31fc2cda2a1f0140961a28e00c4873eeadcb",
                "11756d75569a298566fe9470f95faf5ca713c03b0dea1c1e775e49edce47"};
        long[] workspace = new long[8];
        Arrays.fill(workspace, -1L);
        for (int i = 0; i < magnitudes.length; i++) {
            BigInteger expected = BigInteger.valueOf(7);
            if (i != 0) {
                expected = expected.setBit(i * 64);
            }
            ScaledRoot result = assertScaledRoot(new BigInteger(magnitudes[i], 16), 236, workspace);
            assertArrayEquals(Arrays.copyOf(words(expected), 4), result.remainder());
        }
        // 65 位奇数根超出精确前缀捷径的范围，Newton 终止仍可能得到零余数。
        BigInteger wideSquare = BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE).pow(2).shiftLeft(108);
        BigInteger prefix = wideSquare.shiftLeft(236).shiftRight(346);
        assertTrue(!prefix.sqrt().pow(2).equals(prefix));
        ScaledRoot zero = assertScaledRoot(wideSquare, 236, workspace);
        assertArrayEquals(new long[4], zero.remainder());
    }

    @Test
    void checksScaledNewtonWorkspaceReuseAcrossAllReturnPaths() {
        BigInteger unit = BigInteger.ONE.shiftLeft(236);
        ScaledRoot earlier = assertScaledRoot(unit.add(BigInteger.ONE), 236);
        long[] saved = earlier.workspace().clone();
        long[] workspace = new long[8];
        Arrays.fill(workspace, 0xa5a5_a5a5_a5a5_a5a5L);
        BigInteger wideSquare = BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE).pow(2).shiftLeft(108);
        BigInteger[] magnitudes = {BigInteger.valueOf(197), unit.add(BigInteger.TWO), BigInteger.ONE,
                BigInteger.ONE.shiftLeft(237).subtract(BigInteger.ONE), wideSquare, unit.add(BigInteger.ONE), BigInteger.ONE};
        int[] shifts = {465, 236, 472, 237, 236, 236, 472};
        for (int i = 0; i < magnitudes.length; i++) {
            assertScaledRoot(magnitudes[i], shifts[i], workspace);
            assertArrayEquals(saved, earlier.workspace());
        }
        assertArrayEquals(new long[] {0, 0, 0, 1L << 44, 0, 0, 0, 0}, workspace);
    }

    @Test
    void checksLargeModularExponents() {
        BigInteger modulus = BigInteger.ONE.shiftLeft(237).subtract(BigInteger.valueOf(42));
        for (long exponent : new long[] {0, 1, 63, 64, 237, 524285}) {
            assertEquals(BigInteger.TWO.modPow(BigInteger.valueOf(exponent), modulus),
                    integer(UnsignedWords.powerOfTwoMod(exponent, words(modulus))));
        }
    }

    @Test
    void checksLowBitsAtEveryWordBoundaryWithoutMutatingInputs() {
        Random random = new Random(0x25610B175L);
        for (int i = 0; i < 100; i++) {
            BigInteger value = new BigInteger(512, random);
            long[] input = Arrays.copyOf(words(value), 11), original = input.clone();
            for (int count : new int[] {0, 1, 63, 64, 65, 127, 128, 191, 192, 236, 237, 238, 511, 512, 513, 1024}) {
                BigInteger mask = BigInteger.ONE.shiftLeft(count).subtract(BigInteger.ONE);
                assertEquals(value.and(mask), integer(UnsignedWords.lowBits(input, count)));
                assertArrayEquals(original, input);
            }
        }
    }

    private static void assertDivision(BigInteger numerator, BigInteger denominator, int padding) {
        long[] a = words(numerator), b = words(denominator);
        a = Arrays.copyOf(a, a.length + padding);
        b = Arrays.copyOf(b, b.length + padding);
        long[] originalA = a.clone(), originalB = b.clone();
        UnsignedWords.Division result = UnsignedWords.divide(a, b);
        assertEquals(numerator.divide(denominator), integer(result.quotient()));
        assertEquals(numerator.remainder(denominator), integer(result.remainder()));
        assertEquals(numerator.remainder(denominator), integer(UnsignedWords.remainder(a, b)));
        assertEquals(numerator, integer(result.quotient()).multiply(denominator).add(integer(result.remainder())));
        assertTrue(integer(result.remainder()).signum() >= 0 && integer(result.remainder()).compareTo(denominator) < 0);
        assertArrayEquals(originalA, a, "division must leave the numerator unchanged");
        assertArrayEquals(originalB, b, "division must leave the denominator unchanged");
    }

    @Test
    void checksDivisionAndPureRemainderAtWordBoundariesWithoutMutatingInputs() {
        for (int bit : new int[] {1, 2, 31, 32, 63, 64, 65, 127, 128, 129, 191, 192, 193,
                236, 237, 238, 255, 256, 257, 473, 474, 475, 511, 512, 513, 1024}) {
            BigInteger power = BigInteger.ONE.shiftLeft(bit);
            for (BigInteger a : new BigInteger[] {BigInteger.ZERO, BigInteger.ONE, power.subtract(BigInteger.ONE), power,
                    power.add(BigInteger.ONE), power.shiftLeft(1).subtract(BigInteger.ONE)}) {
                for (BigInteger b : new BigInteger[] {BigInteger.ONE, power.subtract(BigInteger.ONE), power, power.add(BigInteger.ONE)}) {
                    assertDivision(a, b, bit % 3);
                }
            }
        }
        long[] shared = {-1L, 0, -1L, 0};
        long[] original = shared.clone();
        UnsignedWords.Division result = UnsignedWords.divide(shared, shared);
        assertEquals(BigInteger.ONE, integer(result.quotient()));
        assertEquals(BigInteger.ZERO, integer(result.remainder()));
        assertEquals(BigInteger.ZERO, integer(UnsignedWords.remainder(shared, shared)));
        assertArrayEquals(original, shared, "aliased inputs must remain unchanged");
        assertThrows(ArithmeticException.class, () -> UnsignedWords.divide(new long[] {1}, new long[] {0, 0}));
        assertThrows(ArithmeticException.class, () -> UnsignedWords.remainder(new long[] {1}, new long[] {0, 0}));
    }

    @Test
    void comparesRandomPureRemaindersToBigInteger() {
        Random random = new Random(0x256D1A1DEAL);
        for (int i = 0; i < 700; i++) {
            BigInteger a = new BigInteger(random.nextInt(1600), random);
            BigInteger b = new BigInteger(random.nextInt(1, 800), random).setBit(0);
            assertDivision(a, b, i % 3);
        }
    }

    @Test
    void checksGroupedDivisionForEveryNormalizationShiftAndQuotientWidth() {
        BigInteger base = BigInteger.ONE.shiftLeft(32);
        BigInteger[] quotients = {BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO, base.subtract(BigInteger.ONE),
                base, base.add(BigInteger.ONE), BigInteger.ONE.shiftLeft(237).subtract(BigInteger.ONE)};
        Random random = new Random(0x256D16175L);
        for (int groups = 2; groups <= 8; groups++) {
            for (int shift = 0; shift < 32; shift++) {
                int bits = groups * 32 - shift;
                BigInteger divisor = new BigInteger(bits, random).setBit(bits - 1);
                for (BigInteger quotient : quotients) {
                    for (BigInteger residual : new BigInteger[] {BigInteger.ZERO, BigInteger.ONE, divisor.subtract(BigInteger.ONE)}) {
                        assertDivision(divisor.multiply(quotient).add(residual), divisor, (groups + shift) % 3);
                    }
                }
            }
        }
    }

    @Test
    void checksTrialQuotientCorrectionsAndFullMultiplySubtractAddBack() {
        BigInteger base = BigInteger.ONE.shiftLeft(32), half = base.shiftRight(1);
        BigInteger divisor = half.multiply(base).add(base.subtract(BigInteger.ONE));
        // 最高两组的估商为 base-2，第二组比较需要下调两次；加入较大次高组的输入只需下调一次。
        BigInteger twice = half.subtract(BigInteger.ONE).multiply(base.pow(2));
        BigInteger once = twice.add(base.subtract(BigInteger.ONE).multiply(base));
        assertEquals(base.subtract(BigInteger.valueOf(4)), twice.divide(divisor));
        assertEquals(base.subtract(BigInteger.TWO), once.divide(divisor));
        for (int padding : new int[] {0, 1, 3}) {
            assertDivision(twice, divisor, padding);
            assertDivision(once, divisor, padding);
        }
        for (int groups : new int[] {3, 4, 8}) {
            for (int shift = 0; shift < 32; shift++) {
                BigInteger d = BigInteger.ONE.shiftLeft(groups * 32 - 1 - shift).add(BigInteger.ONE);
                // 高两组允许试商二，最低非零组使精确商只能为一，必须通过完整乘减后的回加修正。
                assertDivision(d.shiftLeft(1).subtract(BigInteger.ONE), d, shift % 3);
                // 商接近一个完整分组的上界，覆盖最高被除数组等于最高除数组的估商情况。
                assertDivision(d.multiply(base).subtract(BigInteger.ONE), d, shift % 3);
            }
        }
        BigInteger allOnes = base.pow(3).subtract(BigInteger.ONE);
        assertDivision(allOnes.multiply(base).subtract(BigInteger.ONE), allOnes, 2);
    }

    @Test
    void checksUnsignedSingleDigitDivisorsAndTheirMultiDigitNeighbors() {
        BigInteger base = BigInteger.ONE.shiftLeft(32);
        BigInteger[] divisors = {BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(3),
                BigInteger.ONE.shiftLeft(31).subtract(BigInteger.ONE), BigInteger.ONE.shiftLeft(31),
                base.subtract(BigInteger.TWO), base.subtract(BigInteger.ONE), base, base.add(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)};
        for (int bits : new int[] {0, 1, 31, 32, 33, 63, 64, 65, 127, 128, 237, 238, 474, 1024}) {
            BigInteger value = BigInteger.ONE.shiftLeft(bits);
            for (BigInteger d : divisors) {
                for (BigInteger n : new BigInteger[] {value.subtract(BigInteger.ONE), value, value.add(BigInteger.ONE)}) {
                    assertDivision(n, d, bits % 3);
                }
            }
        }
    }

    @Test
    void checksGroupedDivisionWithLongZeroRunsAndRandomWideOperands() {
        Random random = new Random(0x256D1A1DE32L);
        for (int i = 0; i < 2000; i++) {
            BigInteger d = new BigInteger(random.nextInt(1, 2049), random).setBit(0);
            BigInteger q = new BigInteger(random.nextInt(2049), random);
            BigInteger r = new BigInteger(d.bitLength(), random).mod(d);
            int commonShift = i % 5 == 0 ? (i % 9) * 32 : 0;
            assertDivision(d.multiply(q).add(r).shiftLeft(commonShift), d.shiftLeft(commonShift), i % 4);
        }
    }

    @Test
    void checksModularPowersForUnitPowerOfTwoAndOddModuli() {
        for (BigInteger modulus : new BigInteger[] {BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(3),
                BigInteger.ONE.shiftLeft(64), BigInteger.ONE.shiftLeft(237), BigInteger.ONE.shiftLeft(238).subtract(BigInteger.ONE)}) {
            long[] input = words(modulus), original = input.clone();
            for (long exponent : new long[] {0, 1, 2, 63, 64, 237, 238, 524285, Long.MAX_VALUE}) {
                assertEquals(BigInteger.TWO.modPow(BigInteger.valueOf(exponent), modulus),
                        integer(UnsignedWords.powerOfTwoMod(exponent, input)), "modular exponent=" + exponent);
                assertArrayEquals(original, input, "modular powers must leave the modulus unchanged");
            }
        }
    }
}
