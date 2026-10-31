package com.inf.long2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BinaryFormatTest {
    @ParameterizedTest
    @CsvSource({
            "11, -14, 15",
            "24, -126, 127",
            "53, -1022, 1023",
            "113, -16382, 16383",
            "237, -262142, 262143",
            "2, 0, 0",
            "2147483647, -2147483648, 2147483647"
    })
    void acceptsStandardAndExtendedFormatParameters(int precision, int minExponent, int maxExponent) {
        Binary.Format format = new Binary.Format(precision, minExponent, maxExponent);
        assertEquals(precision, format.precision());
        assertEquals(minExponent, format.minExponent());
        assertEquals(maxExponent, format.maxExponent());
    }

    @ParameterizedTest
    @CsvSource({
            "1, -14, 15",
            "0, -14, 15",
            "-2147483648, -14, 15",
            "53, 1, 1023",
            "53, 2147483647, 2147483647",
            "53, -1022, -1",
            "53, -2147483648, -2147483648"
    })
    void rejectsFormatsWithoutRequiredPrecisionOrNormalOne(int precision, int minExponent, int maxExponent) {
        assertThrows(IllegalArgumentException.class, () -> new Binary.Format(precision, minExponent, maxExponent));
    }

    @Test
    void comparesFormatsByAllNumericParameters() {
        Binary.Format format = new Binary.Format(53, -1022, 1023);
        Binary.Format same = new Binary.Format(53, -1022, 1023);
        assertEquals(format, same);
        assertEquals(format.hashCode(), same.hashCode());
        assertNotEquals(format, new Binary.Format(54, -1022, 1023));
        assertNotEquals(format, new Binary.Format(53, -1023, 1023));
        assertNotEquals(format, new Binary.Format(53, -1022, 1024));
    }
}
