package com.inf.long2.ints;

import com.inf.long2.Int;

/**
 * 不可变的 256-bit 有符号定宽整数，采用 two's complement 表示。
 * 使用四个 long 按大端序排列，算术运算溢出时按 2^256 回绕。
 * 支持位运算、移位、加减乘、比较及多种字符串格式转换。
 */
public final class Int256 implements Int<Int256> {
    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Int256(long l3) {
        this(l3 >> 63, l3 >> 63, l3 >> 63, l3);
    }

    private Int256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /**
     * 将 double 转换为 256-bit 有符号整数，小数向零截断。
     * 有限值溢出时回绕，NaN 转为零，Infinity 转为对应极值。
     */
    private Int256(double value) {
        long raw = Double.doubleToRawLongBits(value);
        int exponent = (int) ((raw >>> 52) & 0x7ffL);
        long fraction = raw & 0x000f_ffff_ffff_ffffL;
        boolean negative = raw < 0L;

        long a0 = 0L, a1 = 0L, a2 = 0L, a3 = 0L;

        if (exponent == 0x7ff) {
            // NaN -> 0，Infinity -> 对应极值
            if (fraction == 0L) {
                if (negative) {
                    a0 = Long.MIN_VALUE;
                } else {
                    a0 = Long.MAX_VALUE;
                    a1 = a2 = a3 = -1L;
                }
            }
        } else if (exponent >= 1023) {
            // Normal double 的 53-bit significand
            long significand = (1L << 52) | fraction;
            int e = exponent - 1023;

            if (e <= 52) {
                // 向零截断小数部分
                a3 = significand >>> (52 - e);
            } else {
                int shift = e - 52;

                // 超过 256-bit 的部分直接丢弃
                if (shift < 256) {
                    int n = shift & 63;
                    long low = significand << n;
                    long high = n == 0
                            ? 0L
                            : significand >>> (64 - n);

                    switch (shift >>> 6) {
                        case 0 -> {
                            a3 = low;
                            a2 = high;
                        }
                        case 1 -> {
                            a2 = low;
                            a1 = high;
                        }
                        case 2 -> {
                            a1 = low;
                            a0 = high;
                        }
                        case 3 -> a0 = low;
                    }
                }
            }

            // 对负数进行 256-bit two's complement 取负
            if (negative) {
                long b3 = a3, b2 = a2, b1 = a1;

                a3 = -b3;
                a2 = ~b2 + (b3 == 0L ? 1L : 0L);
                a1 = ~b1 + ((b3 | b2) == 0L ? 1L : 0L);
                a0 = ~a0 + ((b3 | b2 | b1) == 0L ? 1L : 0L);
            }
        }

        this(a0, a1, a2, a3);
    }

    public static final Int256 MAX_VALUE = new Int256(
            Long.MAX_VALUE, -1L, -1L, -1L);

    public static final Int256 MIN_VALUE = new Int256(
            Long.MIN_VALUE, 0L, 0L, 0L);

    public static final Int256 ZERO = new Int256(0L);
    public static final Int256 ONE = new Int256(1L);

    public static Int256 of(long last) {
        return new Int256(last);
    }

    public static Int256 of(long l0, long l1, long l2, long l3) {
        return new Int256(l0, l1, l2, l3);
    }

    public static Int256 of(long[] longs) {
        if (longs.length != 4) {
            throw new IllegalArgumentException("parameter 'longs' must contain exactly 4 elements");
        }
        return new Int256(longs[0], longs[1], longs[2], longs[3]);
    }

    public static Int256 of(double value) {
        return new Int256(value);
    }

    /** 转换为 double，按照 IEEE 754 最近偶数规则舍入。 */
    @Override
    public double toDouble() {
        boolean negative = l0 < 0L;

        long a0 = l0;
        long a1 = l1;
        long a2 = l2;
        long a3 = l3;

        // 计算无符号绝对值
        if (negative) {
            a3 = -l3;
            a2 = ~l2 + (l3 == 0L ? 1L : 0L);
            a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
            a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        }

        if ((a0 | a1 | a2 | a3) == 0L) {
            return 0.0;
        }

        long top;
        long next;
        long rest;
        int offset;

        if (a0 != 0L) {
            top = a0;
            next = a1;
            rest = a2 | a3;
            offset = 192;
        } else if (a1 != 0L) {
            top = a1;
            next = a2;
            rest = a3;
            offset = 128;
        } else if (a2 != 0L) {
            top = a2;
            next = a3;
            rest = 0L;
            offset = 64;
        } else {
            top = a3;
            next = 0L;
            rest = 0L;
            offset = 0;
        }

        // 将最高有效 bit 对齐至 bit 63
        int leading = Long.numberOfLeadingZeros(top);
        long normalized = top << leading;

        if (leading != 0) {
            normalized |= next >>> (64 - leading);
        }

        // 提取 53-bit 有效数字
        long significand = normalized >>> 11;

        // 最近偶数舍入
        boolean guard = (normalized & (1L << 10)) != 0L;
        boolean sticky = (normalized & 0x3ffL) != 0L
                || (next << leading) != 0L
                || rest != 0L;

        if (guard && (sticky || (significand & 1L) != 0L)) {
            significand++;
        }

        double result = Math.scalb(
                (double) significand, offset + 11 - leading);

        return negative ? -result : result;
    }

