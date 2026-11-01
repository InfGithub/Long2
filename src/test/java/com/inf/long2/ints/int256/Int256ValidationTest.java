package com.inf.long2.ints.int256;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.inf.long2.ints.Int256;

class Int256ValidationTest {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern HEX = Pattern.compile("0x[0-9a-f]+");

    @Test
    void acceptsSignedDecimalIntegersWithLeadingZeros() {
        for (String text : List.of("0", "-0", "+0", "123", "-123", "+00123", "000", "-000123",
                Int256.MAX_VALUE.toString(), Int256.MIN_VALUE.toString())) {
            assertTrue(Int256.isValid(text), text);
            assertFalse(Int256.isHexValid(text), text);
            assertDoesNotThrow(() -> Int256.parseInt(text), text);
        }
        assertEquals(Int256.ZERO, Int256.parseInt("-0"));
    }

    @Test
    void rejectsMalformedDecimalsAndPreservesNullException() {
        assertFalse(Int256.isValid(null));
        assertThrowsExactly(NumberFormatException.class, () -> Int256.parseInt(null));
        for (String text : List.of("", "+", "-", " 1", "1 ", "1\n", "\t1", "1_000", "1,000", "1.0",
                "1e3", "0x10", "0X10", "--1", "++1", "+-1", "-+1", "１２３", "١٢٣", "1\u0000",
                "NaN", "Infinity", "-Infinity")) {
            assertFalse(Int256.isValid(text), text);
            assertThrowsExactly(NumberFormatException.class, () -> Int256.parseInt(text), text);
        }
    }

    @Test
    void acceptsOnlyPrefixedLowercaseHexadecimalIntegers() {
        for (String text : List.of("0x0", "0xff", "0x0000123", "0xabcdef", "0x1234567890abcdef",
                Int256.MAX_VALUE.toHexString(), Int256.MIN_VALUE.toHexString(), Int256.of(-1L).toHexString())) {
            assertTrue(Int256.isHexValid(text), text);
            assertFalse(Int256.isValid(text), text);
            assertDoesNotThrow(() -> Int256.parseHexInt(text), text);
        }
    }

    @Test
    void rejectsMalformedHexadecimalsAndPreservesNullException() {
        assertFalse(Int256.isHexValid(null));
        assertThrowsExactly(NullPointerException.class, () -> Int256.parseHexInt(null));
        for (String text : List.of("", "0", "ff", "0x", "0Xff", "0xFF", "0xfF", "0xg", "+0xff",
                "-0xff", "0x-ff", "0x+ff", " 0xff", "0xff ", "0xff\n", "0x1_0", "0x1.0", "0x１",
                "0x١", "0xff\u0000")) {
            assertFalse(Int256.isHexValid(text), text);
            assertThrowsExactly(NumberFormatException.class, () -> Int256.parseHexInt(text), text);
        }
    }

    @Test
    void validatesSyntaxWithoutRejectingValuesThatWrap() {
        BigInteger modulus = BigInteger.ONE.shiftLeft(256);
        for (BigInteger number : List.of(modulus, modulus.negate(), modulus.add(BigInteger.ONE),
                modulus.negate().subtract(BigInteger.ONE), BigInteger.ONE.shiftLeft(1024))) {
            assertTrue(Int256.isValid(number.toString()));
            assertEquals(number.mod(modulus), new BigInteger(Int256.parseInt(number.toString()).toHexString().substring(2), 16));
        }
        String hex = "0x1" + "0".repeat(64);
        assertTrue(Int256.isHexValid(hex));
        assertEquals(Int256.ZERO, Int256.parseHexInt(hex));
    }

    @Test
    void scansLongTextsAndChecksDiscardedHexDigits() {
        String decimal = "9".repeat(1_000_000);
        assertTrue(Int256.isValid("-" + decimal));
        assertFalse(Int256.isValid(decimal + "x"));
        String hexadecimal = "0x" + "f".repeat(1_000_000);
        assertTrue(Int256.isHexValid(hexadecimal));
        assertEquals(Int256.of(-1L), Int256.parseHexInt(hexadecimal));
        for (String invalid : List.of("0xg" + "0".repeat(10000), hexadecimal + "g")) {
            assertFalse(Int256.isHexValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Int256.parseHexInt(invalid));
        }
    }

    @Test
    void agreesWithIndependentGrammarsAndExistingParsers() {
        Random random = new Random(0x1A11D256L);
        String alphabet = "+-0123456789abcdefABCDEFxX._ \n\u0000\uff11";
        for (int i = 0; i < 5000; i++) {
            StringBuilder builder = new StringBuilder();
            int length = random.nextInt(50);
            for (int j = 0; j < length; j++) {
                builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String text = builder.toString();
            boolean decimal = DECIMAL.matcher(text).matches(), hex = HEX.matcher(text).matches();
            assertEquals(decimal, Int256.isValid(text), text);
            assertEquals(hex, Int256.isHexValid(text), text);
            if (decimal) {
                assertDoesNotThrow(() -> Int256.parseInt(text));
            } else {
                assertThrowsExactly(NumberFormatException.class, () -> Int256.parseInt(text));
            }
            if (hex) {
                assertDoesNotThrow(() -> Int256.parseHexInt(text));
            } else {
                assertThrowsExactly(NumberFormatException.class, () -> Int256.parseHexInt(text));
            }
        }
        BigInteger modulus = BigInteger.ONE.shiftLeft(256);
        for (int i = 0; i < 500; i++) {
            BigInteger number = new BigInteger(1024, random);
            String hex = "0x" + number.toString(16);
            String decimal = (random.nextBoolean() ? "-000" : "+000") + number;
            assertTrue(Int256.isValid(decimal));
            assertTrue(Int256.isHexValid(hex));
            Int256 parsed = Int256.parseHexInt(hex);
            assertEquals(number.mod(modulus), new BigInteger(parsed.toHexString().substring(2), 16));
            assertTrue(Int256.isValid(parsed.toString()));
            assertTrue(Int256.isHexValid(parsed.toHexString()));
        }
    }
}
