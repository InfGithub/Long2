package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256BitTest {

    @Test
    void testShift() {
        Int256 value = Int256.of(1L);

        // 跨 long 边界
        assertEquals(
                Int256.of(0L, 0L, 1L, 0L),
                value.shift(64));

        assertEquals(
                Int256.of(0L, 1L, 0L, 0L),
                value.shift(128));

        assertEquals(
                Int256.of(1L, 0L, 0L, 0L),
                value.shift(192));

        // 算术右移，符号扩展
        assertEquals(
                Int256.of(-1L),
                Int256.of(-2L).shift(-1));

        // 位宽的整数倍归一化为零距离。
        assertEquals(
                value,
                value.shift(256));

        assertEquals(
                Int256.MIN_VALUE,
                Int256.MIN_VALUE.shift(-256));

        // int 最小值的数学绝对值为 2^31，对 256 取模仍为零。
        assertEquals(
                Int256.MIN_VALUE,
                Int256.MIN_VALUE.shift(Integer.MIN_VALUE));

        assertEquals(Int256.of(2L), value.shift(257));
        assertEquals(Int256.of(4L), Int256.of(8L).shift(-257));

        // 不移位
        assertEquals(value, value.shift(0));
    }

    @Test
    void testShiftAgainstBigInteger() {
        Int256[] values = {
                Int256.ZERO, Int256.ONE, Int256.of(-1L), Int256.MAX_VALUE, Int256.MIN_VALUE,
                Int256.of(0x123456789abcdefL, -1L, Long.MIN_VALUE, 0x76543210fedcba98L),
                Int256.of(-2L, 0L, Long.MAX_VALUE, 1L)
        };
        int[] magnitudes = {
                0, 1, 31, 32, 63, 64, 65, 127, 128, 129, 191, 192, 193,
                254, 255, 256, 257, 319, 320, 511, 512, 513, 1024, Integer.MAX_VALUE
        };
        for (Int256 value : values) {
            for (int magnitude : magnitudes) {
                assertShift(value, magnitude);
                assertShift(value, -magnitude);
            }
            assertShift(value, Integer.MIN_VALUE);
            assertShift(value, Integer.MIN_VALUE + 1);
        }

        Random random = new Random(0x25651F7L);
        for (int i = 0; i < 1000; i++) {
            Int256 value = Int256.of(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
            assertShift(value, random.nextInt());
        }
    }

    private static void assertShift(Int256 value, int bits) {
        BigInteger unsigned = new BigInteger(value.toHexString().substring(2), 16);
        BigInteger signed = value.isNegative() ? unsigned.subtract(BigInteger.ONE.shiftLeft(256)) : unsigned;
        int distance = BigInteger.valueOf(bits).abs().mod(BigInteger.valueOf(256)).intValueExact();
        BigInteger shifted = bits < 0 ? signed.shiftRight(distance) : signed.shiftLeft(distance);
        Int256 expected = Int256.of(
                shifted.shiftRight(192).longValue(), shifted.shiftRight(128).longValue(),
                shifted.shiftRight(64).longValue(), shifted.longValue());
        assertEquals(expected, value.shift(bits), () -> value.toHexString() + " shift " + bits);
    }

    @Test
    void testMinecraftCoordinateShifts() {
        // 正坐标：Block -> Chunk / Section -> Region
        Int256 block = Int256.of(8193L);
        Int256 chunk = Int256.of(512L);
        Int256 region = Int256.of(16L);

        assertEquals(chunk, block.shift(-4));
        assertEquals(region, chunk.shift(-5));
        assertEquals(region, block.shift(-9));

        // 反向转换：Region -> Chunk -> Block
        assertEquals(Int256.of(8192L), chunk.shift(4));
        assertEquals(chunk, region.shift(5));
        assertEquals(Int256.of(8192L), region.shift(9));

        // 负坐标：验证算术右移向负无穷取整
        Int256 negativeBlock = Int256.of(-513L);
        Int256 negativeChunk = Int256.of(-33L);
        Int256 negativeRegion = Int256.of(-2L);

        assertEquals(negativeChunk, negativeBlock.shift(-4));
        assertEquals(negativeRegion, negativeChunk.shift(-5));
        assertEquals(negativeRegion, negativeBlock.shift(-9));

        // 负坐标反向转换与对齐
        assertEquals(Int256.of(-528L), negativeChunk.shift(4));
        assertEquals(Int256.of(-64L), negativeRegion.shift(5));
        assertEquals(Int256.of(-1024L), negativeRegion.shift(9));

        // 原点附近的负坐标
        assertEquals(Int256.of(-1L), Int256.of(-1L).shift(-4));
        assertEquals(Int256.of(-1L), Int256.of(-1L).shift(-9));
        assertEquals(Int256.of(-512L),
                Int256.of(-1L).shift(-9).shift(9));

        // 跨 64-bit 边界：Block = 2^64
        Int256 largeBlock = Int256.of(0L, 0L, 1L, 0L);
        Int256 largeChunk = Int256.of(0L, 0L, 0L, 1L << 60);
        Int256 largeRegion = Int256.of(0L, 0L, 0L, 1L << 55);

        assertEquals(largeChunk, largeBlock.shift(-4));
        assertEquals(largeRegion, largeChunk.shift(-5));
        assertEquals(largeRegion, largeBlock.shift(-9));

        // 跨边界反向转换，验证完整恢复
        assertEquals(largeBlock, largeChunk.shift(4));
        assertEquals(largeChunk, largeRegion.shift(5));
        assertEquals(largeBlock, largeRegion.shift(9));
    }

}