    /** 返回有符号十进制字符串。 */
    @Override
    public String toString() {
        if (isZero()) {
            return "0";
        }

        boolean negative = l0 < 0L;
        long a0 = l0, a1 = l1, a2 = l2, a3 = l3;

        // 将负数转换为无符号绝对值
        if (negative) {
            a3 = -l3;
            a2 = ~l2 + (l3 == 0L ? 1L : 0L);
            a1 = ~l1 + ((l3 | l2) == 0L ? 1L : 0L);
            a0 = ~l0 + ((l3 | l2 | l1) == 0L ? 1L : 0L);
        }

        final long base = 1_000_000_000L;
        char[] chars = new char[82];
        int pos = chars.length;

        do {
            long rem = 0L;

            // l0
            long x = (rem << 32) | (a0 >>> 32);
            long q = x / base;
            rem = x % base;
            long high = q << 32;

            x = (rem << 32) | (a0 & 0xffffffffL);
            q = x / base;
            rem = x % base;
            a0 = high | q;

            // l1
            x = (rem << 32) | (a1 >>> 32);
            q = x / base;
            rem = x % base;
            high = q << 32;

            x = (rem << 32) | (a1 & 0xffffffffL);
            q = x / base;
            rem = x % base;
            a1 = high | q;

            // l2
            x = (rem << 32) | (a2 >>> 32);
            q = x / base;
            rem = x % base;
            high = q << 32;

            x = (rem << 32) | (a2 & 0xffffffffL);
            q = x / base;
            rem = x % base;
            a2 = high | q;

            // l3
            x = (rem << 32) | (a3 >>> 32);
            q = x / base;
            rem = x % base;
            high = q << 32;

            x = (rem << 32) | (a3 & 0xffffffffL);
            q = x / base;
            rem = x % base;
            a3 = high | q;

            // 输出 9 位十进制数字
            for (int i = 0; i < 9; i++) {
                chars[--pos] = (char) ('0' + rem % 10);
                rem /= 10;
            }
        } while ((a0 | a1 | a2 | a3) != 0L);

        // 去除前导零并补充符号
        while (chars[pos] == '0') {
            pos++;
        }
        if (negative) {
            chars[--pos] = '-';
        }

        return new String(chars, pos, chars.length - pos);
    }

    /** 解析有符号十进制字符串，超出 256-bit 时回绕。 */
    public static Int256 parseInt(String value) {
        if (value == null || value.isEmpty()) {
            throw new NumberFormatException("Empty decimal value");
        }

        int index = 0;
        boolean negative = false;
        char first = value.charAt(0);

        if (first == '+' || first == '-') {
            negative = first == '-';
            index = 1;
        }

        if (index == value.length()) {
            throw new NumberFormatException("Missing decimal digits");
        }

        long a0 = 0L, a1 = 0L, a2 = 0L, a3 = 0L;

        for (; index < value.length(); index++) {
            int digit = value.charAt(index) - '0';

            if (digit < 0 || digit > 9) {
                throw new NumberFormatException(
                        "Invalid decimal value: " + value);
            }

            // a3
            long p = (a3 & 0xffffffffL) * 10L + digit;
            long q = (a3 >>> 32) * 10L + (p >>> 32);
            a3 = (q << 32) | (p & 0xffffffffL);
            long carry = q >>> 32;

            // a2
            p = (a2 & 0xffffffffL) * 10L + carry;
            q = (a2 >>> 32) * 10L + (p >>> 32);
            a2 = (q << 32) | (p & 0xffffffffL);
            carry = q >>> 32;

            // a1
            p = (a1 & 0xffffffffL) * 10L + carry;
            q = (a1 >>> 32) * 10L + (p >>> 32);
            a1 = (q << 32) | (p & 0xffffffffL);
            carry = q >>> 32;

            // a0，超出 256-bit 的进位直接丢弃
            p = (a0 & 0xffffffffL) * 10L + carry;
            q = (a0 >>> 32) * 10L + (p >>> 32);
            a0 = (q << 32) | (p & 0xffffffffL);
        }

        // 对负数进行 256-bit two's complement 取负
        if (negative) {
            long b3 = a3, b2 = a2, b1 = a1;

            a3 = -b3;
            a2 = ~b2 + (b3 == 0L ? 1L : 0L);
            a1 = ~b1 + ((b3 | b2) == 0L ? 1L : 0L);
            a0 = ~a0 + ((b3 | b2 | b1) == 0L ? 1L : 0L);
        }

        return new Int256(a0, a1, a2, a3);
    }

