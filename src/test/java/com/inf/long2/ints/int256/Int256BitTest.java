package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        // 左移溢出
        assertEquals(
                Int256.of(0L),
                value.shift(256));

        // 右移超出位宽
        assertEquals(
                Int256.of(-1L),
                Int256.MIN_VALUE.shift(-256));

        // int 最小值不会导致取负溢出
        assertEquals(
                Int256.of(-1L),
                Int256.MIN_VALUE.shift(Integer.MIN_VALUE));

        // 不移位
        assertEquals(value, value.shift(0));
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
