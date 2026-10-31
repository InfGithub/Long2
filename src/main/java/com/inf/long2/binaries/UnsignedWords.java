package com.inf.long2.binaries;

import java.util.Arrays;

/**
 * 无符号幅值的最低 long 组位于索引零，规范化幅值的零使用空数组；分段工作区按各方法约定保留高零组。
 * 返回结果的运算不修改输入；原地辅助方法只接受调用方独占的工作区。
 */
final class UnsignedWords {
    private static final long DIGIT_MASK = 0xffff_ffffL;
    private static final long DIGIT_BASE = 0x1_0000_0000L;
    private static final int SQRT_WORDS = 4;

    private UnsignedWords() {
    }

    record Division(long[] quotient, long[] remainder) {
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
        divideNormalized(dividend, divisor, dividendLength, quotient, 0);
        return remainderWords(dividend, divisorLength, shift);
    }

    /** 除数最高组的 bit 31 为一，分子多一组进位；工作区独占，非空 quotient 从 offset 开始的商区须预先清零。 */
    private static void divideNormalized(int[] dividend, int[] divisor, int dividendLength, long[] quotient, int offset) {
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
                quotient[offset + (j >>> 1)] |= estimate << ((j & 1) * 32);
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
        remainderWords(dividend, length, shift, result, 0);
        return trim(result);
    }

    /** 完整覆盖 result 从 offset 到结尾的区间，包括最后半组和未使用高组；保留 offset 之前的分组。 */
    private static void remainderWords(int[] dividend, int length, int shift, long[] result, int offset) {
        for (int i = 0; i < length; i++) {
            long current = (dividend[i] & DIGIT_MASK) >>> shift;
            if (shift != 0 && i + 1 < length) {
                current |= (dividend[i + 1] & DIGIT_MASK) << (32 - shift);
            }
            if ((i & 1) == 0) {
                result[offset + (i >>> 1)] = current & DIGIT_MASK;
            } else {
                result[offset + (i >>> 1)] |= (current & DIGIT_MASK) << 32;
            }
        }
        Arrays.fill(result, offset + (length + 1) / 2, result.length, 0L);
    }

    /**
     * 求四个低组在前的幅值分组左移 shift 后的精确整数根和余数。
     * 调用方保证幅值为 1 至 237 位、缩放结果 N 为 473 或 474 位。
     * workspace 为调用方独占的八组数组，完整覆盖前四组为未舍入整数根 S、后四组为余数 R，各区间均低组在前。
     * 返回 R 与 S 的比较结果，满足 N=S²+R 且 0<=R<2S+1；零余数保留四个零组，允许复用含旧数据的工作区。
     * 高 127 或 128 位前缀给出严格上界初值，首轮利用低零位缩减除数，随后最多执行两次分组除法。
     * 初值误差不超过 2^173，两次 Newton 更新后的正误差分别小于 2^109 和 2^-19，向下取整不会低于整数根。
     * 第二次更新若为整数根加一，精确平方根必超过该整数根加二分之一，故下一轮相邻商直接结束。
     */
    static int sqrtScaled(long w0, long w1, long w2, long w3, int shift, long[] workspace) {
        assert workspace.length == 2 * SQRT_WORDS;
        workspace[0] = 0;
        workspace[1] = 0;
        long prefixLow = scaledWord(w0, w1, w2, w3, 0, shift - 346);
        long prefixHigh = scaledWord(w0, w1, w2, w3, 1, shift - 346);
        long prefixRoot = sqrtUnsigned128(prefixLow, prefixHigh);
        if (prefixRoot * prefixRoot == prefixLow && Math.unsignedMultiplyHigh(prefixRoot, prefixRoot) == prefixHigh) {
            // shift 至少为 236，前缀之外最多剩余 110 个幅值 bit，检查前两组即可确认整个尾部为零。
            int tailBits = 346 - shift;
            if (tailBits <= 0 || (tailBits < 64 ? (w0 & ((1L << tailBits) - 1)) == 0
                    : w0 == 0 && (tailBits == 64 || (w1 & ((1L << (tailBits - 64)) - 1)) == 0))) {
                workspace[2] = prefixRoot << 45;
                workspace[3] = prefixRoot >>> 19;
                Arrays.fill(workspace, SQRT_WORDS, workspace.length, 0L);
                return -1;
            }
        }
        long seed = prefixRoot + 1;
        // seed 的零位模式表示 2^64，左移 173 位后须保留初值的第 238 位。
        workspace[2] = seed << 45;
        workspace[3] = seed == 0 ? 1L << 45 : seed >>> 19;
        long residual = divideSqrtSeed64(w0, w1, w2, w3, shift, seed, workspace);
        assert compareSqrtBlocks(workspace) < 0;
        if (oneBelow(workspace)) {
            // 初值严格大于精确根，商为 X-1 时已经是整数根；原除法余数为 residual<<173。
            System.arraycopy(workspace, SQRT_WORDS, workspace, 0, SQRT_WORDS);
            workspace[4] = 0;
            workspace[5] = 0;
            workspace[6] = residual << 45;
            workspace[7] = residual >>> 19;
            addMultipleInPlace(workspace, SQRT_WORDS, 0, 1);
            return compareSqrtBlocks(workspace);
        }
        averageInPlace(workspace);
        int[] dividend = new int[16], divisor = new int[8];
        for (int iteration = 1; ; iteration++) {
            assert iteration < 3 : "Newton square root exceeded its division bound";
            int normalization = Integer.numberOfLeadingZeros(digit(workspace, 7));
            normalizedDigits(workspace, 8, normalization, divisor);
            for (int i = 0; i < 8; i++) {
                long current = scaledWord(w0, w1, w2, w3, i, shift + normalization);
                dividend[i * 2] = (int) current;
                dividend[i * 2 + 1] = (int) (current >>> 32);
            }
            Arrays.fill(workspace, SQRT_WORDS, workspace.length, 0L);
            divideNormalized(dividend, divisor, 15, workspace, SQRT_WORDS);
            if (compareSqrtBlocks(workspace) >= 0) {
                // 此时前四组估值就是整数根，商与根之差最多为二；直接恢复 N-S²，无须另算平方。
                int multiple = (int) (workspace[SQRT_WORDS] - workspace[0]);
                assert multiple >= 0 && multiple <= 2;
                remainderWords(dividend, 8, normalization, workspace, SQRT_WORDS);
                if (multiple != 0) {
                    addMultipleInPlace(workspace, SQRT_WORDS, 0, multiple);
                }
                return compareSqrtBlocks(workspace);
            }
            if (oneBelow(workspace)) {
                // 商为 X-1 时，S=X-1 且 N-S²=S+U，可直接结束可能出现的相邻值循环。
                for (int i = 0; i < SQRT_WORDS; i++) {
                    if (workspace[i]-- != 0) {
                        break;
                    }
                }
                remainderWords(dividend, 8, normalization, workspace, SQRT_WORDS);
                addMultipleInPlace(workspace, SQRT_WORDS, 0, 1);
                return compareSqrtBlocks(workspace);
            }
            averageInPlace(workspace);
        }
    }

