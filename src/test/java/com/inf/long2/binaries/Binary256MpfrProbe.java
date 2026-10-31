package com.inf.long2.binaries;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** 为外部 MPFR 差分测试提供逐行原始编码协议，避免通过 double 交换数值。 */
public final class Binary256MpfrProbe {
    private Binary256MpfrProbe() {
    }

    public static void main(String[] args) throws Exception {
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                var output = new PrintWriter(System.out, false, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split(" ");
                Binary256 a = parse(fields[1]);
                Binary256 b = fields.length > 2 ? parse(fields[2]) : Binary256.ZERO;
                Binary256 c = fields.length > 3 ? parse(fields[3]) : Binary256.ZERO;
                String result = switch (fields[0]) {
                    case "add" -> raw(a.add(b));
                    case "sub" -> raw(a.subtract(b));
                    case "mul" -> raw(a.multiply(b));
                    case "div" -> raw(a.divide(b));
                    case "fma" -> raw(a.fma(b, c));
                    case "rem" -> raw(a.remainder(b));
                    case "ieee" -> raw(a.ieeeRemainder(b));
                    case "sqrt" -> raw(a.sqrt());
                    case "float" -> Integer.toHexString(Float.floatToRawIntBits(a.toFloat()));
                    case "double" -> Long.toHexString(Double.doubleToRawLongBits(a.toDouble()));
                    case "int" -> Integer.toHexString(a.toInt());
                    case "long" -> Long.toHexString(a.toLong());
                    default -> throw new IllegalArgumentException(fields[0]);
                };
                output.println(result);
            }
        }
    }

    private static Binary256 parse(String bits) {
        return Binary256.fromRawBits(Long.parseUnsignedLong(bits.substring(0, 16), 16),
                Long.parseUnsignedLong(bits.substring(16, 32), 16),
                Long.parseUnsignedLong(bits.substring(32, 48), 16),
                Long.parseUnsignedLong(bits.substring(48, 64), 16));
    }

    private static String raw(Binary256 value) {
        return "%016x%016x%016x%016x".formatted(value.getRawLong(0), value.getRawLong(1),
                value.getRawLong(2), value.getRawLong(3));
    }
}
