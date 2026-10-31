package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            assertEquals(a.add(b), integer(UnsignedWords.add(aw, bw)));
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
                assertEquals(value.shiftRight(distance), integer(UnsignedWords.right(words, distance)));
                assertEquals(value.and(BigInteger.ONE.shiftLeft(distance).subtract(BigInteger.ONE)).signum() != 0,
                        UnsignedWords.anyLow(words, distance));
            }
            assertEquals(BigInteger.ZERO, integer(UnsignedWords.right(words, Long.MAX_VALUE)));
            assertEquals(value.signum() != 0, UnsignedWords.anyLow(words, Long.MAX_VALUE));
            assertEquals(value.bitLength(), UnsignedWords.bitLength(Arrays.copyOf(words, words.length + 2)));
        }
    }

    @Test
    void checksSquareRootAndRemainder() {
        Random random = new Random(0x2565007L);
        for (int i = 0; i < 200; i++) {
            BigInteger value = new BigInteger(random.nextInt(600), random);
            UnsignedWords.Root result = UnsignedWords.sqrt(words(value));
            BigInteger[] expected = value.sqrtAndRemainder();
            assertEquals(expected[0], integer(result.value()));
            assertEquals(expected[1], integer(result.remainder()));
        }
    }

    @Test
    void checksLargeModularExponents() {
        BigInteger modulus = BigInteger.ONE.shiftLeft(237).subtract(BigInteger.valueOf(42));
        for (long exponent : new long[] {0, 1, 63, 64, 237, 524285}) {
            assertEquals(BigInteger.TWO.modPow(BigInteger.valueOf(exponent), modulus),
                    integer(UnsignedWords.powerOfTwoMod(exponent, words(modulus))));
        }
    }
}
