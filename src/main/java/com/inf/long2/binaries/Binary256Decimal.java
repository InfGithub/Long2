package com.inf.long2.binaries;

import java.util.Arrays;
import java.util.Objects;

/** 精确十进制转换；幅值低组在前，不经过其他浮点格式。 */
final class Binary256Decimal {
    private static final int QMIN = -262378;
    private static final long EXPONENT_LIMIT = Long.MAX_VALUE / 4;
    // 仅缓存 5^(2^i), i <= 16；覆盖全部输出指数，缓存容量不随输入增长。
    private static final long[][] FIVE_POWERS = new long[17][];

    static {
        FIVE_POWERS[0] = new long[] {5};
    }

    private Binary256Decimal() {
    }

    static Binary256 parse(String text) {
        Objects.requireNonNull(text);
        switch (text) {
            case "NaN": return Binary256.NaN;
            case "Infinity", "+Infinity": return Binary256.POSITIVE_INFINITY;
            case "-Infinity": return Binary256.NEGATIVE_INFINITY;
            default: break;
        }
        int length = text.length(), start = 0;
        boolean negative = length != 0 && text.charAt(0) == '-';
        if (length != 0 && (negative || text.charAt(0) == '+')) {
            start++;
        }
        int index = start, digits = 0, fraction = 0, first = -1, last = -1;
        boolean point = false;
        while (index < length && text.charAt(index) != 'e' && text.charAt(index) != 'E') {
            char c = text.charAt(index++);
            if (c == '.' && !point) {
                point = true;
                continue;
            }
            if (c < '0' || c > '9') {
                throw invalid(text);
            }
            if (c != '0') {
                if (first < 0) {
                    first = digits;
                }
                last = digits;
            }
            digits++;
            if (point) {
                fraction++;
            }
        }
        if (digits == 0) {
            throw invalid(text);
        }
        int end = index;
        long exponent = 0;
        if (index < length) {
            index++;
            boolean exponentNegative = index < length && text.charAt(index) == '-';
            if (index < length && (exponentNegative || text.charAt(index) == '+')) {
                index++;
            }
            int exponentStart = index;
            while (index < length) {
                char c = text.charAt(index++);
                if (c < '0' || c > '9') {
                    throw invalid(text);
                }
                // 饱和值远大于字符串可能提供的小数位修正，故不会误判相互抵消的指数。
                exponent = exponent > (EXPONENT_LIMIT - (c - '0')) / 10
                        ? EXPONENT_LIMIT : exponent * 10 + c - '0';
            }
            if (index == exponentStart) {
                throw invalid(text);
            }
            if (exponentNegative) {
                exponent = -exponent;
            }
        }
        if (first < 0) {
            return zero(negative);
        }
        long scale = exponent - fraction + (digits - 1L - last);
        long decimalExponent = scale + (last - (long) first);
        // 保守边界严格包围全部有限非零舍入区间；此时整段文本已完成语法检查。
        if (decimalExponent > 80000) {
            return infinity(negative);
        }
        if (decimalExponent < -80000) {
            return zero(negative);
        }
        long[] coefficient = new long[0];
        int ordinal = 0, chunk = 0, chunkDigits = 0;
        for (index = start; index < end; index++) {
            char c = text.charAt(index);
            if (c == '.') {
                continue;
            }
            if (ordinal >= first && ordinal <= last) {
                chunk = chunk * 10 + c - '0';
                if (++chunkDigits == 9) {
                    coefficient = multiplyAdd(coefficient, 1_000_000_000, chunk);
                    chunk = chunkDigits = 0;
                }
            }
            ordinal++;
        }
        if (chunkDigits != 0) {
            int factor = 1;
            for (int i = 0; i < chunkDigits; i++) {
                factor *= 10;
            }
            coefficient = multiplyAdd(coefficient, factor, chunk);
        }
        long[] numerator = coefficient, denominator = new long[] {1};
        if (scale >= 0) {
            numerator = UnsignedWords.multiply(numerator, powerOfFive(Math.toIntExact(scale)));
        } else {
            denominator = powerOfFive(Math.toIntExact(-scale));
        }
        return round(negative, numerator, denominator, scale);
    }

    private static NumberFormatException invalid(String text) {
        return new NumberFormatException("Invalid decimal value: " + text);
    }