    /**
     * 输入为 127 或 128 位无符号数，返回其完整 64 位整数根。
     * 高半部整数根构造的上界误差不超过 2^32，一次 Newton 更新后候选最多高一，完整平方比较只需修正一次。
     */
    static long sqrtUnsigned128(long low, long high) {
        long upper = sqrtUnsignedLong(high) + 1;
        long candidate;
        if (upper == DIGIT_BASE) {
            // 初值为 2^64，平均值仍小于 2^64，按最高位和其余位直接构造，避免初值回绕。
            candidate = Long.MIN_VALUE | (high >>> 1);
        } else {
            long quotientHigh = Long.divideUnsigned(high, upper);
            long remainder = high - quotientHigh * upper;
            long pair = (remainder << 32) | (low >>> 32);
            long quotient = (quotientHigh << 32) | Long.divideUnsigned(pair, upper);
            long initial = upper << 32;
            // 公共位与差异位分开求平均，不构造可能需要 65 位的和。
            candidate = (initial & quotient) + ((initial ^ quotient) >>> 1);
        }
        long squareLow = candidate * candidate, squareHigh = Math.unsignedMultiplyHigh(candidate, candidate);
        if (Long.compareUnsigned(squareHigh, high) > 0
                || (squareHigh == high && Long.compareUnsigned(squareLow, low) > 0)) {
            candidate--;
        }
        return candidate;
    }

