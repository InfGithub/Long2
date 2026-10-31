
package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256Base3Test {
    @Test
    void testDoubleBasic() {
        // 零与基本整数
        assertEquals(Int256.ZERO, Int256.of(0.0));
        assertEquals(Int256.ZERO, Int256.of(-0.0));
        assertEquals(Int256.of(1L), Int256.of(1.0));
        assertEquals(Int256.of(-1L), Int256.of(-1.0));
        assertEquals(Int256.of(1119L), Int256.of(1119.0));
        assertEquals(Int256.of(-1119L), Int256.of(-1119.0));

        // 小数向零截断
        assertEquals(Int256.of(123L), Int256.of(123.75));
        assertEquals(Int256.of(-123L), Int256.of(-123.75));
        assertEquals(Int256.of(12L), Int256.of(12.999));
        assertEquals(Int256.of(-12L), Int256.of(-12.999));

        // 绝对值小于 1
        assertEquals(Int256.ZERO, Int256.of(0.999));
        assertEquals(Int256.ZERO, Int256.of(-0.999));
        assertEquals(Int256.ZERO, Int256.of(0.5));
        assertEquals(Int256.ZERO, Int256.of(-0.5));

        // subnormal 和极小有限值
        assertEquals(Int256.ZERO, Int256.of(Double.MIN_VALUE));
        assertEquals(Int256.ZERO, Int256.of(-Double.MIN_VALUE));
        assertEquals(Int256.ZERO, Int256.of(Double.MIN_NORMAL));
        assertEquals(Int256.ZERO, Int256.of(-Double.MIN_NORMAL));
    }

    @Test
    void testDoubleSpecialValues() {
        // NaN -> ZERO
        assertEquals(Int256.ZERO, Int256.of(Double.NaN));

        // 正负 NaN 及不同 payload
        assertEquals(Int256.ZERO, Int256.of(
                Double.longBitsToDouble(0x7ff0000000000001L)));

        assertEquals(Int256.ZERO, Int256.of(
                Double.longBitsToDouble(0x7ff8000000000001L)));

        assertEquals(Int256.ZERO, Int256.of(
                Double.longBitsToDouble(0xfff8000000000001L)));

        // Infinity -> 对应极值
        assertEquals(Int256.MAX_VALUE,
                Int256.of(Double.POSITIVE_INFINITY));

        assertEquals(Int256.MIN_VALUE,
                Int256.of(Double.NEGATIVE_INFINITY));

        // 最大有限 double：按 256-bit 回绕
        assertEquals(Int256.ZERO,
                Int256.of(Double.MAX_VALUE));

        assertEquals(Int256.ZERO,
                Int256.of(-Double.MAX_VALUE));
    }

    @Test
    void testDoublePrecision() {
        // 1.0 附近的小数截断
        assertEquals(
                Int256.ZERO,
                Int256.of(Math.nextDown(1.0)));

        assertEquals(
                Int256.of(1L),
                Int256.of(Math.nextUp(1.0)));

        assertEquals(
                Int256.ZERO,
                Int256.of(Math.nextUp(-1.0)));

        assertEquals(
                Int256.of(-1L),
                Int256.of(Math.nextDown(-1.0)));

        // 53-bit 有效精度边界
        assertEquals(
                Int256.of(9007199254740991L),
                Int256.of(9007199254740991.0));

        assertEquals(
                Int256.of(9007199254740992L),
                Int256.of(9007199254740992.0));

        // double 已经发生舍入，构造器不恢复丢失精度
        double rounded = (double) 9007199254740993L;

        assertEquals(
                Int256.of(9007199254740992L),
                Int256.of(rounded));

        // long 边界
        assertEquals(
                Int256.of(Long.MIN_VALUE),
                Int256.of((double) Long.MIN_VALUE));

        // Long.MAX_VALUE 转 double 后舍入为 2^63
        assertEquals(
                Int256.of(0L, 0L, 0L, Long.MIN_VALUE),
                Int256.of((double) Long.MAX_VALUE));

        // 跨 64-bit 边界
        assertEquals(
                Int256.of(0L, 0L, 1L, 0L),
                Int256.of(Math.scalb(1.0, 64)));

        assertEquals(
                Int256.of(0L, 1L, 0L, 0L),
                Int256.of(Math.scalb(1.0, 128)));

        assertEquals(
                Int256.of(1L, 0L, 0L, 0L),
                Int256.of(Math.scalb(1.0, 192)));

        // 指数和 significand 共同决定多个 bit
        double mixed = Double.longBitsToDouble(
                ((long) (1023 + 255) << 52) | 1L);

        assertEquals(
                Int256.of(Long.MIN_VALUE | (1L << 11),
                        0L, 0L, 0L),
                Int256.of(mixed));
    }

    @Test
    void testDoubleOverflow() {
        double pow255 = Math.scalb(1.0, 255);
        double pow256 = Math.scalb(1.0, 256);

        // 有符号 256-bit 边界
        assertEquals(Int256.MIN_VALUE,
                Int256.of(pow255));

        assertEquals(Int256.MIN_VALUE,
                Int256.of(-pow255));

        // 2^256 回绕为零
        assertEquals(Int256.ZERO,
                Int256.of(pow256));

        assertEquals(Int256.ZERO,
                Int256.of(-pow256));

        // 2^256 之上最近的 double
        // nextUp(2^256) = 2^256 + 2^204
        assertEquals(
                Int256.of(1L << 12, 0L, 0L, 0L),
                Int256.of(Math.nextUp(pow256)));

        // 2^256 之下最近的 double
        // nextDown(2^256) = 2^256 - 2^203
        assertEquals(
                Int256.of(-(1L << 11), 0L, 0L, 0L),
                Int256.of(Math.nextDown(pow256)));

        // 负数对称回绕
        assertEquals(
                Int256.of(-(1L << 12), 0L, 0L, 0L),
                Int256.of(-Math.nextUp(pow256)));

        // 更大的 2 的整数次幂
        assertEquals(Int256.ZERO,
                Int256.of(Math.scalb(1.0, 307)));

        assertEquals(Int256.ZERO,
                Int256.of(Math.scalb(1.0, 308)));

        assertEquals(Int256.ZERO,
                Int256.of(-Math.scalb(1.0, 308)));
    }

    @Test
    void testDoubleRandom() {
        Random random = new Random(0x256D0B1EL);

        // 10,000 组随机 IEEE 754 原始位模式
        for (int i = 0; i < 10000; i++) {
            double value = Double.longBitsToDouble(
                    random.nextLong());

            assertDoubleConversion(value);
        }

        // 穷举全部有限 exponent 编码
        long[] fractions = {
                0L,
                1L,
                0x0008000000000000L,
                0x000fffffffffffffL
        };

        for (int exponent = 0; exponent < 2047; exponent++) {
            for (long fraction : fractions) {
                long raw = ((long) exponent << 52) | fraction;

                assertDoubleConversion(
                        Double.longBitsToDouble(raw));

                assertDoubleConversion(
                        Double.longBitsToDouble(raw | Long.MIN_VALUE));
            }
        }
    }

    // 独立参考：精确取得 double 的整数部分并保留低 256 bit
    private static Int256 referenceDouble(double value) {
        if (Double.isNaN(value)) {
            return Int256.ZERO;
        }

        if (value == Double.POSITIVE_INFINITY) {
            return Int256.MAX_VALUE;
        }

        if (value == Double.NEGATIVE_INFINITY) {
            return Int256.MIN_VALUE;
        }

        BigInteger integer = new BigDecimal(value).toBigInteger();

        return Int256.of(
                integer.shiftRight(192).longValue(),
                integer.shiftRight(128).longValue(),
                integer.shiftRight(64).longValue(),
                integer.longValue());
    }

    private static void assertDoubleConversion(double value) {
        long raw = Double.doubleToRawLongBits(value);

        assertEquals(
                referenceDouble(value),
                Int256.of(value),
                () -> "Double raw bits: 0x"
                        + Long.toHexString(raw));
    }
}