    /** 直接舍入 numerator / denominator * 2^scale，整数商和余数共同决定最近偶数方向。 */
    private static Binary256 round(boolean negative, long[] numerator, long[] denominator, long scale) {
        int ratioExponent = UnsignedWords.bitLength(numerator) - UnsignedWords.bitLength(denominator);
        int comparison = ratioExponent >= 0
                ? UnsignedWords.compare(numerator, UnsignedWords.left(denominator, ratioExponent))
                : UnsignedWords.compare(UnsignedWords.left(numerator, -ratioExponent), denominator);
        long exponent = scale + ratioExponent - (comparison < 0 ? 1 : 0);
        if (exponent > 262143) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        long alignment = scale - quantum;
        if (alignment >= 0) {
            numerator = UnsignedWords.left(numerator, Math.toIntExact(alignment));
        } else {
            denominator = UnsignedWords.left(denominator, Math.toIntExact(-alignment));
        }
        UnsignedWords.Division division = UnsignedWords.divide(numerator, denominator);
        long[] result = division.quotient();
        int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), denominator);
        return Binary256.packRounded(negative, UnsignedWords.word(result, 0), UnsignedWords.word(result, 1),
                UnsignedWords.word(result, 2), UnsignedWords.word(result, 3), quantum,
                half > 0 || (half == 0 && UnsignedWords.bit(result, 0)));
    }

    static String format(Binary256 value, boolean scientific) {
        if (value.isNaN()) {
            return "NaN";
        }
        if (value.isInfinite()) {
            return value.signBit() ? "-Infinity" : "Infinity";
        }
        String sign = value.signBit() ? "-" : "";
        if (value.isZero()) {
            return sign + (scientific ? "0.0e0" : "0");
        }
        Decimal decimal = shortest(value);
        String digits = decimal.digits();
        int exponent = decimal.exponent();
        if (scientific) {
            return sign + digits.charAt(0) + "." + (digits.length() == 1 ? "0" : digits.substring(1))
                    + "e" + exponent;
        }
        int point = exponent + 1;
        if (point <= 0) {
            return sign + "0." + "0".repeat(-point) + digits;
        }
        if (point >= digits.length()) {
            return sign + digits + "0".repeat(point - digits.length());
        }
        return sign + digits.substring(0, point) + "." + digits.substring(point);
    }

    private record Decimal(String digits, int exponent) {
    }

    static String format(Binary256 value, boolean scientific, int fractionDigits) {
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("fractionDigits must not be negative");
        }
        if (!value.isFinite()) {
            return format(value, scientific);
        }
        boolean negative = value.signBit();
        // 先检查必需的最小长度，尤其避免超大参数在精确转换前发生 int 溢出。
        checkLength((negative ? 1L : 0L) + 1 + (fractionDigits == 0 ? 0L : 1L + fractionDigits)
                + (scientific ? 2 : 0));
        if (value.isZero()) {
            return scientific ? scientificFixed(negative, "0", fractionDigits, 0)
                    : plainFixed(negative, "0", 0, fractionDigits);
        }
        long[] magnitude = UnsignedWords.trim(new long[] {value.getSignificandLong(3),
                value.getSignificandLong(2), value.getSignificandLong(1), value.getSignificandLong(0)});
        int trailing = 0;
        while (magnitude[trailing / 64] == 0) {
            trailing += 64;
        }
        trailing += Long.numberOfTrailingZeros(magnitude[trailing / 64]);
        magnitude = right(magnitude, trailing);
        int scale = Math.toIntExact(value.scaleExponent() + trailing);
        int exactFractionDigits = Math.max(-scale, 0);
        int exponent = scientific ? decimalExponent(magnitude, scale) : 0;
        long decimalShift = fractionDigits - (long) exponent;
        String digits;
        int appendedZeros = 0;
        if (decimalShift >= exactFractionDigits) {
            // 精确展开已经结束；仅计算有限的非零部分，余下请求位数由排版直接补零。
            long[] coefficient = scale >= 0 ? UnsignedWords.left(magnitude, scale)
                    : UnsignedWords.multiply(magnitude, powerOfFive(exactFractionDigits));
            digits = decimalDigits(coefficient);
            if (!scientific) {
                appendedZeros = fractionDigits - exactFractionDigits;
            }
        } else {
            // 此分支的十进制缩放受格式范围约束，而不随任意大的 fractionDigits 增长。
            Rational rational = scaledDecimal(magnitude, scale, Math.toIntExact(decimalShift));
            UnsignedWords.Division division = UnsignedWords.divide(rational.numerator(), rational.denominator());
            long[] coefficient = division.quotient();
            int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), rational.denominator());
            if (half > 0 || (half == 0 && UnsignedWords.bit(coefficient, 0))) {
                coefficient = add(coefficient, new long[] {1});
            }
            digits = decimalDigits(coefficient);
            if (scientific && digits.length() > fractionDigits + 1L) {
                // 尾数从 9.99... 进位到 10.00...，末位为零，可精确移走并增加指数。
                digits = digits.substring(0, digits.length() - 1);
                exponent++;
            }
        }
        return scientific ? scientificFixed(negative, digits, fractionDigits, exponent)
                : plainFixed(negative, digits, appendedZeros, fractionDigits);
    }

    private record Rational(long[] numerator, long[] denominator) {
    }

    /** magnitude * 2^scale * 10^decimalShift 的精确有理数；本方法的缩放参数均受 Binary256 格式约束。 */
    private static Rational scaledDecimal(long[] magnitude, int scale, int decimalShift) {
        long[] numerator = magnitude, denominator = new long[] {1};
        if (decimalShift >= 0) {
            numerator = UnsignedWords.multiply(numerator, powerOfFive(decimalShift));
        } else {
            denominator = powerOfFive(-decimalShift);
        }
        int binaryShift = scale + decimalShift;
        if (binaryShift >= 0) {
            numerator = UnsignedWords.left(numerator, binaryShift);
        } else {
            denominator = UnsignedWords.left(denominator, -binaryShift);
        }
        return new Rational(numerator, denominator);
    }

    /** 真正的 floor(log10(x))，不使用可能因最短表示进位而改变的十进制指数。 */
    private static int decimalExponent(long[] magnitude, int scale) {
        int binaryExponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        int exponent = (int) Math.floorDiv(binaryExponent * 78913L, 262144);
        Rational rational = scaledDecimal(magnitude, scale, -exponent);
        long[] numerator = rational.numerator(), denominator = rational.denominator();
        while (UnsignedWords.compare(numerator, denominator) < 0) {
            numerator = multiplyAdd(numerator, 10, 0);
            exponent--;
        }
        long[] tenDenominator = multiplyAdd(denominator, 10, 0);
        while (UnsignedWords.compare(numerator, tenDenominator) >= 0) {
            denominator = tenDenominator;
            tenDenominator = multiplyAdd(denominator, 10, 0);
            exponent++;
        }
        return exponent;
    }

    private static long[] right(long[] value, int distance) {
        int whole = distance / 64, bits = distance & 63;
        long[] result = new long[value.length - whole];
        for (int i = 0; i < result.length; i++) {
            result[i] = value[whole + i] >>> bits;
            if (bits != 0) {
                result[i] |= UnsignedWords.word(value, whole + i + 1) << (64 - bits);
            }
        }
        return UnsignedWords.trim(result);
    }

    /** 独占副本按 10^9 分组除法转为十进制，不修改输入或缓存中的五次幂。 */
    private static String decimalDigits(long[] value) {
        int bits = UnsignedWords.bitLength(value);
        if (bits == 0) {
            return "0";
        }
        long[] work = Arrays.copyOf(value, value.length);
        char[] chars = new char[(int) (bits * 30103L / 100000) + 1];
        int position = chars.length, length = work.length;
        while (length != 0) {
            long remainder = 0;
            for (int i = length - 1; i >= 0; i--) {
                // remainder < 10^9，拼接 32 位后仍在有符号 long 的正数范围内。
                long pair = (remainder << 32) | (work[i] >>> 32);
                long high = pair / 1_000_000_000;
                remainder = pair % 1_000_000_000;
                pair = (remainder << 32) | (work[i] & 0xffff_ffffL);
                work[i] = (high << 32) | (pair / 1_000_000_000);
                remainder = pair % 1_000_000_000;
            }
            while (length != 0 && work[length - 1] == 0) {
                length--;
            }
            int count = 0;
            do {
                chars[--position] = (char) ('0' + remainder % 10);
                remainder /= 10;
                count++;
            } while (length == 0 ? remainder != 0 : count < 9);
        }
        return new String(chars, position, chars.length - position);
    }

    private static int checkLength(long length) {
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Formatted decimal exceeds the maximum string length");
        }
        return (int) length;
    }

    private static String plainFixed(boolean negative, String digits, int appendedZeros, int fractionDigits) {
        long totalDigits = digits.length() + (long) appendedZeros;
        int length = checkLength((negative ? 1L : 0L) + Math.max(totalDigits, fractionDigits + 1L)
                + (fractionDigits == 0 ? 0 : 1));
        char[] result = new char[length];
        Arrays.fill(result, '0');
        int offset = negative ? 1 : 0;
        if (negative) {
            result[0] = '-';
        }
        int point = Math.toIntExact(totalDigits - fractionDigits);
        if (fractionDigits == 0) {
            digits.getChars(0, digits.length(), result, offset);
        } else if (point <= 0) {
            result[offset + 1] = '.';
            digits.getChars(0, digits.length(), result, offset + 2 - point);
        } else {
            result[offset + point] = '.';
            int integerDigits = Math.min(point, digits.length());
            digits.getChars(0, integerDigits, result, offset);
            digits.getChars(integerDigits, digits.length(), result, offset + point + 1);
        }
        return new String(result);
    }

    private static String scientificFixed(boolean negative, String digits, int fractionDigits, int exponent) {
        String exponentText = Integer.toString(exponent);
        int length = checkLength((negative ? 1L : 0L) + 1 + (fractionDigits == 0 ? 0L : 1L + fractionDigits)
                + 1 + exponentText.length());
        char[] result = new char[length];
        Arrays.fill(result, '0');
        int offset = negative ? 1 : 0;
        if (negative) {
            result[0] = '-';
        }
        result[offset] = digits.charAt(0);
        if (fractionDigits != 0) {
            result[offset + 1] = '.';
            digits.getChars(1, digits.length(), result, offset + 2);
        }
        int exponentPosition = length - exponentText.length() - 1;
        result[exponentPosition] = 'e';
        exponentText.getChars(0, exponentText.length(), result, exponentPosition + 1);
        return new String(result);
    }

    /**
     * Dragon 式区间生成。r/s 为当前归一化数值，lower/s、upper/s 为两侧中点距离。
     * 用 4M 和 q-2 的共同位权表示边界，正常二次幂（最小正常数除外）的下侧间距减半。
     * 最大有限值的上侧使用虚拟后继 2^(emax+1)，恰好覆盖舍入到无穷的边界。
     */
    private static Decimal shortest(Binary256 value) {
        long[] magnitude = UnsignedWords.trim(new long[] {value.getSignificandLong(3),
                value.getSignificandLong(2), value.getSignificandLong(1), value.getSignificandLong(0)});
        boolean inclusive = (magnitude[0] & 1) == 0;
        int bits = UnsignedWords.bitLength(magnitude);
        long scale = value.scaleExponent();
        int binaryExponent = Math.toIntExact(scale + bits - 1);
        boolean lowerCloser = value.isNormal() && binaryExponent > -262142
                && !UnsignedWords.anyLow(magnitude, 236);
        long[] r = UnsignedWords.left(magnitude, 2);
        long[] lower = new long[] {lowerCloser ? 1 : 2}, upper = new long[] {2};
        long[] s = new long[] {1};
        // 此有理数只估算十进制数量级，最终完全由整数比较校正，不参与舍入决定。
        int exponent = (int) Math.floorDiv(binaryExponent * 78913L, 262144);
        if (exponent >= 0) {
            s = powerOfFive(exponent);
        } else {
            long[] power = powerOfFive(-exponent);
            r = UnsignedWords.multiply(r, power);
            lower = UnsignedWords.multiply(lower, power);
            upper = UnsignedWords.multiply(upper, power);
        }
        int shift = Math.toIntExact(scale - 2 - exponent);
        if (shift >= 0) {
            r = UnsignedWords.left(r, shift);
            lower = UnsignedWords.left(lower, shift);
            upper = UnsignedWords.left(upper, shift);
        } else {
            s = UnsignedWords.left(s, -shift);
        }
        while (UnsignedWords.compare(r, s) < 0) {
            r = multiplyAdd(r, 10, 0);
            lower = multiplyAdd(lower, 10, 0);
            upper = multiplyAdd(upper, 10, 0);
            exponent--;
        }
        long[] tenS = multiplyAdd(s, 10, 0);
        while (UnsignedWords.compare(r, tenS) >= 0) {
            s = tenS;
            tenS = multiplyAdd(s, 10, 0);
            exponent++;
        }
        StringBuilder digits = new StringBuilder(73);
        for (;;) {
            // 归一化后的整数商只有一位；最多九次减法，避免为每位构造规范化除法工作区。
            int digit = 0;
            while (UnsignedWords.compare(r, s) >= 0) {
                r = UnsignedWords.subtract(r, s);
                digit++;
            }
            digits.append((char) ('0' + digit));
            int lowComparison = UnsignedWords.compare(r, lower);
            int highComparison = UnsignedWords.compare(add(r, upper), s);
            boolean low = lowComparison < 0 || (inclusive && lowComparison == 0);
            boolean high = highComparison > 0 || (inclusive && highComparison == 0);
            if (low || high) {
                int half = UnsignedWords.compare(UnsignedWords.left(r, 1), s);
                if (high && (!low || half > 0 || (half == 0 && (digit & 1) != 0))) {
                    int i = digits.length() - 1;
                    while (i >= 0 && digits.charAt(i) == '9') {
                        digits.setCharAt(i--, '0');
                    }
                    if (i < 0) {
                        digits.setCharAt(0, '1');
                        exponent++;
                    } else {
                        digits.setCharAt(i, (char) (digits.charAt(i) + 1));
                    }
                }
                while (digits.length() > 1 && digits.charAt(digits.length() - 1) == '0') {
                    digits.setLength(digits.length() - 1);
                }
                return new Decimal(digits.toString(), exponent);
            }
            r = multiplyAdd(r, 10, 0);
            lower = multiplyAdd(lower, 10, 0);
            upper = multiplyAdd(upper, 10, 0);
        }
    }

    /** factor 和 addend 均在 0 至 10^9 之间，输入只读。 */
    private static long[] multiplyAdd(long[] value, int factor, int addend) {
        long[] result = new long[value.length + 1];
        long carry = addend;
        for (int i = 0; i < value.length; i++) {
            long low = value[i] * factor;
            long next = low + carry;
            carry = Math.unsignedMultiplyHigh(value[i], factor) + (Long.compareUnsigned(next, low) < 0 ? 1 : 0);
            result[i] = next;
        }
        result[value.length] = carry;
        return UnsignedWords.trim(result);
    }

    private static long[] add(long[] a, long[] b) {
        long[] result = new long[Math.max(a.length, b.length) + 1];
        long carry = 0;
        for (int i = 0; i < result.length - 1; i++) {
            long av = UnsignedWords.word(a, i), bv = UnsignedWords.word(b, i);
            long sum = av + bv, next = sum + carry;
            carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            result[i] = next;
        }
        result[result.length - 1] = carry;
        return UnsignedWords.trim(result);
    }

    private static long[] powerOfFive(int exponent) {
        long[] result = new long[] {1};
        long[] factor = FIVE_POWERS[0];
        for (int bit = 0; exponent != 0; bit++, exponent >>>= 1) {
            if (bit < FIVE_POWERS.length) {
                factor = cachedPower(bit);
            } else {
                factor = UnsignedWords.multiply(factor, factor);
            }
            if ((exponent & 1) != 0) {
                result = UnsignedWords.multiply(result, factor);
            }
        }
        return result;
    }

    private static synchronized long[] cachedPower(int bit) {
        for (int i = 1; i <= bit; i++) {
            if (FIVE_POWERS[i] == null) {
                FIVE_POWERS[i] = UnsignedWords.multiply(FIVE_POWERS[i - 1], FIVE_POWERS[i - 1]);
            }
        }
        return FIVE_POWERS[bit];
    }

    private static Binary256 zero(boolean negative) {
        return negative ? Binary256.NEGATIVE_ZERO : Binary256.ZERO;
    }

    private static Binary256 infinity(boolean negative) {
        return negative ? Binary256.NEGATIVE_INFINITY : Binary256.POSITIVE_INFINITY;
    }
}
