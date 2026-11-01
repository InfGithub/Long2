package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.Binary;

class Binary256BytesTest {
    @Test
    void exposesTheExactBigEndianLayoutThroughTheInterface() {
        Binary<?> value = Binary256.fromRawBits(0x0123_4567_89ab_cdefL, 0xfedc_ba98_7654_3210L,
                0x0011_2233_4455_6677L, 0x8899_aabb_ccdd_eeffL);
        assertArrayEquals(HexFormat.of().parseHex(
                "0123456789abcdeffedcba987654321000112233445566778899aabbccddeeff"), value.toBytes());
    }

    @Test
    void preservesZerosInfinitiesAndAllNaNEncodingBits() {
        assertEquals("00".repeat(32), HexFormat.of().formatHex(Binary256.ZERO.toBytes()));
        assertEquals("80" + "00".repeat(31), HexFormat.of().formatHex(Binary256.NEGATIVE_ZERO.toBytes()));
        assertEquals("3ffff00000000000" + "00".repeat(24), HexFormat.of().formatHex(Binary256.ONE.toBytes()));
        for (Binary256 value : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.MIN_VALUE,
                Binary256.MIN_NORMAL, Binary256.MAX_VALUE, Binary256.POSITIVE_INFINITY, Binary256.NEGATIVE_INFINITY,
                Binary256.NaN, Binary256.fromRawBits(0x7fff_f000_0000_0000L, 0, 0, 1),
                Binary256.fromRawBits(0xffff_f000_0000_0000L, 0, 0, 1),
                Binary256.fromRawBits(0x7fff_f800_0000_0001L, 2, 3, 4),
                Binary256.fromRawBits(0xffff_f800_0000_0001L, 2, 3, 4),
                Binary256.fromRawBits(-1, -1, -1, -1))) {
            assertEncoding(value);
        }
    }

    @Test
    void returnsIndependentArraysForEveryCallIncludingConstants() {
        for (Binary256 value : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE,
                Binary256.POSITIVE_INFINITY, Binary256.NaN)) {
            byte[] first = value.toBytes(), second = value.toBytes(), snapshot = second.clone();
            assertNotSame(first, second);
            Arrays.fill(first, (byte) 0x5a);
            assertArrayEquals(snapshot, second);
            assertArrayEquals(snapshot, value.toBytes());
            assertEncoding(value);
        }
    }

    @Test
    void roundTripsRandomRawEncodingsWithoutCanonicalizingThem() {
        Random random = new Random(0xB17E5256L);
        for (int i = 0; i < 5000; i++) {
            assertEncoding(Binary256.fromRawBits(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()));
        }
    }

    @Test
    void restoresKnownBigEndianBytesWithoutSignedByteExtension() {
        byte[] bytes = HexFormat.of().parseHex(
                "0123456789abcdeffedcba987654321000112233445566778899aabbccddeeff");
        Binary256 value = Binary256.of(bytes);
        assertEquals(0x0123_4567_89ab_cdefL, value.getRawLong(0));
        assertEquals(0xfedc_ba98_7654_3210L, value.getRawLong(1));
        assertEquals(0x0011_2233_4455_6677L, value.getRawLong(2));
        assertEquals(0x8899_aabb_ccdd_eeffL, value.getRawLong(3));
        assertArrayEquals(bytes, value.toBytes());
    }

    @Test
    void rejectsNullAndEveryIncorrectLengthWithoutPaddingOrTruncation() {
        assertThrowsExactly(NullPointerException.class, () -> Binary256.of((byte[]) null));
        for (int length : new int[] {0, 1, 7, 8, 16, 24, 31, 33, 40, 64, 1000}) {
            assertThrowsExactly(IllegalArgumentException.class, () -> Binary256.of(new byte[length]));
        }
    }

    @Test
    void neitherModifiesNorRetainsTheInputArray() {
        for (Binary256 original : List.of(Binary256.ZERO, Binary256.NEGATIVE_ZERO, Binary256.ONE,
                Binary256.MIN_VALUE, Binary256.MAX_VALUE, Binary256.POSITIVE_INFINITY,
                Binary256.fromRawBits(0xffff_f000_0000_0001L, 2, 3, 4))) {
            byte[] input = original.toBytes(), snapshot = input.clone();
            Binary256 restored = Binary256.of(input);
            assertArrayEquals(snapshot, input);
            Arrays.fill(input, (byte) 0x5a);
            assertArrayEquals(snapshot, restored.toBytes());
            assertEquals(Binary256Reference.raw(original), Binary256Reference.raw(restored));
            byte[] output = restored.toBytes();
            Arrays.fill(output, (byte) 0);
            assertArrayEquals(snapshot, restored.toBytes());
        }
    }

    private static void assertEncoding(Binary256 value) {
        ByteBuffer expected = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < 4; i++) {
            expected.putLong(value.getRawLong(i));
        }
        byte[] actual = value.toBytes();
        assertEquals(32, actual.length);
        assertArrayEquals(expected.array(), actual);
        ByteBuffer encoded = ByteBuffer.wrap(actual).order(ByteOrder.BIG_ENDIAN);
        Binary256 restored = Binary256.fromRawBits(encoded.getLong(), encoded.getLong(), encoded.getLong(), encoded.getLong());
        // 使用原始位比较，equals 会忽略不同 NaN 编码之间的差异。
        assertEquals(Binary256Reference.raw(value), Binary256Reference.raw(restored));
        Binary256 parsed = Binary256.of(expected.array());
        assertEquals(Binary256Reference.raw(value), Binary256Reference.raw(parsed));
        assertArrayEquals(expected.array(), parsed.toBytes());
        assertEquals(Binary256Reference.raw(value), Binary256Reference.raw(Binary256.of(actual)));
    }
}
