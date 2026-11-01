package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Int;
import com.inf.long2.ints.Int256;

class Int256BytesTest {
    @Test
    void preservesFixedWidthTwosComplementForZeroNegativesAndExtremes() {
        assertEquals("00".repeat(32), HexFormat.of().formatHex(Int256.ZERO.toBytes()));
        assertEquals("00".repeat(31) + "01", HexFormat.of().formatHex(Int256.ONE.toBytes()));
        assertEquals("ff".repeat(32), HexFormat.of().formatHex(Int256.of(-1L).toBytes()));
        assertEquals("80" + "00".repeat(31), HexFormat.of().formatHex(Int256.MIN_VALUE.toBytes()));
        assertEquals("7f" + "ff".repeat(31), HexFormat.of().formatHex(Int256.MAX_VALUE.toBytes()));
        for (Int256 value : List.of(Int256.ZERO, Int256.ONE, Int256.of(-1L), Int256.MIN_VALUE, Int256.MAX_VALUE,
                Int256.of(Long.MIN_VALUE), Int256.of(Long.MAX_VALUE))) {
            assertEncoding(value);
        }
    }

    @Test
    void usesBigEndianGroupsWithoutSignedByteExtension() {
        byte[] bytes = HexFormat.of().parseHex(
                "0123456789abcdeffedcba987654321000112233445566778899aabbccddeeff");
        Int256 value = Int256.of(bytes);
        assertEquals(0x0123_4567_89ab_cdefL, value.getLong(0));
        assertEquals(0xfedc_ba98_7654_3210L, value.getLong(1));
        assertEquals(0x0011_2233_4455_6677L, value.getLong(2));
        assertEquals(0x8899_aabb_ccdd_eeffL, value.getLong(3));
        Int<?> contract = value;
        assertArrayEquals(bytes, contract.toBytes());
    }

    @Test
    void rejectsIncorrectLengthsWithoutPaddingTruncationOrSignExtension() {
        assertThrowsExactly(NullPointerException.class, () -> Int256.of((byte[]) null));
        assertThrowsExactly(NullPointerException.class, () -> Int256.of((long[]) null));
        for (int length : new int[] {0, 1, 7, 8, 16, 24, 31, 33, 40, 64, 1000}) {
            byte[] bytes = new byte[length];
            Arrays.fill(bytes, (byte) -1);
            assertThrowsExactly(IllegalArgumentException.class, () -> Int256.of(bytes));
        }
    }

    @Test
    void neitherRetainsNorModifiesArraysAndAlwaysReturnsIndependentBytes() {
        for (Int256 value : List.of(Int256.ZERO, Int256.ONE, Int256.of(-1L), Int256.MIN_VALUE, Int256.MAX_VALUE)) {
            byte[] input = value.toBytes(), snapshot = input.clone();
            Int256 restored = Int256.of(input);
            assertArrayEquals(snapshot, input);
            Arrays.fill(input, (byte) 0x5a);
            assertEquals(value, restored);
            byte[] first = restored.toBytes(), second = restored.toBytes();
            assertNotSame(first, second);
            Arrays.fill(first, (byte) 0x5a);
            assertArrayEquals(snapshot, second);
            assertArrayEquals(snapshot, restored.toBytes());
        }
    }

    @Test
    void roundTripsRandomEncodingsAgainstIndependentByteAndIntegerReferences() {
        Random random = new Random(0x1B17E256L);
        for (int i = 0; i < 5000; i++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            ByteBuffer reference = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            Int256 expected = Int256.of(reference.getLong(), reference.getLong(), reference.getLong(), reference.getLong());
            Int256 restored = Int256.of(bytes);
            assertEquals(expected, restored);
            assertArrayEquals(bytes, restored.toBytes());
            assertEquals(new BigInteger(bytes).toString(), restored.toString());
            assertEncoding(restored);
        }
    }

    private static void assertEncoding(Int256 value) {
        ByteBuffer reference = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < 4; i++) {
            reference.putLong(value.getLong(i));
        }
        byte[] bytes = value.toBytes();
        assertEquals(32, bytes.length);
        assertArrayEquals(reference.array(), bytes);
        assertEquals(value, Int256.of(bytes));
    }
}