    /**
     * seed 为 N 高 127 或 128 位整数根加一，零位模式表示 2^64，其余值按无符号 long 解释。
     * 幅值和 shift 遵守 sqrtScaled 的位宽约束；完整覆盖 workspace 后四组为商，返回 N>>>173 除以 seed 的无符号余数。
     */
    private static long divideSqrtSeed64(long w0, long w1, long w2, long w3, int shift, long seed, long[] workspace) {
        if ((seed & DIGIT_MASK) == 0) {
            // 低 32 位为零时，除数仍可写成单组种子左移 205 位；余数换回 173 位缩放。
            return divideSqrtSeed(w0, w1, w2, w3, shift, seed == 0 ? DIGIT_BASE : seed >>> 32, workspace) << 32;
        }
        long top = seed >>> 32, bottom = seed & DIGIT_MASK;
        int reducedShift = shift - 173;
        long remainder = scaledWord(w0, w1, w2, w3, 4, reducedShift);
        assert Long.compareUnsigned(remainder, seed) < 0;
        // 每组商都已校正到精确值，余数小于 2^64，保留乘减的低 64 位即可恢复全部余数。
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            long current = scaledWord(w0, w1, w2, w3, i, reducedShift);
            long high = divideTwoDigitQuotient(remainder, current >>> 32, top, bottom);
            remainder = ((remainder << 32) | (current >>> 32)) - high * seed;
            long low = divideTwoDigitQuotient(remainder, current & DIGIT_MASK, top, bottom);
            remainder = ((remainder << 32) | (current & DIGIT_MASK)) - low * seed;
            workspace[SQRT_WORDS + i] = (high << 32) | low;
        }
        return remainder;
    }

    /**
     * 双组除数最高 bit 为一，remainder 按无符号 long 解释且小于除数，digit 为一个 32 位分组。
     * 返回 remainder 左移 32 位并补入 digit 后的单组商；低组比较覆盖完整除数，最多修正两次，无须回加。
     */
    private static long divideTwoDigitQuotient(long remainder, long digit, long top, long bottom) {
        long quotient, residual;
        if ((remainder >>> 32) == top) {
            quotient = DIGIT_MASK;
            residual = (remainder & DIGIT_MASK) + top;
        } else {
            quotient = Long.divideUnsigned(remainder, top);
            residual = remainder - quotient * top;
        }
        while (residual < DIGIT_BASE && Long.compareUnsigned(quotient * bottom, (residual << 32) | digit) > 0) {
            quotient--;
            residual += top;
        }
        return quotient;
    }

    /**
     * 幅值不超过 237 位且缩放结果 N 为 473 或 474 位，seed 为 N 高 63 或 64 位整数根加一。
     * 完整覆盖 workspace 后四组为商，返回 N>>>205 除以 seed 的余数；N 的低 205 位为零，原余数只需左移 205 位。
     */
    private static long divideSqrtSeed(long w0, long w1, long w2, long w3, int shift, long seed, long[] workspace) {
        if (seed == DIGIT_BASE) {
            // 该种子要求 N 为 474 位，故 shift 至少为 237，除以 2^237 的余数必为零。
            for (int i = 0; i < SQRT_WORDS; i++) {
                workspace[SQRT_WORDS + i] = scaledWord(w0, w1, w2, w3, i, shift - 237);
            }
            return 0;
        }
        int reducedShift = shift - 205;
        long remainder = scaledWord(w0, w1, w2, w3, 4, reducedShift);
        assert remainder < seed;
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            long current = scaledWord(w0, w1, w2, w3, i, reducedShift);
            long pair = (remainder << 32) | (current >>> 32);
            long high = Long.divideUnsigned(pair, seed);
            remainder = pair - high * seed;
            pair = (remainder << 32) | (current & DIGIT_MASK);
            long low = Long.divideUnsigned(pair, seed);
            remainder = pair - low * seed;
            workspace[SQRT_WORDS + i] = (high << 32) | low;
        }
        return remainder;
    }

    /** 两区间的精确和不超出四组容量，仅覆盖前四组估值，平均值向下取整。 */
    private static void averageInPlace(long[] workspace) {
        addMultipleInPlace(workspace, 0, SQRT_WORDS, 1);
        for (int i = 0; i < SQRT_WORDS - 1; i++) {
            workspace[i] = (workspace[i] >>> 1) | (workspace[i + 1] << 63);
        }
        workspace[SQRT_WORDS - 1] >>>= 1;
    }

    /** 输入按无符号位模式解释，返回不大于精确平方根的最大整数；余数与试减值最多 34 位，均装得进 long。 */
    static long sqrtUnsignedLong(long value) {
        long root = 0, remainder = 0;
        for (int pair = (63 - Long.numberOfLeadingZeros(value)) >> 1; pair >= 0; pair--) {
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

    /** 比较两个独立的四组无符号区间，后四组在迭代期间是商，结束后是余数。 */
    private static int compareSqrtBlocks(long[] workspace) {
        for (int i = SQRT_WORDS - 1; i >= 0; i--) {
            int comparison = Long.compareUnsigned(workspace[SQRT_WORDS + i], workspace[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    /** 后四组商加一不会超出容量，检查其是否恰好等于前四组估值，不修改商。 */
    private static boolean oneBelow(long[] workspace) {
        long carry = 1;
        for (int i = 0; i < SQRT_WORDS; i++) {
            long next = workspace[SQRT_WORDS + i] + carry;
            if (next != workspace[i]) {
                return false;
            }
            carry = carry != 0 && next == 0 ? 1 : 0;
        }
        return true;
    }

    /** 两个四组区间互不重叠，目标容量足以容纳精确和；multiple 只能为一或二，源区间只读。 */
    private static void addMultipleInPlace(long[] workspace, int targetOffset, int valueOffset, int multiple) {
        int shift = multiple - 1;
        long carry = 0, shiftedCarry = 0;
        for (int i = 0; i < SQRT_WORDS; i++) {
            long current = workspace[valueOffset + i], addend = (current << shift) | shiftedCarry;
            shiftedCarry = shift == 0 ? 0 : current >>> 63;
            long sum = workspace[targetOffset + i] + addend;
            long next = sum + carry;
            carry = Long.compareUnsigned(sum, workspace[targetOffset + i]) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
            workspace[targetOffset + i] = next;
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
