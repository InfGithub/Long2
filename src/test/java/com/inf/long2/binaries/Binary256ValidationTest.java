package com.inf.long2.binaries;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class Binary256ValidationTest {
    private static final Pattern DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Set<String> SPECIAL = Set.of("NaN", "Infinity", "+Infinity", "-Infinity");

    @Test
    void acceptsEverySupportedDecimalAndSpecialForm() {
        for (String text : List.of("0", "-0", "+0", "123", "-123.5", "+.5", "1.", ".0", "-.0",
                "00012.500", "000", "1e3", "-1.25E-20", "+1.e+3", ".5E0002", "0e-0",
                "NaN", "Infinity", "+Infinity", "-Infinity")) {
            assertTrue(Binary256.isValid(text), text);
            assertDoesNotThrow(() -> Binary256.of(text), text);
        }
    }

    @Test
    void rejectsMalformedTextAndPreservesFactoryExceptions() {
        assertFalse(Binary256.isValid(null));
        assertThrowsExactly(NullPointerException.class, () -> Binary256.of((String) null));
        for (String text : List.of("", "+", "-", ".", "+.", "e1", "1e", "1e+", "1e-", "1e2e3",
                "1..0", "1.0.0", "1e1.0", " 1", "1 ", "1\n", "\t1", "1_000", "1,000", "0x1p0", "1f", "1d",
                "nan", "infinity", "INF", "-NaN", "+NaN", "１２", "١", "1\u0000", "--1", "++1")) {
            assertFalse(Binary256.isValid(text), text);
            assertThrowsExactly(NumberFormatException.class, () -> Binary256.of(text), text);
        }
    }

    @Test
    void validatesSyntaxWithoutRequiringFiniteOrExactRepresentation() {
        for (String text : List.of("0.1", "1e78914", "-1e78914", "1e-78984", "-1e-78984")) {
            assertTrue(Binary256.isValid(text), text);
            assertDoesNotThrow(() -> Binary256.of(text), text);
        }
        String exponent = "9".repeat(1000);
        assertTrue(Binary256.isValid("1e" + exponent));
        assertTrue(Binary256.of("1e" + exponent).isInfinite());
        assertTrue(Binary256.isValid("-1e-" + exponent));
        assertEquals(Binary256.NEGATIVE_ZERO, Binary256.of("-1e-" + exponent));
        for (String prefix : List.of("1e", "0e", "1e-", "-0e-")) {
            String invalid = prefix + exponent + "x";
            assertFalse(Binary256.isValid(invalid));
            assertThrowsExactly(NumberFormatException.class, () -> Binary256.of(invalid));
        }
    }

    @Test
    void scansLongCoefficientsAndExponentsWithoutNumericConversion() {
        String coefficient = "7".repeat(1_000_000);
        assertTrue(Binary256.isValid(coefficient));
        assertTrue(Binary256.isValid("0." + coefficient + "e1000000"));
        assertFalse(Binary256.isValid(coefficient + "x"));
        String exponent = "9".repeat(1_000_000);
        assertTrue(Binary256.isValid("-1e-" + exponent));
        assertFalse(Binary256.isValid("0e" + exponent + "x"));
    }

    @Test
    void agreesWithAnIndependentGrammarAndWithParsing() {
        Random random = new Random(0x15A11D256L);
        String alphabet = "+-0123456789.eE NaInfinity_\n\u0000\uff11";
        for (int i = 0; i < 5000; i++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(50);
            for (int j = 0; j < length; j++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertGrammar(text.toString());
        }
        for (int i = 0; i < 500; i++) {
            String sign = random.nextBoolean() ? "-" : "+";
            String integer = Integer.toString(random.nextInt(1_000_000));
            String fraction = Integer.toString(random.nextInt(1_000_000));
            String text = sign + (random.nextBoolean() ? integer : "") + "." + fraction
                    + (random.nextBoolean() ? "e" : "E") + (random.nextInt(2001) - 1000);
            assertGrammar(text);
        }
    }

    private static void assertGrammar(String text) {
        boolean expected = SPECIAL.contains(text) || DECIMAL.matcher(text).matches();
        assertEquals(expected, Binary256.isValid(text), text);
        if (expected) {
            assertDoesNotThrow(() -> Binary256.of(text), text);
        } else {
            assertThrowsExactly(NumberFormatException.class, () -> Binary256.of(text), text);
        }
    }
}
