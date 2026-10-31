package com.inf.long2.binaries;

import java.util.Arrays;

/** 无符号幅值的最低 long 组位于索引零，零使用空数组；运算不修改传入数组。 */
final class UnsignedWords {
    private UnsignedWords() {
    }

    record Division(long[] quotient, long[] remainder) {
    }

    record Root(long[] value, long[] remainder) {
    }

    static long[] trim(long[] value) {
        int length = length(value);
        return length == value.length ? value : Arrays.copyOf(value, length);
    }

    private static int length(long[] value) {
        int length = value.length;
        while (length > 0 && value[length - 1] == 0) {
            length--;
        }
        return length;
    }

    static long word(long[] value, int index) {
        return index < value.length ? value[index] : 0L;
    }

    static int bitLength(long[] value) {
        int length = length(value);
        return length == 0 ? 0 : (length - 1) * 64 + 64 - Long.numberOfLeadingZeros(value[length - 1]);
    }

    static boolean bit(long[] value, long index) {
        return index >= 0 && index / 64 < value.length
                && (value[(int) (index / 64)] & (1L << (index & 63))) != 0;
    }

    /** 检查低 count 位，不按数组宽度对 count 取模。 */
    static boolean anyLow(long[] value, long count) {
        if (count <= 0) {
            return false;
        }
        int whole = (int) Math.min(count / 64, value.length);
        for (int i = 0; i < whole; i++) {
            if (value[i] != 0) {
                return true;
            }
        }
        int bits = (int) (count & 63);
        return whole < value.length && bits != 0 && (value[whole] & ((1L << bits) - 1)) != 0;
    }

    static int compare(long[] a, long[] b) {
        int al = length(a), bl = length(b);
        if (al != bl) {
            return Integer.compare(al, bl);
        }
        for (int i = al - 1; i >= 0; i--) {
            int result = Long.compareUnsigned(a[i], b[i]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    static long[] add(long[] a, long[] b) {
        int length = Math.max(a.length, b.length);
        long[] result = new long[length + 1];
        long carry = 0;
        for (int i = 0; i < length; i++) {
            long av = word(a, i), bv = word(b, i);
            long sum = av + bv;
            long next = sum + carry;
            carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            result[i] = next;
        }
        result[length] = carry;
        return trim(result);
    }

    /** 调用方保证 a 不小于 b，结果不包含额外的符号扩展位。 */
    static long[] subtract(long[] a, long[] b) {
        long[] result = new long[a.length];
        long borrow = 0;
        for (int i = 0; i < a.length; i++) {
            long bv = word(b, i);
            long difference = a[i] - bv;
            result[i] = difference - borrow;
            borrow = Long.compareUnsigned(a[i], bv) < 0 || Long.compareUnsigned(difference, borrow) < 0 ? 1 : 0;
        }
        return trim(result);
    }

    static long[] left(long[] value, int count) {
        if (count == 0 || value.length == 0) {
            return value;
        }
        int whole = count / 64, bits = count & 63;
        long[] result = new long[value.length + whole + (bits == 0 ? 0 : 1)];
        for (int i = 0; i < value.length; i++) {
            result[i + whole] |= value[i] << bits;
            if (bits != 0) {
                result[i + whole + 1] |= value[i] >>> (64 - bits);
            }
        }
        return trim(result);
    }

    static long[] right(long[] value, long count) {
        if (count == 0) {
            return value;
        }
        if (count >= bitLength(value)) {
            return new long[0];
        }
        int whole = (int) (count / 64), bits = (int) (count & 63);
        long[] result = new long[value.length - whole];
        for (int i = 0; i < result.length; i++) {
            result[i] = value[i + whole] >>> bits;
            if (bits != 0 && i + whole + 1 < value.length) {
                result[i] |= value[i + whole + 1] << (64 - bits);
            }
        }
        return trim(result);
    }

    static long[] multiply(long[] a, long[] b) {
        long[] result = new long[a.length + b.length];
        for (int i = 0; i < a.length; i++) {
            long carry = 0;
            for (int j = 0; j < b.length; j++) {
                long low = a[i] * b[j];
                long high = Math.unsignedMultiplyHigh(a[i], b[j]);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + b.length] = carry;
        }
        return trim(result);
    }

    static Division divide(long[] numerator, long[] denominator) {
        if (bitLength(denominator) == 0) {
            throw new ArithmeticException("Unsigned division by zero");
        }
        int distance = bitLength(numerator) - bitLength(denominator);
        if (distance < 0) {
            return new Division(new long[0], numerator);
        }
        long[] quotient = new long[distance / 64 + 1];
        long[] remainder = numerator;
        long[] shifted = left(denominator, distance).clone();
        for (int i = distance; i >= 0; i--) {
            if (compare(remainder, shifted) >= 0) {
                remainder = subtract(remainder, shifted);
                quotient[i / 64] |= 1L << (i & 63);
            }
            // 仅移动本方法拥有的工作区，保留数组长度以免逐位分配。
            for (int j = 0; j < shifted.length; j++) {
                shifted[j] = (shifted[j] >>> 1)
                        | (j + 1 < shifted.length ? shifted[j + 1] << 63 : 0);
            }
        }
        return new Division(trim(quotient), trim(remainder));
    }

    static Root sqrt(long[] value) {
        long[] root = new long[0], remainder = new long[0];
        for (int pair = (bitLength(value) + 1) / 2 - 1; pair >= 0; pair--) {
            long digit = (bit(value, 2L * pair + 1) ? 2L : 0L) | (bit(value, 2L * pair) ? 1L : 0L);
            remainder = add(left(remainder, 2), new long[] {digit});
            long[] trial = add(left(root, 2), new long[] {1});
            boolean take = compare(remainder, trial) >= 0;
            if (take) {
                remainder = subtract(remainder, trial);
            }
            root = add(left(root, 1), new long[] {take ? 1L : 0L});
        }
        return new Root(trim(root), trim(remainder));
    }

    /** 模重复平方只保留余数，避免为大指数构造完整的二次幂。 */
    static long[] powerOfTwoMod(long exponent, long[] modulus) {
        long[] result = new long[] {1};
        long[] factor = divide(new long[] {2}, modulus).remainder();
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = divide(multiply(result, factor), modulus).remainder();
            }
            exponent >>>= 1;
            if (exponent != 0) {
                factor = divide(multiply(factor, factor), modulus).remainder();
            }
        }
        return result;
    }
}
