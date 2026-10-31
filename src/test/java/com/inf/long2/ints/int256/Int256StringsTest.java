package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

public class Int256StringsTest {
    @Test
    void testToString() {
        // 基本数值
        assertEquals("0", Int256.ZERO.toString());
        assertEquals("1", Int256.of(1L).toString());
        assertEquals("-1", Int256.of(-1L).toString());
        assertEquals("1119", Int256.of(1119L).toString());
        assertEquals("-1119", Int256.of(-1119L).toString());

        // long 边界
        assertEquals(
                "9223372036854775807",
                Int256.of(Long.MAX_VALUE).toString());

        assertEquals(
                "-9223372036854775808",
                Int256.of(Long.MIN_VALUE).toString());

        // 跨越 64-bit 边界
        assertEquals(
                "18446744073709551616",
                Int256.of(0L, 0L, 1L, 0L).toString());

        assertEquals(
                "340282366920938463463374607431768211456",
                Int256.of(0L, 1L, 0L, 0L).toString());

        // 256-bit 极值
        assertEquals(
                "57896044618658097711785492504343953926634992332820282019728792003956564819967",
                Int256.MAX_VALUE.toString());

        assertEquals(
                "-57896044618658097711785492504343953926634992332820282019728792003956564819968",
                Int256.MIN_VALUE.toString());
    }

    @Test
    void testParseInt256() {
        // 基本数值
        assertEquals(Int256.ZERO, Int256.parseInt("0"));
        assertEquals(Int256.of(1L), Int256.parseInt("1"));
        assertEquals(Int256.of(-1L), Int256.parseInt("-1"));
        assertEquals(Int256.of(1119L), Int256.parseInt("1119"));
        assertEquals(Int256.of(-1119L), Int256.parseInt("-1119"));

        // 符号与前导零
        assertEquals(Int256.ZERO, Int256.parseInt("+0"));
        assertEquals(Int256.ZERO, Int256.parseInt("-0"));
        assertEquals(Int256.ZERO, Int256.parseInt("000000"));
        assertEquals(Int256.of(1119L),
                Int256.parseInt("+0001119"));
        assertEquals(Int256.of(-1119L),
                Int256.parseInt("-0001119"));

        // long 边界
        assertEquals(Int256.of(Long.MAX_VALUE),
                Int256.parseInt("9223372036854775807"));

        assertEquals(Int256.of(Long.MIN_VALUE),
                Int256.parseInt("-9223372036854775808"));

        // 跨越 64-bit 边界
        assertEquals(
                Int256.of(0L, 0L, 1L, 0L),
                Int256.parseInt("18446744073709551616"));

        // 256-bit 极值
        assertEquals(
                Int256.MAX_VALUE,
                Int256.parseInt(
                        "57896044618658097711785492504343953926634992332820282019728792003956564819967"));

        assertEquals(
                Int256.MIN_VALUE,
                Int256.parseInt(
                        "-57896044618658097711785492504343953926634992332820282019728792003956564819968"));
    }

    @Test
    void testParseInt256Overflow() {
        BigInteger one = BigInteger.ONE;
        BigInteger pow255 = one.shiftLeft(255);
        BigInteger pow256 = one.shiftLeft(256);

        // 有符号 256-bit 边界
        assertEquals(
                Int256.MAX_VALUE,
                Int256.parseInt(pow255.subtract(one).toString()));

        assertEquals(
                Int256.MIN_VALUE,
                Int256.parseInt(pow255.negate().toString()));

        // 正向溢出
        assertEquals(
                Int256.MIN_VALUE,
                Int256.parseInt(pow255.toString()));

        assertEquals(
                Int256.ZERO,
                Int256.parseInt(pow256.toString()));

        assertEquals(
                Int256.of(1L),
                Int256.parseInt(pow256.add(one).toString()));

        // 负向溢出
        assertEquals(
                Int256.MAX_VALUE,
                Int256.parseInt(
                        pow255.negate().subtract(one).toString()));

        assertEquals(
                Int256.ZERO,
                Int256.parseInt(pow256.negate().toString()));

        assertEquals(
                Int256.of(-1L),
                Int256.parseInt(
                        pow256.add(one).negate().toString()));
    }

    @Test
    void testParseInt256Invalid() {
        assertThrows(NumberFormatException.class,
                () -> Int256.parseInt(null));

        String[] invalid = {
                "",
                "+",
                "-",
                " ",
                " 123",
                "123 ",
                "1 2",
                "12.3",
                "1e3",
                "1_000",
                "0x10",
                "0X10",
                "--1",
                "++1",
                "+-1",
                "-+1",
                "123abc",
                "１２３",
                "\u0661\u0662\u0663"
        };

        for (String value : invalid) {
            assertThrows(
                    NumberFormatException.class,
                    () -> Int256.parseInt(value),
                    "Invalid input: " + value);
        }
    }

    @Test
    void testRandomRoundTrip() {
        Random random = new Random(0x256DEC1L);
        BigInteger modulus = BigInteger.ONE.shiftLeft(256);
        BigInteger mask = modulus.subtract(BigInteger.ONE);

        // 随机 256-bit 有符号整数
        for (int i = 0; i < 10000; i++) {
            Int256 value = Int256.of(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong());

            BigInteger reference = new BigInteger(
                    value.toHexString().substring(2), 16);

            if (value.isNegative()) {
                reference = reference.subtract(modulus);
            }

            String expected = reference.toString();

            // 独立验证十进制输出
            assertEquals(
                    expected,
                    value.toString(),
                    value.toHexString());

            // 验证字符串解析
            assertEquals(
                    value,
                    Int256.parseInt(expected),
                    expected);
        }

        // 随机超宽十进制输入，验证 256-bit 回绕
        for (int i = 0; i < 1000; i++) {
            BigInteger value = new BigInteger(512, random);

            if (random.nextBoolean()) {
                value = value.negate();
            }

            BigInteger truncated = value.and(mask);
            Int256 expected = Int256.parseHexInt(
                    "0x" + truncated.toString(16));

            assertEquals(
                    expected,
                    Int256.parseInt(value.toString()),
                    value.toString());
        }
    }
}
