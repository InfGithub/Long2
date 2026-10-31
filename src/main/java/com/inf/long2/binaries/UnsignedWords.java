package com.inf.long2.binaries;

import java.util.Arrays;

/**
 * 无符号幅值的最低 long 组位于索引零，零使用空数组。
 * 返回结果的运算不修改输入；原地辅助方法只接受调用方独占的工作区。
 */
final class UnsignedWords {
    private static final long DIGIT_MASK = 0xffff_ffffL;
    private static final long DIGIT_BASE = 0x1_0000_0000L;

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

    /** value 是调用方独占的规范化幅值，只有全部分组进位时才扩展容量。 */
    static long[] incrementInPlace(long[] value) {
        for (int i = 0; i < value.length; i++) {
            if (++value[i] != 0) {
                return value;
            }
        }
        long[] result = Arrays.copyOf(value, value.length + 1);
        result[value.length] = 1;
        return result;
    }

    /** 保留低 count 位，结果可能借用输入，但不会修改输入。 */
    static long[] lowBits(long[] value, int count) {
        if (count == 0) {
            return new long[0];
        }
        if (count >= bitLength(value)) {
            return value;
        }
        long[] result = Arrays.copyOf(value, (count + 63) / 64);
        int bits = count & 63;
        if (bits != 0) {
            result[result.length - 1] &= (1L << bits) - 1;
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
        int distance = divisionDistance(numerator, denominator);
        if (distance < 0) {
            return new Division(new long[0], numerator);
        }
        if (distance == 0) {
            int comparison = compare(numerator, denominator);
            if (comparison < 0) {
                return new Division(new long[0], numerator);
            }
            if (comparison == 0) {
                return new Division(new long[] {1}, new long[0]);
            }
        }
        long[] quotient = new long[distance / 64 + 1];
        long[] remainder = divideRemainder(numerator, denominator, distance, quotient);
        return new Division(trim(quotient), remainder);
    }

    /** 与 divide 共用分组试商，省去完整商及 Division 的构造。 */
    static long[] remainder(long[] numerator, long[] denominator) {
        int distance = divisionDistance(numerator, denominator);
        if (distance < 0) {
            return numerator;
        }
        if (distance == 0) {
            int comparison = compare(numerator, denominator);
            if (comparison < 0) {
                return numerator;
            }
            if (comparison == 0) {
                return new long[0];
            }
        }
        return divideRemainder(numerator, denominator, distance, null);
    }

    private static int divisionDistance(long[] numerator, long[] denominator) {
        int denominatorBits = bitLength(denominator);
        if (denominatorBits == 0) {
            throw new ArithmeticException("Unsigned division by zero");
        }
        return bitLength(numerator) - denominatorBits;
    }

    /** 规范化后最高除数组至少为 2^31，各轮以高两组估商，再用完整乘减恢复小于除数的余数。 */
    private static long[] divideRemainder(long[] numerator, long[] denominator, int distance, long[] quotient) {
        int denominatorBits = bitLength(denominator);
        int divisorLength = (int) ((denominatorBits + 31L) / 32);
        int dividendLength = (int) ((denominatorBits + (long) distance + 31) / 32);
        if (divisorLength == 1) {
            return divideOneDigit(numerator, denominator[0] & DIGIT_MASK, dividendLength, quotient);
        }
        int shift = Integer.numberOfLeadingZeros(digit(denominator, divisorLength - 1));
        int[] divisor = normalizedDigits(denominator, divisorLength, shift, false);
        int[] dividend = normalizedDigits(numerator, dividendLength, shift, true);
        divideNormalized(dividend, divisor, dividendLength, quotient);
        return remainderWords(dividend, divisorLength, shift);
    }

    /** 除数最高组的 bit 31 为一，分子多一组进位；工作区独占，非空 quotient 须预先清零。 */
    private static void divideNormalized(int[] dividend, int[] divisor, int dividendLength, long[] quotient) {
        int divisorLength = divisor.length;
        long top = divisor[divisorLength - 1] & DIGIT_MASK;
        long second = divisor[divisorLength - 2] & DIGIT_MASK;
        for (int j = dividendLength - divisorLength; j >= 0; j--) {
            long high = dividend[j + divisorLength] & DIGIT_MASK;
            long next = dividend[j + divisorLength - 1] & DIGIT_MASK;
            long estimate, residual;
            if (high == top) {
                // 两组估商可能达到基数，先钳制到单组上界；residual 允许多出一位。
                estimate = DIGIT_MASK;
                residual = next + top;
            } else {
                long pair = (high << 32) | next;
                estimate = Long.divideUnsigned(pair, top);
                residual = pair - estimate * top;
            }
            // residual 达到基数后，右侧已超过任意两组乘积，不再构造会丢失最高位的 pair。
            long lower = dividend[j + divisorLength - 2] & DIGIT_MASK;
            while (residual < DIGIT_BASE && Long.compareUnsigned(estimate * second, (residual << 32) | lower) > 0) {
                estimate--;
                residual += top;
            }
            if (estimate == 0) {
                continue;
            }
            if (subtractProduct(dividend, j, divisor, estimate)) {
                // 高两组的修正未观察更低分组，完整乘减为负时还需将试商减一并回加除数。
                estimate--;
                addBack(dividend, j, divisor);
            }
            if (quotient != null && estimate != 0) {
                quotient[j >>> 1] |= estimate << ((j & 1) * 32);
            }
        }
    }

    private static long[] divideOneDigit(long[] numerator, long divisor, int length, long[] quotient) {
        long remainder = 0;
        for (int i = length - 1; i >= 0; i--) {
            long pair = (remainder << 32) | (digit(numerator, i) & DIGIT_MASK);
            long current = Long.divideUnsigned(pair, divisor);
            remainder = pair - current * divisor;
            if (quotient != null && current != 0) {
                quotient[i >>> 1] |= current << ((i & 1) * 32);
            }
        }
        return remainder == 0 ? new long[0] : new long[] {remainder};
    }

    private static int digit(long[] value, int index) {
        return (int) (value[index >>> 1] >>> ((index & 1) * 32));
    }

    /** 左移使最高除数组的 bit 31 为一，分子额外保留一组进位；输入始终只读。 */
    private static int[] normalizedDigits(long[] value, int length, int shift, boolean extra) {
        int[] result = new int[length + (extra ? 1 : 0)];
        normalizedDigits(value, length, shift, result);
        return result;
    }

    private static void normalizedDigits(long[] value, int length, int shift, int[] result) {
        long carry = 0;
        for (int i = 0; i < length; i++) {
            long current = ((digit(value, i) & DIGIT_MASK) << shift) | carry;
            result[i] = (int) current;
            carry = current >>> 32;
        }
        if (result.length > length) {
            result[length] = (int) carry;
        }
    }

    /** 在独占分子窗口内减去完整试商乘积，返回最高组是否发生借位。 */
    private static boolean subtractProduct(int[] dividend, int offset, int[] divisor, long estimate) {
        long borrow = 0;
        for (int i = 0; i < divisor.length; i++) {
            long product = estimate * (divisor[i] & DIGIT_MASK) + borrow;
            long low = product & DIGIT_MASK, current = dividend[offset + i] & DIGIT_MASK;
            dividend[offset + i] = (int) (current - low);
            borrow = (product >>> 32) + (current < low ? 1 : 0);
        }
        long high = dividend[offset + divisor.length] & DIGIT_MASK;
        dividend[offset + divisor.length] = (int) (high - borrow);
        return high < borrow;
    }

    private static void addBack(int[] dividend, int offset, int[] divisor) {
        long carry = 0;
        for (int i = 0; i < divisor.length; i++) {
            long sum = (dividend[offset + i] & DIGIT_MASK) + (divisor[i] & DIGIT_MASK) + carry;
            dividend[offset + i] = (int) sum;
            carry = sum >>> 32;
        }
        dividend[offset + divisor.length] += (int) carry;
    }

    private static long[] remainderWords(int[] dividend, int length, int shift) {
        long[] result = new long[(length + 1) / 2];
        remainderWords(dividend, length, shift, result);
        return trim(result);
    }

    /** 完整覆盖 result，包括最后半组和未使用高组，允许复用先前保存商的数组。 */
    private static void remainderWords(int[] dividend, int length, int shift, long[] result) {
        for (int i = 0; i < length; i++) {
            long current = (dividend[i] & DIGIT_MASK) >>> shift;
            if (shift != 0 && i + 1 < length) {
                current |= (dividend[i + 1] & DIGIT_MASK) << (32 - shift);
            }
            if ((i & 1) == 0) {
                result[i >>> 1] = current & DIGIT_MASK;
            } else {
                result[i >>> 1] |= (current & DIGIT_MASK) << 32;
            }
        }
        Arrays.fill(result, (length + 1) / 2, result.length, 0L);
    }

    /**
     * 求四个低组在前的幅值分组左移 shift 后的精确整数根和余数。
     * 调用方保证幅值为 1 至 237 位、缩放结果为 473 或 474 位。
     * 高 63 或 64 位前缀给出严格上界初值，相邻商判定使迭代最多执行四次分组除法。
     */
    static Root sqrtScaled(long w0, long w1, long w2, long w3, int shift) {
        long prefix = scaledWord(w0, w1, w2, w3, 0, shift - 410);
        long seed = sqrtUnsignedLong(prefix) + 1;
        long[] estimate = new long[4], quotient = new long[4];
        int[] dividend = new int[16], divisor = new int[8];
        // seed 最多 33 位，左移 205 位后仍装得进四个 long，不能截去初值的第 238 位。
        estimate[3] = seed << 13;
        for (int iteration = 0; ; iteration++) {
            assert iteration < 4 : "Newton square root exceeded its division bound";
            int normalization = Integer.numberOfLeadingZeros(digit(estimate, 7));
            normalizedDigits(estimate, 8, normalization, divisor);
            for (int i = 0; i < 8; i++) {
                long current = scaledWord(w0, w1, w2, w3, i, shift + normalization);
                dividend[i * 2] = (int) current;
                dividend[i * 2 + 1] = (int) (current >>> 32);
            }
            Arrays.fill(quotient, 0L);
            divideNormalized(dividend, divisor, 15, quotient);
            if (compare(quotient, estimate) >= 0) {
                // 此时 estimate 就是整数根，商与根之差最多为二；直接恢复 N-S²，无须另算平方。
                int multiple = (int) (quotient[0] - estimate[0]);
                assert multiple >= 0 && multiple <= 2;
                remainderWords(dividend, 8, normalization, quotient);
                if (multiple != 0) {
                    addMultipleInPlace(quotient, estimate, multiple);
                }
                return new Root(estimate, trim(quotient));
            }
            if (oneBelow(quotient, estimate)) {
                // 商为 X-1 时，S=X-1 且 N-S²=S+U，可直接结束可能出现的相邻值循环。
                for (int i = 0; i < estimate.length; i++) {
                    if (estimate[i]-- != 0) {
                        break;
                    }
                }
                remainderWords(dividend, 8, normalization, quotient);
                addMultipleInPlace(quotient, estimate, 1);
                return new Root(estimate, trim(quotient));
            }
            addMultipleInPlace(estimate, quotient, 1);
            for (int i = 0; i < estimate.length - 1; i++) {
                estimate[i] = (estimate[i] >>> 1) | (estimate[i + 1] << 63);
            }
            estimate[estimate.length - 1] >>>= 1;
        }
    }

    /** 按无符号位模式读取前缀，余数与试减值最多 34 位，局部 long 不会溢出。 */
    private static long sqrtUnsignedLong(long value) {
        long root = 0, remainder = 0;
        for (int pair = 31; pair >= 0; pair--) {
            remainder = (remainder << 2) | ((value >>> (pair * 2)) & 3);
            long trial = (root << 2) | 1;
            root <<= 1;
            if (remainder >= trial) {
                remainder -= trial;
                root |= 1;
            }
        }
        return root;
    }

    /** 不物化被开方数；shift 为正时左移，为负时右移，幅值范围外补零。 */
    private static long scaledWord(long w0, long w1, long w2, long w3, int index, int shift) {
        int group = index - (shift >> 6), bits = shift & 63;
        long low = fourWord(w0, w1, w2, w3, group) << bits;
        return bits == 0 ? low : low | (fourWord(w0, w1, w2, w3, group - 1) >>> (64 - bits));
    }

    private static long fourWord(long w0, long w1, long w2, long w3, int index) {
        return switch (index) {
            case 0 -> w0;
            case 1 -> w1;
            case 2 -> w2;
            case 3 -> w3;
            default -> 0;
        };
    }

    /** 两个数组等长且 lower 加一不会超出容量，检查相邻值时不修改商。 */
    private static boolean oneBelow(long[] lower, long[] upper) {
        long carry = 1;
        for (int i = 0; i < lower.length; i++) {
            long next = lower[i] + carry;
            if (next != upper[i]) {
                return false;
            }
            carry = carry != 0 && next == 0 ? 1 : 0;
        }
        return true;
    }

    /** target 为等长的独占工作区，容量足以容纳精确和；multiple 只能为一或二，value 只读。 */
    private static void addMultipleInPlace(long[] target, long[] value, int multiple) {
        int shift = multiple - 1;
        long carry = 0, shiftedCarry = 0;
        for (int i = 0; i < target.length; i++) {
            long current = value[i], addend = (current << shift) | shiftedCarry;
            shiftedCarry = shift == 0 ? 0 : current >>> 63;
            long sum = target[i] + addend;
            long next = sum + carry;
            carry = Long.compareUnsigned(sum, target[i]) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            target[i] = next;
        }
    }

    /** 模重复平方只保留余数，避免为大指数构造完整的二次幂。 */
    static long[] powerOfTwoMod(long exponent, long[] modulus) {
        long[] result = remainder(new long[] {1}, modulus);
        if (result.length == 0 || exponent == 0) {
            return result;
        }
        long[] factor = remainder(new long[] {2}, modulus);
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = remainder(multiply(result, factor), modulus);
            }
            exponent >>>= 1;
            if (exponent != 0) {
                factor = remainder(multiply(factor, factor), modulus);
            }
        }
        return result;
    }
}