    @Override
    public String toHexString() {
        return "0x%016x%016x%016x%016x".formatted(
                l0, l1, l2, l3);
    }

    /**
     * 必须带有 0x 前缀，必须使用小写字母。
     * 位数不足时补齐前导零，位数溢出时截断高位。
     * 按原始位模式解释。位全空时不合法。
     */
    public static Int256 parseHexInt(String value) {
        if (!value.startsWith("0x")) {
            throw new NumberFormatException("Missing 0x prefix");
        }

        String hex = value.substring(2);
        if (hex.isEmpty()) {
            throw new NumberFormatException("Empty hexadecimal value");
        }

        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                throw new NumberFormatException("Invalid hexadecimal digit: " + c);
            }
        }

        if (hex.length() > 64) {
            hex = hex.substring(hex.length() - 64);
        }

        hex = "0".repeat(64 - hex.length()) + hex;

        return new Int256(
                Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16, 32), 16),
                Long.parseUnsignedLong(hex.substring(32, 48), 16),
                Long.parseUnsignedLong(hex.substring(48, 64), 16));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Int256 other)) {
            return false;
        }
        return l0 == other.l0
                && l1 == other.l1
                && l2 == other.l2
                && l3 == other.l3;
    }

    @Override
    public int hashCode() {
        int h = 1;
        h = 31 * h + Long.hashCode(l0);
        h = 31 * h + Long.hashCode(l1);
        h = 31 * h + Long.hashCode(l2);
        h = 31 * h + Long.hashCode(l3);
        return h;
    }

    @Override
    public Int256 and(Int256 other) {
        return new Int256(
                l0 & other.l0,
                l1 & other.l1,
                l2 & other.l2,
                l3 & other.l3);
    }

    @Override
    public Int256 or(Int256 other) {
        return new Int256(
                l0 | other.l0,
                l1 | other.l1,
                l2 | other.l2,
                l3 | other.l3);
    }

    @Override
    public Int256 not() {
        return new Int256(~l0, ~l1, ~l2, ~l3);
    }

    @Override
    public Int256 xor(Int256 other) {
        return new Int256(
                l0 ^ other.l0,
                l1 ^ other.l1,
                l2 ^ other.l2,
                l3 ^ other.l3);
    }

    /**
     * bits 为正时左移，为负时算术右移。
     * 超出 256-bit 范围的位被丢弃，右移时进行符号扩展。
     */
    @Override
    public Int256 shift(int bits) {
        if (bits == 0) {
            return this;
        }
        if (bits >= 256) {
            return new Int256(0L);
        }
        if (bits <= -256) {
            return new Int256(l0 >> 63);
        }

        long a = l0;
        long b = l1;
        long c = l2;
        long d = l3;

        if (bits > 0) {
            switch (bits >>> 6) {
                case 1 -> {
                    a = b;
                    b = c;
                    c = d;
                    d = 0L;
                }
                case 2 -> {
                    a = c;
                    b = d;
                    c = d = 0L;
                }
                case 3 -> {
                    a = d;
                    b = c = d = 0L;
                }
            }

            int n = bits & 63;
            if (n != 0) {
                return new Int256(
                        (a << n) | (b >>> (64 - n)),
                        (b << n) | (c >>> (64 - n)),
                        (c << n) | (d >>> (64 - n)),
                        d << n);
            }
        } else {
            int distance = -bits;
            long sign = a >> 63;

            switch (distance >>> 6) {
                case 1 -> {
                    d = c;
                    c = b;
                    b = a;
                    a = sign;
                }
                case 2 -> {
                    d = b;
                    c = a;
                    a = b = sign;
                }
                case 3 -> {
                    d = a;
                    a = b = c = sign;
                }
            }

            int n = distance & 63;
            if (n != 0) {
                return new Int256(
                        a >> n,
                        (b >>> n) | (a << (64 - n)),
                        (c >>> n) | (b << (64 - n)),
                        (d >>> n) | (c << (64 - n)));
            }
        }

        return new Int256(a, b, c, d);
    }

    @Override
    public Int256 add(Int256 other) {
        long r3 = l3 + other.l3;
        long carry = Long.compareUnsigned(r3, l3) < 0 ? 1L : 0L;

        long r2 = l2 + other.l2 + carry;
        carry = Long.compareUnsigned(r2, l2) < 0
                || (carry != 0 && r2 == l2) ? 1L : 0L;

        long r1 = l1 + other.l1 + carry;
        carry = Long.compareUnsigned(r1, l1) < 0
                || (carry != 0 && r1 == l1) ? 1L : 0L;

        long r0 = l0 + other.l0 + carry;

        return new Int256(r0, r1, r2, r3);
    }

    @Override
    public Int256 subtract(Int256 other) {
        long r3 = l3 - other.l3;
        long borrow = Long.compareUnsigned(l3, other.l3) < 0 ? 1L : 0L;

        long r2 = l2 - other.l2 - borrow;
        borrow = Long.compareUnsigned(l2, other.l2) < 0
                || (borrow != 0 && l2 == other.l2) ? 1L : 0L;

        long r1 = l1 - other.l1 - borrow;
        borrow = Long.compareUnsigned(l1, other.l1) < 0
                || (borrow != 0 && l1 == other.l1) ? 1L : 0L;

        long r0 = l0 - other.l0 - borrow;

        return new Int256(r0, r1, r2, r3);
    }

    @Override
    public Int256 negate() {
        return ZERO.subtract(this);
    }

    @Override
    public Int256 successor() {
        return ONE.add(this);
    }

    @Override
    public Int256 multiply(Int256 other) {
        long r3 = l3 * other.l3;

        // 第二个 64-bit
        long r2 = Math.unsignedMultiplyHigh(l3, other.l3);
        long carry = 0L;
        long v = l3 * other.l2;
        long t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;

        v = l2 * other.l3;
        t = r2 + v;
        if (Long.compareUnsigned(t, r2) < 0)
            carry++;
        r2 = t;

        // 第三个 64-bit
        long r1 = carry;
        carry = 0L;

        v = Math.unsignedMultiplyHigh(l3, other.l2);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = Math.unsignedMultiplyHigh(l2, other.l3);
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l3 * other.l1;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l2 * other.l2;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        v = l1 * other.l3;
        t = r1 + v;
        if (Long.compareUnsigned(t, r1) < 0)
            carry++;
        r1 = t;

        // 最高 64-bit，最终进位直接丢弃
        long r0 = carry
                + Math.unsignedMultiplyHigh(l3, other.l1)
                + Math.unsignedMultiplyHigh(l2, other.l2)
                + Math.unsignedMultiplyHigh(l1, other.l3)
                + l3 * other.l0
                + l2 * other.l1
                + l1 * other.l2
                + l0 * other.l3;

        return new Int256(r0, r1, r2, r3);
    }

    @Override
    public int compareTo(Int256 other) {
        int cmp = Long.compare(l0, other.l0);
        if (cmp != 0)
            return cmp;

        cmp = Long.compareUnsigned(l1, other.l1);
        if (cmp != 0)
            return cmp;

        cmp = Long.compareUnsigned(l2, other.l2);
        if (cmp != 0)
            return cmp;

        return Long.compareUnsigned(l3, other.l3);
    }

    @Override
    public int signum() {
        if (isNegative())
            return -1;
        return isZero() ? 0 : 1;
    }

    @Override
    public boolean isZero() {
        return (l0 | l1 | l2 | l3) == 0L;
    }

    @Override
    public boolean isNegative() {
        return l0 < 0L;
    }

    @Override
    public long getLong(int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /**
     * bit 0 为最低有效位，bit 255 为符号位。
     */
    @Override
    public boolean testBit(int index) {
        if (index < 0 || index >= 256) {
            throw new IndexOutOfBoundsException(index);
        }

        long word = switch (index >>> 6) {
            case 0 -> l3;
            case 1 -> l2;
            case 2 -> l1;
            default -> l0;
        };

        return (word & (1L << (index & 63))) != 0L;
    }

    /** 返回去除冗余符号扩展位后的有效位数，不计符号位。 */
    @Override
    public int bitLength() {
        boolean negative = l0 < 0L;

        long a0 = negative ? ~l0 : l0;
        long a1 = negative ? ~l1 : l1;
        long a2 = negative ? ~l2 : l2;
        long a3 = negative ? ~l3 : l3;

        if (a0 != 0L)
            return 256 - Long.numberOfLeadingZeros(a0);
        if (a1 != 0L)
            return 192 - Long.numberOfLeadingZeros(a1);
        if (a2 != 0L)
            return 128 - Long.numberOfLeadingZeros(a2);
        return 64 - Long.numberOfLeadingZeros(a3);
    }

    /** 返回完整 256-bit 位模式中值为 1 的 bit 数量。 */
    @Override
    public int bitCount() {
        return Long.bitCount(l0)
                + Long.bitCount(l1)
                + Long.bitCount(l2)
                + Long.bitCount(l3);
    }
}
