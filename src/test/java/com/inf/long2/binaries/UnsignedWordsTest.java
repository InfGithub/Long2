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

    private static UnsignedWords.Root assertScaledRoot(BigInteger magnitude, int shift) {
        BigInteger input = magnitude.shiftLeft(shift);
        assertTrue(magnitude.signum() > 0 && magnitude.bitLength() <= 237);
        assertTrue(input.bitLength() == 473 || input.bitLength() == 474);
        UnsignedWords.Root actual = UnsignedWords.sqrtScaled(magnitude.longValue(), magnitude.shiftRight(64).longValue(),
                magnitude.shiftRight(128).longValue(), magnitude.shiftRight(192).longValue(), shift);
        BigInteger[] expected = input.sqrtAndRemainder();
        BigInteger root = integer(actual.value()), remainder = integer(actual.remainder());
        assertEquals(expected[0], root, "scaled root input=" + input.toString(16));
        assertEquals(expected[1], remainder, "scaled residual input=" + input.toString(16));
        assertEquals(input, root.multiply(root).add(remainder));
        assertTrue(remainder.signum() >= 0 && remainder.compareTo(root.shiftLeft(1).add(BigInteger.ONE)) < 0);
        return actual;
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
    void checksScaledNewtonResidualsAtExactSquaresRoundingEdgesAndAdjacentCycles() {
        boolean exact = false, below = false, boundary = false, above = false, cycle = false;
        for (BigInteger input : Binary256CompatibilityFixtures.squareRootRoundingBoundaries()) {
            int shift = input.bitLength() - 237;
            assertTrue(input.getLowestSetBit() >= shift);
            UnsignedWords.Root result = assertScaledRoot(input.shiftRight(shift), shift);
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
        UnsignedWords.Root earlier = assertScaledRoot(high.add(BigInteger.TWO), 236);
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
    void checksLargeModularExponents() {
        BigInteger modulus = BigInteger.ONE.shiftLeft(237).subtract(BigInteger.valueOf(42));
        for (long exponent : new long[] {0, 1, 63, 64, 237, 524285}) {
            assertEquals(BigInteger.TWO.modPow(BigInteger.valueOf(exponent), modulus),
                    integer(UnsignedWords.powerOfTwoMod(exponent, words(modulus))));
        }
    }

    @Test
    void checksOwnedIncrementAcrossFullAndPartialWordCarries() {
        for (int bit : new int[] {0, 1, 63, 64, 65, 127, 128, 192, 236, 237, 238, 256, 512}) {
            for (int delta : new int[] {-1, 0, 1}) {
                BigInteger value = BigInteger.ONE.shiftLeft(bit).add(BigInteger.valueOf(delta));
                assertArrayEquals(words(value.add(BigInteger.ONE)), UnsignedWords.incrementInPlace(words(value)));
            }
        }
        assertArrayEquals(new long[] {0, 0, 4}, UnsignedWords.incrementInPlace(new long[] {-1, -1, 3}));
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
