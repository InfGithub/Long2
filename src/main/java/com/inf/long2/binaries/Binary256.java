package com.inf.long2.binaries;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.ints.Int256;

/**
 * 256-bit 二进制浮点数，以四个 long 按大端序保存原始编码。
 * bit 255 为符号，bit 236 至 bit 254 为 19 位偏置指数，其余 236 位为小数字段。
 * 正常数补入隐含位后具有 237 位精度，指数偏置为 262143。
 * 加减与 FMA 的精确对齐均使用有界工作区，FMA 对齐幅值最多使用 12 个 long，不随指数差扩张。
 */
public final class Binary256 implements Binary<Binary256> {
    private static final int BIAS = 262143;
    private static final int QMIN = -262378;
    private static final long FRACTION_MASK = 0x0000_0fff_ffff_ffffL;
    private static final long HIDDEN_BIT = 0x0000_1000_0000_0000L;
    private static final long INFINITY_TOP = 0x7fff_f000_0000_0000L;
    private static final long NAN_TOP = 0x7fff_f800_0000_0000L;
    private static final int FMA_ALIGNMENT_WORDS = 12;

    /**
     * 237 位有效数字，正常指数范围为 -262142 至 262143。
     *
     * @see Binary#FORMAT
     */
    public static final Format FORMAT = new Format(237, -262142, 262143);

    /**
     * 正零。
     *
     * @see Binary#ZERO
     */
    public static final Binary256 ZERO = new Binary256(0, 0, 0, 0);
    /**
     * 负零。
     *
     * @see Binary#NEGATIVE_ZERO
     */
    public static final Binary256 NEGATIVE_ZERO = new Binary256(Long.MIN_VALUE, 0, 0, 0);
    /**
     * 精确的一。
     *
     * @see Binary#ONE
     */
    public static final Binary256 ONE = new Binary256(0x3fff_f000_0000_0000L, 0, 0, 0);
    /**
     * 最小非零正数，数值为 {@code 2^-262378}。
     *
     * @see Binary#MIN_VALUE
     */
    public static final Binary256 MIN_VALUE = new Binary256(0, 0, 0, 1);
    /**
     * 最小正常正数，数值为 {@code 2^-262142}。
     *
     * @see Binary#MIN_NORMAL
     */
    public static final Binary256 MIN_NORMAL = new Binary256(HIDDEN_BIT, 0, 0, 0);
    /**
     * 最大有限正数。
     *
     * @see Binary#MAX_VALUE
     */
    public static final Binary256 MAX_VALUE = new Binary256(0x7fff_efff_ffff_ffffL, -1, -1, -1);
    /**
     * 正无穷。
     *
     * @see Binary#POSITIVE_INFINITY
     */
    public static final Binary256 POSITIVE_INFINITY = new Binary256(INFINITY_TOP, 0, 0, 0);
    /**
     * 负无穷。
     *
     * @see Binary#NEGATIVE_INFINITY
     */
    public static final Binary256 NEGATIVE_INFINITY = new Binary256(INFINITY_TOP | Long.MIN_VALUE, 0, 0, 0);
    /**
     * 正的 quiet NaN，数值运算产生的 NaN 统一使用该编码。
     *
     * @see Binary#NaN
     */
    public static final Binary256 NaN = new Binary256(NAN_TOP, 0, 0, 0);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Binary256(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    /** 按大端序保存完整原始编码，包括 NaN 的符号、payload 和 signaling 位，不执行数值转换。 */
    public static Binary256 fromRawBits(long l0, long l1, long l2, long l3) {
        return new Binary256(l0, l1, l2, l3);
    }

    /**
     * 返回原始编码的一个 long 分组，索引 0 为最高组，3 为最低组。
     *
     * @throws IndexOutOfBoundsException 索引小于 0 或大于 3
     */
    public long getRawLong(int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    /**
     * 从 long 精确构造，包含 Long.MIN_VALUE。
     *
     * @see Binary#of(long)
     */
    public static Binary256 of(long value) {
        // Long.MIN_VALUE 取负后的位模式仍是正确的无符号幅值。
        return fromUnsignedLong(value < 0, value < 0 ? -value : value, 0);
    }

    /**
     * 精确接收 float 的有限数值，保留零和无穷的符号。
     *
     * @see Binary#of(float)
     */
    public static Binary256 of(float value) {
        int bits = Float.floatToRawIntBits(value);
        return fromPrimitive(bits < 0, (bits >>> 23) & 255, bits & 0x7fffffL, 23, 127, 255);
    }

    /**
     * 精确接收 double 的有限数值，保留零和无穷的符号。
     *
     * @see Binary#of(double)
     */
    public static Binary256 of(double value) {
        long bits = Double.doubleToRawLongBits(value);
        return fromPrimitive(bits < 0, (int) ((bits >>> 52) & 2047), bits & 0xfffffffffffffL, 52, 1023, 2047);
    }

    private static Binary256 fromPrimitive(boolean negative, int exponent, long fraction,
            int fractionBits, int bias, int infinityExponent) {
        if (exponent == infinityExponent) {
            return fraction == 0 ? infinity(negative) : NaN;
        }
        long magnitude = exponent == 0 ? fraction : fraction | (1L << fractionBits);
        long scale = (exponent == 0 ? 1L : exponent) - bias - fractionBits;
        return fromUnsignedLong(negative, magnitude, scale);
    }

    /** 原始无符号 long 幅值的精确扩展，调用方保证非零值落在 Binary256 的正常范围内。 */
    private static Binary256 fromUnsignedLong(boolean negative, long magnitude, long scale) {
        if (magnitude == 0) {
            return zero(negative);
        }
        int length = 64 - Long.numberOfLeadingZeros(magnitude);
        long top = length <= 45 ? magnitude << (45 - length) : magnitude >>> (length - 45);
        long next = length <= 45 ? 0 : magnitude << (109 - length);
        long encoded = scale + length - 1 + BIAS;
        return new Binary256((negative ? Long.MIN_VALUE : 0) | (encoded << 44) | (top & FRACTION_MASK), next, 0, 0);
    }

    /**
     * 从完整整数直接舍入，最小负数的幅值按原始位模式读取。
     *
     * @see Binary#of(Int)
     */
    public static Binary256 of(Int<?> value) {
        Objects.requireNonNull(value);
        if (value instanceof Int256 fixed) {
            return fromInt256(fixed);
        }
        boolean negative = value.isNegative();
        Int<?> magnitude = negative ? value.negate() : value;
        long length = (long) magnitude.bitLength() + (magnitude.isNegative() ? 1 : 0);
        if (length > FORMAT.maxExponent() + 1L) {
            return infinity(negative);
        }
        int kept = (int) Math.min(length, 239);
        int dropped = (int) (length - kept);
        long[] words = new long[(kept + 63) / 64];
        int ones = 0;
        for (int i = 0; i < kept; i++) {
            if (magnitude.testBit(dropped + i)) {
                words[i / 64] |= 1L << (i & 63);
                ones++;
            }
        }
        // 整数不会下溢；高 239 位恰为偶数一侧中点时，完整 bitCount 才影响舍入方向。
        if (dropped != 0 && (words[0] & 7) == 2 && magnitude.bitCount() > ones) {
            words[0] |= 1;
        }
        return fromDyadic(negative, words, dropped);
    }

    private static Binary256 fromInt256(Int256 value) {
        boolean negative = value.isNegative();
        long m0 = value.getLong(0), m1 = value.getLong(1), m2 = value.getLong(2), m3 = value.getLong(3);
        if (negative) {
            // 低组全零时继续传播补码取负的进位，最小负数也得到正确的无符号幅值。
            m3 = -m3;
            m2 = ~m2 + (m3 == 0 ? 1 : 0);
            m1 = ~m1 + ((m3 | m2) == 0 ? 1 : 0);
            m0 = ~m0 + ((m3 | m2 | m1) == 0 ? 1 : 0);
        }
        if ((m0 | m1 | m2) == 0) {
            return fromUnsignedLong(negative, m3, 0);
        }
        int discarded = bitLength(m3, m2, m1, m0) - 237;
        long low = shiftedWord(m3, m2, m1, m0, 0, discarded);
        // 整数幅值最多 256 位，舍弃部分最多 19 位，guard 与 sticky 均在最低分组内。
        boolean increment = discarded > 0 && (m3 & (1L << (discarded - 1))) != 0
                && ((low & 1) != 0 || (m3 & ((1L << (discarded - 1)) - 1)) != 0);
        return packRounded(negative, low, shiftedWord(m3, m2, m1, m0, 1, discarded),
                shiftedWord(m3, m2, m1, m0, 2, discarded), shiftedWord(m3, m2, m1, m0, 3, discarded),
                discarded, increment);
    }

    /**
     * 从源格式直接舍入，读取有限值分解，不经过 double。
     *
     * @see Binary#of(Binary)
     */
    public static Binary256 of(Binary<?> value) {
        Objects.requireNonNull(value);
        if (value.isNaN()) {
            return NaN;
        }
        boolean negative = value.signBit();
        if (value.isInfinite()) {
            return infinity(negative);
        }
        if (value.isZero()) {
            return zero(negative);
        }
        if (value instanceof Binary256 same) {
            return same;
        }
        Format sourceFormat = value.format();
        int precision = sourceFormat.precision();
        long scale = value.scaleExponent();
        long maximumExponent = scale + precision - 1L;
        if (maximumExponent < QMIN - 1L) {
            return zero(negative);
        }
        // 次正规源的 scale+p-1 等于 emin，且 emin 不大于零；越过目标上界时源必为正常数。
        if (maximumExponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        int groups = (int) ((precision + 63L) / 64);
        long guard = QMIN - 1L - scale;
        int lastLeadingGroup = guard > 0 ? groups - 1 - (int) (guard / 64) : groups - 1;
        int first = 0;
        long top;
        do {
            top = value.getSignificandLong(first++);
            // 半个 MIN_VALUE 对应 bit 及以上均为零时，更低分组不能改变舍入为零的结果。
            if (top == 0 && first > lastLeadingGroup) {
                return zero(negative);
            }
        } while (top == 0 && first < groups);
        long length = (groups - (long) first) * 64 + 64 - Long.numberOfLeadingZeros(top);
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        int kept = (int) Math.min(length, 239);
        long dropped = length - kept;
        long[] magnitude = new long[(kept + 63) / 64];
        int last = groups - 1 - (int) (dropped / 64);
        long lastWord = top;
        for (int index = first - 1; index <= last; index++) {
            long word = index == first - 1 ? top : value.getSignificandLong(index);
            lastWord = word;
            long offset = (groups - 1L - index) * 64 - dropped;
            if (offset < 0) {
                magnitude[0] |= word >>> (int) -offset;
            } else {
                int group = (int) (offset / 64);
                int shift = (int) (offset & 63);
                magnitude[group] |= word << shift;
                if (shift != 0 && group + 1 < magnitude.length) {
                    magnitude[group + 1] |= word >>> (64 - shift);
                }
            }
        }
        long discarded = Math.max(exponent - 236, QMIN) - scale - dropped;
        // 只有已保留部分恰为偶数一侧的中点，才需读取更低尾项；其他情况的舍入方向已经确定。
        if (dropped != 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && !UnsignedWords.bit(magnitude, discarded) && !UnsignedWords.anyLow(magnitude, discarded - 1)) {
            int partial = (int) (dropped & 63);
            boolean sticky = partial != 0 && (lastWord & ((1L << partial) - 1)) != 0;
            for (int index = groups - 1; index > last && !sticky; index--) {
                sticky = value.getSignificandLong(index) != 0;
            }
            if (sticky) {
                // 两位舍入余量保证最低位低于最终 guard。
                magnitude[0] |= 1;
            }
        }
        return fromDyadic(negative, magnitude, scale + dropped);
    }

    @Override
    public Format format() {
        return FORMAT;
    }

    private int encodedExponent() {
        return (int) ((l0 >>> 44) & 0x7ffff);
    }

    private boolean emptyFraction() {
        return ((l0 & FRACTION_MASK) | l1 | l2 | l3) == 0;
    }

    @Override
    public boolean signBit() {
        return l0 < 0;
    }

    @Override
    public boolean isNaN() {
        return encodedExponent() == 0x7ffff && !emptyFraction();
    }

    @Override
    public boolean isInfinite() {
        return encodedExponent() == 0x7ffff && emptyFraction();
    }

    @Override
    public boolean isFinite() {
        return encodedExponent() != 0x7ffff;
    }

    @Override
    public boolean isZero() {
        return encodedExponent() == 0 && emptyFraction();
    }

    @Override
    public boolean isNormal() {
        return encodedExponent() != 0 && isFinite();
    }

    @Override
    public boolean isSubnormal() {
        return encodedExponent() == 0 && !emptyFraction();
    }

    private void requireFinite() {
        if (!isFinite()) {
            throw new IllegalStateException("A finite value is required");
        }
    }

    @Override
    public long scaleExponent() {
        requireFinite();
        int exponent = encodedExponent();
        return exponent == 0 ? QMIN : exponent - (long) BIAS - 236;
    }

    @Override
    public long getSignificandLong(int index) {
        requireFinite();
        return switch (index) {
            case 0 -> (l0 & FRACTION_MASK) | (isNormal() ? HIDDEN_BIT : 0);
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    private long[] magnitude() {
        return UnsignedWords.trim(new long[] {l3, l2, l1, getSignificandLong(0)});
    }

    /** 内部按低位分组读取有限幅值，超出四个分组的部分补零。 */
    private long magnitudeWord(int index) {
        return switch (index) {
            case 0 -> l3;
            case 1 -> l2;
            case 2 -> l1;
            case 3 -> (l0 & FRACTION_MASK) | (encodedExponent() == 0 ? 0 : HIDDEN_BIT);
            default -> 0;
        };
    }

    /** 调用方保证值为有限非零数，次正规数按最高非零 bit 计算实际指数。 */
    private int magnitudeExponent() {
        int encoded = encodedExponent();
        if (encoded != 0) {
            return encoded - BIAS;
        }
        long top = l0 & FRACTION_MASK;
        int length = top != 0 ? 256 - Long.numberOfLeadingZeros(top)
                : l1 != 0 ? 192 - Long.numberOfLeadingZeros(l1)
                : l2 != 0 ? 128 - Long.numberOfLeadingZeros(l2) : 64 - Long.numberOfLeadingZeros(l3);
        return QMIN + length - 1;
    }

    /** 有限非零值为二次幂时返回其指数，否则返回 Integer.MIN_VALUE。 */
    private int powerOfTwoExponent() {
        int encoded = encodedExponent();
        if (encoded != 0) {
            return emptyFraction() ? encoded - BIAS : Integer.MIN_VALUE;
        }
        for (int group = 0; group < 4; group++) {
            long word = magnitudeWord(group);
            if (word != 0) {
                if ((word & (word - 1)) != 0) {
                    return Integer.MIN_VALUE;
                }
                for (int higher = group + 1; higher < 4; higher++) {
                    if (magnitudeWord(higher) != 0) {
                        return Integer.MIN_VALUE;
                    }
                }
                return QMIN + group * 64 + Long.numberOfTrailingZeros(word);
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 两值均为有限非零数；只调整比较位权，不对接收值执行浮点舍入。 */
    private int compareScaledMagnitude(Binary256 other, int scale) {
        int exponent = magnitudeExponent(), otherExponent = other.magnitudeExponent();
        int comparison = Integer.compare(exponent + scale, otherExponent);
        if (comparison != 0) {
            return comparison;
        }
        int shift = (int) (236 - exponent + scaleExponent());
        int otherShift = (int) (236 - otherExponent + other.scaleExponent());
        for (int group = 3; group >= 0; group--) {
            comparison = Long.compareUnsigned(alignedMagnitudeWord(group, shift), other.alignedMagnitudeWord(group, otherShift));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private long alignedMagnitudeWord(int group, int shift) {
        int whole = shift >>> 6, bits = shift & 63;
        long word = magnitudeWord(group - whole) << bits;
        return bits == 0 ? word : word | (magnitudeWord(group - whole - 1) >>> (64 - bits));
    }

    /** 返回有限幅值右移后的低 64 位，distance 不小于零。 */
    private long shiftedMagnitude(int distance) {
        int group = distance >>> 6;
        int bits = distance & 63;
        long result = magnitudeWord(group) >>> bits;
        return bits == 0 ? result : result | (magnitudeWord(group + 1) << (64 - bits));
    }

    private boolean magnitudeBit(int index) {
        return (magnitudeWord(index >>> 6) & (1L << (index & 63))) != 0;
    }

    private boolean anyLowMagnitude(int count) {
        int whole = count >>> 6;
        for (int i = 0; i < whole; i++) {
            if (magnitudeWord(i) != 0) {
                return true;
            }
        }
        int bits = count & 63;
        return bits != 0 && (magnitudeWord(whole) & ((1L << bits) - 1)) != 0;
    }

    private static Binary256 zero(boolean negative) {
        return negative ? NEGATIVE_ZERO : ZERO;
    }

    private static Binary256 infinity(boolean negative) {
        return negative ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }

    /** 最终位权同时考虑精度和次正规下界，只在该位权上舍入一次。 */
    private static Binary256 fromDyadic(boolean negative, long[] magnitude, long scale) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long low = shiftedWord(magnitude, 0, discarded);
        boolean increment = discarded > 0 && UnsignedWords.bit(magnitude, discarded - 1)
                && ((low & 1) != 0 || UnsignedWords.anyLow(magnitude, discarded - 1));
        return packRounded(negative, low, shiftedWord(magnitude, 1, discarded),
                shiftedWord(magnitude, 2, discarded), shiftedWord(magnitude, 3, discarded), quantum, increment);
    }

    /** 两个低组在前的 long 保存非零完整乘积，不分配幅值数组；次正规边界仍在最终位权上舍入。 */
    private static Binary256 fromDyadic(boolean negative, long w0, long w1, long scale) {
        int length = bitLength(w0, w1, 0, 0);
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        int discarded = Math.toIntExact(quantum - scale);
        long low = shiftedWord(w0, w1, 0, 0, 0, discarded);
        boolean increment = false;
        if (discarded > 0) {
            int guard = discarded - 1;
            long word = guard < 64 ? w0 : w1;
            if ((word & (1L << (guard & 63))) != 0) {
                // 已排除低于半个 MIN_VALUE 的输入，guard 不超过 127；两侧掩码的移位均小于 64。
                increment = (low & 1) != 0 || (guard < 64
                        ? (w0 & ((1L << guard) - 1)) != 0
                        : w0 != 0 || (w1 & ((1L << (guard - 64)) - 1)) != 0);
            }
        }
        return packRounded(negative, low, shiftedWord(w0, w1, 0, 0, 1, discarded),
                shiftedWord(w0, w1, 0, 0, 2, discarded), shiftedWord(w0, w1, 0, 0, 3, discarded), quantum, increment);
    }

    /** distance 为正时右移，为负时左移；幅值保持只读，范围外的分组补零。 */
    private static long shiftedWord(long[] magnitude, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = group < 0 ? 0 : UnsignedWords.word(magnitude, group);
        if (bits == 0) {
            return low;
        }
        long high = group < -1 ? 0 : UnsignedWords.word(magnitude, group + 1);
        return (low >>> bits) | (high << (64 - bits));
    }

    private static long shiftedWord(long w0, long w1, long w2, long w3, int index, int distance) {
        int group = index + (distance >> 6), bits = distance & 63;
        long low = fourWord(w0, w1, w2, w3, group);
        return bits == 0 ? low : (low >>> bits) | (fourWord(w0, w1, w2, w3, group + 1) << (64 - bits));
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

    private static int bitLength(long w0, long w1, long w2, long w3) {
        return w3 != 0 ? 256 - Long.numberOfLeadingZeros(w3)
                : w2 != 0 ? 192 - Long.numberOfLeadingZeros(w2)
                : w1 != 0 ? 128 - Long.numberOfLeadingZeros(w1) : 64 - Long.numberOfLeadingZeros(w0);
    }

    /** 幅值最多 237 位；increment 表示最终舍入加一，额外进位归一化后再判断上溢。 */
    static Binary256 packRounded(boolean negative, long w0, long w1, long w2, long w3,
            long scale, boolean increment) {
        if (increment && ++w0 == 0 && ++w1 == 0 && ++w2 == 0) {
            w3++;
        }
        if ((w3 & (HIDDEN_BIT << 1)) != 0) {
            w0 = (w0 >>> 1) | (w1 << 63);
            w1 = (w1 >>> 1) | (w2 << 63);
            w2 = (w2 >>> 1) | (w3 << 63);
            w3 >>>= 1;
            scale++;
        }
        int length = bitLength(w0, w1, w2, w3);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = scale + length - 1;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        long encoded = exponent < FORMAT.minExponent() ? 0 : exponent + BIAS;
        return new Binary256((negative ? Long.MIN_VALUE : 0) | (encoded << 44)
                | (w3 & FRACTION_MASK), w2, w1, w0);
    }

    @Override
    public Binary256 add(Binary256 other) {
        return add(other, false);
    }

    @Override
    public Binary256 subtract(Binary256 other) {
        return add(other, true);
    }

    private Binary256 add(Binary256 other, boolean subtract) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return NaN;
        }
        boolean negative = signBit();
        boolean otherNegative = other.signBit() ^ subtract;
        if (isInfinite() || other.isInfinite()) {
            return isInfinite() && other.isInfinite() && negative != otherNegative
                    ? NaN : isInfinite() ? this : infinity(otherNegative);
        }
        if (isZero()) {
            return other.isZero() ? zero(negative && otherNegative) : subtract ? other.negate() : other;
        }
        if (other.isZero()) {
            return this;
        }
        // 两项均已按 237 位编码；指数差至少 239 时，小项严格小于较大值两侧舍入边界的距离。
        int difference = magnitudeExponent() - other.magnitudeExponent();
        if (difference >= 239) {
            return this;
        }
        if (difference <= -239) {
            return subtract ? other.negate() : other;
        }
        return sumFinite(other, negative, otherNegative);
    }

    /** 有限非零输入的实际指数差小于 239，完整对齐和连同进位最多需要 476 位。 */
    private Binary256 sumFinite(Binary256 other, boolean negative, boolean otherNegative) {
        boolean subtract = negative != otherNegative;
        int comparison = subtract ? compareScaledMagnitude(other, 0) : 1;
        if (comparison == 0) {
            return ZERO;
        }
        Binary256 a = comparison > 0 ? this : other, b = comparison > 0 ? other : this;
        long as = a.scaleExponent(), bs = b.scaleExponent(), scale = Math.min(as, bs);
        int ashift = (int) (as - scale), bshift = (int) (bs - scale);
        long[] result = new long[8];
        long carry = 0;
        for (int i = 0; i < result.length; i++) {
            long av = a.alignedMagnitudeWord(i, ashift), bv = b.alignedMagnitudeWord(i, bshift);
            if (subtract) {
                long difference = av - bv;
                result[i] = difference - carry;
                carry = Long.compareUnsigned(av, bv) < 0 || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            } else {
                long sum = av + bv;
                long next = sum + carry;
                carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i] = next;
            }
        }
        return fromDyadic(comparison > 0 ? negative : otherNegative, result, scale);
    }

    /** 两值均为有限非零数，直接读取字段并保留完整乘积，结果工作区由本次调用独占。 */
    private long[] multiplyMagnitude(Binary256 other) {
        long atop = magnitudeWord(3), btop = other.magnitudeWord(3);
        int alength = (bitLength(l3, l2, l1, atop) + 63) / 64;
        int blength = (bitLength(other.l3, other.l2, other.l1, btop) + 63) / 64;
        return multiplyMagnitude(l3, l2, l1, atop, alength, other.l3, other.l2, other.l1, btop, blength);
    }

    /** 输入分组低组在前，结果保留指定容量供 FMA 对齐时复用；位权由调用方随幅值调整。 */
    private static long[] multiplyMagnitude(long a0, long a1, long a2, long a3, int alength,
            long b0, long b1, long b2, long b3, int blength) {
        int astart = a0 != 0 ? 0 : a1 != 0 ? 1 : a2 != 0 ? 2 : 3;
        int bstart = b0 != 0 ? 0 : b1 != 0 ? 1 : b2 != 0 ? 2 : 3;
        // 低零组保留原有位权和结果容量；每行跳过的低零组不产生进位，后续零组仍须传播进位。
        long[] result = new long[alength + blength];
        for (int i = astart; i < alength; i++) {
            long av = fourWord(a0, a1, a2, a3, i), carry = 0;
            for (int j = bstart; j < blength; j++) {
                long bv = fourWord(b0, b1, b2, b3, j);
                long low = av * bv, high = Math.unsignedMultiplyHigh(av, bv);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + blength] = carry;
        }
        return result;
    }

    /** 正常幅值已按最少容量打包，组数为 1 至 4 且最低组非零，无需扫描低零组。 */
    private static long[] multiplyNormalMagnitude(long a0, long a1, long a2, long a3, int alength,
            long b0, long b1, long b2, long b3, int blength) {
        long[] result = new long[alength + blength];
        for (int i = 0; i < alength; i++) {
            long av = fourWord(a0, a1, a2, a3, i), carry = 0;
            for (int j = 0; j < blength; j++) {
                long bv = fourWord(b0, b1, b2, b3, j);
                long low = av * bv, high = Math.unsignedMultiplyHigh(av, bv);
                long sum = low + result[i + j];
                high += Long.compareUnsigned(sum, low) < 0 ? 1 : 0;
                long next = sum + carry;
                high += Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i + j] = next;
                carry = high;
            }
            result[i + blength] = carry;
        }
        return result;
    }

    /** 幅值非零；只有右移能减少 long 分组数时才压缩，避免为稠密幅值增加移位工作。 */
    private static int compactMagnitudeShift(long w0, long w1, long w2, long w3, int length) {
        if (length <= 64) {
            return 0;
        }
        int bits = ((length - 1) & 63) + 1;
        // 1L << 64 会回绕为一，减少整组所需的 64 个低零位须直接检查整个最低组。
        if (bits == 64 ? w0 != 0 : (w0 & ((1L << bits) - 1)) != 0) {
            return 0;
        }
        return w0 != 0 ? Long.numberOfTrailingZeros(w0)
                : w1 != 0 ? 64 + Long.numberOfTrailingZeros(w1)
                : w2 != 0 ? 128 + Long.numberOfTrailingZeros(w2) : 192 + Long.numberOfTrailingZeros(w3);
    }

    /** 接收有限非零输入；低零位转移到位权，不丢弃乘积中任何会影响舍入的 bit。 */
    private Binary256 multiplyFinite(Binary256 other, boolean negative) {
        long atop = magnitudeWord(3), btop = other.magnitudeWord(3);
        int abits = bitLength(l3, l2, l1, atop), bbits = bitLength(other.l3, other.l2, other.l1, btop);
        int ashift = compactMagnitudeShift(l3, l2, l1, atop, abits);
        int bshift = compactMagnitudeShift(other.l3, other.l2, other.l1, btop, bbits);
        int alength = (abits - ashift + 63) / 64, blength = (bbits - bshift + 63) / 64;
        long scale = scaleExponent() + other.scaleExponent() + ashift + bshift;
        long a0 = shiftedWord(l3, l2, l1, atop, 0, ashift);
        long b0 = shiftedWord(other.l3, other.l2, other.l1, btop, 0, bshift);
        if (alength == 1 && blength == 1) {
            return fromDyadic(negative, a0 * b0, Math.unsignedMultiplyHigh(a0, b0), scale);
        }
        long a1 = alength > 1 ? shiftedWord(l3, l2, l1, atop, 1, ashift) : 0;
        long a2 = alength > 2 ? shiftedWord(l3, l2, l1, atop, 2, ashift) : 0;
        long a3 = alength > 3 ? shiftedWord(l3, l2, l1, atop, 3, ashift) : 0;
        long b1 = blength > 1 ? shiftedWord(other.l3, other.l2, other.l1, btop, 1, bshift) : 0;
        long b2 = blength > 2 ? shiftedWord(other.l3, other.l2, other.l1, btop, 2, bshift) : 0;
        long b3 = blength > 3 ? shiftedWord(other.l3, other.l2, other.l1, btop, 3, bshift) : 0;
        return fromDyadic(negative, multiplyMagnitude(a0, a1, a2, a3, alength, b0, b1, b2, b3, blength), scale);
    }

    /** 正常幅值恰为 237 位，只检查减少分组所需的低零位，不定位全部低零位。 */
    private int normalMultiplyGroups() {
        long mask = (1L << 45) - 1;
        if ((l3 & mask) != 0) {
            return 4;
        }
        if (l3 != 0 || (l2 & mask) != 0) {
            return 3;
        }
        if (l2 != 0 || (l1 & mask) != 0) {
            return 2;
        }
        return 1;
    }

    /** 两侧为正常数；scale 是未压缩乘积的位权，各路径只移除其容量允许的低零位。 */
    private Binary256 multiplyNormal(Binary256 other, boolean negative, long scale) {
        int agroups = normalMultiplyGroups(), bgroups = other.normalMultiplyGroups();
        if (agroups != bgroups) {
            return multiplyNormalMixed(other, negative, scale, agroups, bgroups);
        }
        return switch (agroups) {
            case 1 -> multiplyNormal64(other, negative, scale);
            case 2 -> multiplyNormal128(other, negative, scale);
            case 3 -> multiplyNormal192(other, negative, scale);
            default -> multiplyNormalFull(other, negative, scale);
        };
    }

    /** 两侧各移除 173 个低零位，完整 128 位乘积无需数组。 */
    private Binary256 multiplyNormal64(Binary256 other, boolean negative, long scale) {
        long a = (l1 >>> 45) | (((l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b = (other.l1 >>> 45) | (((other.l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, a * b, Math.unsignedMultiplyHigh(a, b), scale + 346);
    }

    /** 两侧各移除 109 个低零位，以四组保存完整乘积。 */
    private Binary256 multiplyNormal128(Binary256 other, boolean negative, long scale) {
        long a0 = (l2 >>> 45) | (l1 << 19);
        long a1 = (l1 >>> 45) | (((l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b0 = (other.l2 >>> 45) | (other.l1 << 19);
        long b1 = (other.l1 >>> 45) | (((other.l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, multiplyNormalMagnitude(a0, a1, 0, 0, 2, b0, b1, 0, 0, 2), scale + 218);
    }

    /** 两侧各移除 45 个低零位，以六组保存完整乘积。 */
    private Binary256 multiplyNormal192(Binary256 other, boolean negative, long scale) {
        long a0 = (l3 >>> 45) | (l2 << 19), a1 = (l2 >>> 45) | (l1 << 19);
        long a2 = (l1 >>> 45) | (((l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        long b0 = (other.l3 >>> 45) | (other.l2 << 19), b1 = (other.l2 >>> 45) | (other.l1 << 19);
        long b2 = (other.l1 >>> 45) | (((other.l0 & FRACTION_MASK) | HIDDEN_BIT) << 19);
        return fromDyadic(negative, multiplyNormalMagnitude(a0, a1, a2, 0, 3, b0, b1, b2, 0, 3), scale + 90);
    }

    /** 两侧为正常数，直接保留原始四组幅值；scale 是未压缩乘积的位权。 */
    private Binary256 multiplyNormalFull(Binary256 other, boolean negative, long scale) {
        return fromDyadic(negative, multiplyNormalMagnitude(l3, l2, l1, (l0 & FRACTION_MASK) | HIDDEN_BIT, 4,
                other.l3, other.l2, other.l1, (other.l0 & FRACTION_MASK) | HIDDEN_BIT, 4), scale);
    }

    private Binary256 multiplyNormalMixed(Binary256 other, boolean negative, long scale, int agroups, int bgroups) {
        long adjusted = scale + (agroups == 4 ? 0 : 237 - agroups * 64) + (bgroups == 4 ? 0 : 237 - bgroups * 64);
        return fromDyadic(negative, multiplyNormalMagnitude(
                normalMultiplyWord(agroups, 0), normalMultiplyWord(agroups, 1),
                normalMultiplyWord(agroups, 2), normalMultiplyWord(agroups, 3), agroups,
                other.normalMultiplyWord(bgroups, 0), other.normalMultiplyWord(bgroups, 1),
                other.normalMultiplyWord(bgroups, 2), other.normalMultiplyWord(bgroups, 3), bgroups), adjusted);
    }

    /** groups 是正常幅值的最少组数；压缩距离只改变来源分组，组内移位固定为 45。 */
    private long normalMultiplyWord(int groups, int index) {
        if (index >= groups) {
            return 0;
        }
        long top = (l0 & FRACTION_MASK) | HIDDEN_BIT;
        if (groups == 4) {
            return fourWord(l3, l2, l1, top, index);
        }
        return switch (index + 3 - groups) {
            case 0 -> (l3 >>> 45) | (l2 << 19);
            case 1 -> (l2 >>> 45) | (l1 << 19);
            case 2 -> (l1 >>> 45) | (top << 19);
            default -> 0;
        };
    }

    @Override
    public Binary256 multiply(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN() || (isZero() && other.isInfinite()) || (isInfinite() && other.isZero())) {
            return NaN;
        }
        boolean negative = signBit() ^ other.signBit();
        if (isInfinite() || other.isInfinite()) {
            return infinity(negative);
        }
        if (isZero() || other.isZero()) {
            return zero(negative);
        }
        int minimumExponent = magnitudeExponent() + other.magnitudeExponent();
        // 精确积位于 [2^S, 2^(S+2))，只在整个区间都越过舍入边界时提前返回。
        if (minimumExponent >= FORMAT.maxExponent() + 1) {
            return infinity(negative);
        }
        if (minimumExponent <= QMIN - 3) {
            return zero(negative);
        }
        int power = other.powerOfTwoExponent();
        if (power != Integer.MIN_VALUE) {
            return scalbFinite(power, negative);
        }
        power = powerOfTwoExponent();
        if (power != Integer.MIN_VALUE) {
            return other.scalbFinite(power, negative);
        }
        if (encodedExponent() != 0 && other.encodedExponent() != 0) {
            return multiplyNormal(other, negative, minimumExponent - 472L);
        }
        return multiplyFinite(other, negative);
    }

    @Override
    public Binary256 divide(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN() || (isZero() && other.isZero()) || (isInfinite() && other.isInfinite())) {
            return NaN;
        }
        boolean negative = signBit() ^ other.signBit();
        if (isInfinite() || other.isZero()) {
            return infinity(negative);
        }
        if (isZero() || other.isInfinite()) {
            return zero(negative);
        }
        long[] a = magnitude(), b = other.magnitude();
        long scale = scaleExponent() - other.scaleExponent();
        int ratioExponent = UnsignedWords.bitLength(a) - UnsignedWords.bitLength(b);
        int comparison = ratioExponent >= 0 ? UnsignedWords.compare(a, UnsignedWords.left(b, ratioExponent))
                : UnsignedWords.compare(UnsignedWords.left(a, -ratioExponent), b);
        if (comparison < 0) {
            ratioExponent--;
        }
        long exponent = scale + ratioExponent;
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        if (exponent < QMIN - 1L) {
            return zero(negative);
        }
        long quantum = Math.max(exponent - 236, QMIN);
        long shift = scale - quantum;
        if (shift >= 0) {
            a = UnsignedWords.left(a, Math.toIntExact(shift));
        } else {
            b = UnsignedWords.left(b, Math.toIntExact(-shift));
        }
        UnsignedWords.Division division = UnsignedWords.divide(a, b);
        long[] result = division.quotient();
        int half = UnsignedWords.compare(UnsignedWords.left(division.remainder(), 1), b);
        return packRounded(negative, UnsignedWords.word(result, 0), UnsignedWords.word(result, 1),
                UnsignedWords.word(result, 2), UnsignedWords.word(result, 3), quantum,
                half > 0 || (half == 0 && UnsignedWords.bit(result, 0)));
    }

    @Override
    public Binary256 remainder(Binary256 other) {
        return remainder(other, false);
    }

    @Override
    public Binary256 ieeeRemainder(Binary256 other) {
        return remainder(other, true);
    }

    private Binary256 remainder(Binary256 other, boolean nearest) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN() || isInfinite() || other.isZero()) {
            return NaN;
        }
        if (isZero() || other.isInfinite()) {
            return this;
        }
        int comparison = compareScaledMagnitude(other, nearest ? 1 : 0);
        if (nearest ? comparison <= 0 : comparison < 0) {
            return this;
        }
        long[] a = magnitude(), b = other.magnitude(), remainder;
        long difference = scaleExponent() - other.scaleExponent();
        long scale;
        boolean odd;
        if (difference >= 0) {
            int divisorPower = other.powerOfTwoExponent();
            int modulusBits = divisorPower == Integer.MIN_VALUE ? -1 : (int) (divisorPower - other.scaleExponent()) + (nearest ? 1 : 0);
            if (modulusBits >= 0 && difference >= modulusBits) {
                return zero(signBit());
            }
            long[] modulus = nearest ? UnsignedWords.left(b, 1) : b;
            long[] reduced;
            if (modulusBits >= 0) {
                reduced = UnsignedWords.left(UnsignedWords.lowBits(a, modulusBits - (int) difference), (int) difference);
            } else if (difference <= 237) {
                // 对齐后的被除数不超过 474 位，可直接求余而无须模重复平方。
                reduced = UnsignedWords.remainder(UnsignedWords.left(a, (int) difference), modulus);
            } else {
                long[] power = UnsignedWords.powerOfTwoMod(difference, modulus);
                reduced = UnsignedWords.remainder(UnsignedWords.multiply(a, power), modulus);
            }
            odd = nearest && UnsignedWords.compare(reduced, b) >= 0;
            remainder = odd ? UnsignedWords.subtract(reduced, b) : reduced;
            scale = other.scaleExponent();
        } else {
            // 位权较大的除数必为正常数，对齐后大于被除数，向零截断的整数商为零。
            if (!nearest || UnsignedWords.bitLength(b) - difference > UnsignedWords.bitLength(a) + 1L) {
                return this;
            }
            b = UnsignedWords.left(b, Math.toIntExact(-difference));
            remainder = a;
            odd = false;
            scale = scaleExponent();
        }
        boolean negative = signBit();
        if (nearest) {
            int half = UnsignedWords.compare(UnsignedWords.left(remainder, 1), b);
            if (half > 0 || (half == 0 && odd)) {
                remainder = UnsignedWords.subtract(b, remainder);
                negative = !negative;
            }
        }
        return fromDyadic(negative, remainder, scale);
    }

    @Override
    public Binary256 negate() {
        return isNaN() ? NaN : new Binary256(l0 ^ Long.MIN_VALUE, l1, l2, l3);
    }

    @Override
    public Binary256 abs() {
        return isNaN() ? NaN : signBit() ? new Binary256(l0 & Long.MAX_VALUE, l1, l2, l3) : this;
    }

    @Override
    public Binary256 fma(Binary256 multiplier, Binary256 addend) {
        Objects.requireNonNull(multiplier);
        Objects.requireNonNull(addend);
        if (isNaN() || multiplier.isNaN() || addend.isNaN()
                || (isZero() && multiplier.isInfinite()) || (isInfinite() && multiplier.isZero())) {
            return NaN;
        }
        boolean negative = signBit() ^ multiplier.signBit();
        if (isInfinite() || multiplier.isInfinite()) {
            return addend.isInfinite() && negative != addend.signBit() ? NaN : infinity(negative);
        }
        if (addend.isInfinite()) {
            return addend;
        }
        if ((multiplier.l0 & Long.MAX_VALUE) == ONE.l0 && (multiplier.l1 | multiplier.l2 | multiplier.l3) == 0) {
            return multiplier.signBit() ? addend.subtract(this) : add(addend);
        }
        return fmaFinite(multiplier, addend, negative);
    }

    private Binary256 fmaFinite(Binary256 multiplier, Binary256 addend, boolean negative) {
        if (isZero() || multiplier.isZero()) {
            return addend.isZero() ? zero(negative && addend.signBit()) : addend;
        }
        int minimumProductExponent = magnitudeExponent() + multiplier.magnitudeExponent();
        // 乘积至少为 2^(emax+2) 时，任何有限加数都不足以将结果抵消回有限范围。
        if (minimumProductExponent >= FORMAT.maxExponent() + 2) {
            return infinity(negative);
        }
        boolean addendZero = addend.isZero();
        int addendExponent = addendZero ? 0 : addend.magnitudeExponent();
        // 精确乘积的实际指数最多比两个输入指数之和大一，先用此上界省去无关乘积的构造。
        if (!addendZero && addendExponent - minimumProductExponent - 1 >= 239) {
            return addend;
        }
        long[] product = multiplyMagnitude(multiplier);
        long productScale = scaleExponent() + multiplier.scaleExponent();
        long productExponent = productScale + UnsignedWords.bitLength(product) - 1;
        if (productExponent >= FORMAT.maxExponent() + 2L) {
            return infinity(negative);
        }
        if (addendZero) {
            return fromDyadic(negative, product, productScale);
        }
        long difference = productExponent - addendExponent;
        if (difference <= -239) {
            return addend;
        }
        if (difference >= 475) {
            return roundProductWithTail(negative, product, productScale, productExponent, negative == addend.signBit());
        }
        return sumFma(negative, product, productScale, productExponent, addend, addendExponent);
    }

    private static Binary256 roundProductWithTail(boolean negative, long[] product, long scale, long exponent,
            boolean magnitudeIncreases) {
        if (exponent > FORMAT.maxExponent()) {
            return infinity(negative);
        }
        // 加数非零且指数至少低 475，乘积必为正常范围；其与非中点舍入边界的距离大于尾项。
        int discarded = UnsignedWords.bitLength(product) - 237;
        if (discarded > 0 && UnsignedWords.bit(product, discarded - 1) && !UnsignedWords.anyLow(product, discarded - 1)) {
            // 精确中点处，尾项使绝对值增大就选上界，减小就选下界，不再按保留位奇偶处理。
            return packRounded(negative, shiftedWord(product, 0, discarded), shiftedWord(product, 1, discarded),
                    shiftedWord(product, 2, discarded), shiftedWord(product, 3, discarded), scale + discarded, magnitudeIncreases);
        }
        return fromDyadic(negative, product, scale);
    }

    private static Binary256 sumFma(boolean negative, long[] product, long productScale, long productExponent,
            Binary256 addend, int addendExponent) {
        long addendScale = addend.scaleExponent();
        long scale = Math.min(productScale, addendScale);
        // 乘积最多 474 位、加数最多 237 位；两个方向的指数差分别小于 475 和 239，连同进位最多 713 位。
        long span = Math.max(productExponent, addendExponent) - scale + 1;
        int capacity = span < 512 ? 8 : FMA_ALIGNMENT_WORDS;
        int productShift = Math.toIntExact(productScale - scale), addendShift = Math.toIntExact(addendScale - scale);
        boolean subtract = negative != addend.signBit();
        int comparison = 1;
        if (subtract) {
            comparison = Long.compare(productExponent, addendExponent);
            if (comparison == 0) {
                for (int i = capacity - 1; i >= 0; i--) {
                    comparison = Long.compareUnsigned(shiftedWord(product, i, -productShift), addend.alignedMagnitudeWord(i, addendShift));
                    if (comparison != 0) {
                        break;
                    }
                }
                if (comparison == 0) {
                    return ZERO;
                }
            }
        }
        // 仅在乘积不移位时复用其数组；按低组写入后，不会覆盖后续仍需读取的乘积分组。
        long[] result = productShift == 0 && product.length == capacity ? product : new long[capacity];
        long carry = 0;
        for (int i = 0; i < capacity; i++) {
            long av = shiftedWord(product, i, -productShift), bv = addend.alignedMagnitudeWord(i, addendShift);
            if (subtract) {
                long greater = comparison > 0 ? av : bv, smaller = comparison > 0 ? bv : av;
                long difference = greater - smaller;
                result[i] = difference - carry;
                carry = Long.compareUnsigned(greater, smaller) < 0 || Long.compareUnsigned(difference, carry) < 0 ? 1 : 0;
            } else {
                long sum = av + bv;
                long next = sum + carry;
                carry = Long.compareUnsigned(sum, av) < 0 || Long.compareUnsigned(next, sum) < 0 ? 1 : 0;
                result[i] = next;
            }
        }
        return fromDyadic(comparison > 0 ? negative : addend.signBit(), result, scale);
    }

    @Override
    public Binary256 sqrt() {
        if (isNaN() || (signBit() && !isZero())) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        // 正有限输入的平方根都在正常范围内，精确命中时可直接扩展为编码。
        int inputExponent = magnitudeExponent();
        long scale = scaleExponent(), top = magnitudeWord(3);
        int length = (int) (inputExponent - scale + 1);
        int trailing = length <= 64 ? Long.numberOfTrailingZeros(l3)
                : compactMagnitudeShift(l3, l2, l1, top, length);
        long reducedScale = scale + trailing;
        if (length - trailing <= 64 && (reducedScale & 1) == 0) {
            long magnitude = shiftedWord(l3, l2, l1, top, 0, trailing);
            // 奇数平方模 8 为一；整数根最多 32 位，其平方可按完整的无符号 long 位模式验证。
            if ((magnitude & 7) == 1) {
                long exactRoot = UnsignedWords.sqrtUnsignedLong(magnitude);
                if (exactRoot * exactRoot == magnitude) {
                    return fromUnsignedLong(false, exactRoot, reducedScale / 2);
                }
            }
        }
        long exponent = Math.floorDiv(inputExponent, 2);
        long quantum = exponent - 236;
        int shift = Math.toIntExact(scale - 2 * quantum);
        long[] workspace = new long[8];
        int comparison = UnsignedWords.sqrtScaled(l3, l2, l1, top, shift, workspace);
        // N = S² + R，中点平方为 S² + S + 1/4；R 为整数，向上舍入恰好等价于 R > S。
        return packRounded(false, workspace[0], workspace[1], workspace[2], workspace[3], quantum, comparison > 0);
    }

    @Override
    public Binary256 scalb(long scaleFactor) {
        if (isNaN()) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        return scalbFinite(scaleFactor, signBit());
    }

    /** 接收值为有限非零数，negative 已包含调用方需要的符号变换。 */
    private Binary256 scalbFinite(long scaleFactor, boolean negative) {
        int encoded = encodedExponent();
        // 先限制增量，再相加，保证全部 long 参数都不会使指数回绕。
        if (encoded != 0 && scaleFactor >= 1L - encoded && scaleFactor <= 0x7fffeL - encoded) {
            return new Binary256((negative ? Long.MIN_VALUE : 0) | (l0 & FRACTION_MASK)
                    | ((encoded + scaleFactor) << 44), l1, l2, l3);
        }
        long[] magnitude = magnitude();
        long scale = scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        // 先与有限的小范围界限比较，避免将 Long.MIN_VALUE 或 Long.MAX_VALUE 直接加到指数上。
        if (scaleFactor > FORMAT.maxExponent() - exponent) {
            return infinity(negative);
        }
        if (scaleFactor < QMIN - 1L - exponent) {
            return zero(negative);
        }
        return fromDyadic(negative, magnitude, scale + scaleFactor);
    }

    @Override
    public Binary256 nextUp() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE;
        }
        if (equals(POSITIVE_INFINITY)) {
            return this;
        }
        return stepRaw(!signBit());
    }

    @Override
    public Binary256 nextDown() {
        if (isNaN()) {
            return NaN;
        }
        if (isZero()) {
            return MIN_VALUE.negate();
        }
        if (equals(NEGATIVE_INFINITY)) {
            return this;
        }
        return stepRaw(signBit());
    }

    private Binary256 stepRaw(boolean increment) {
        long delta = increment ? 1 : -1;
        long propagated = increment ? 0 : -1;
        long next3 = l3 + delta, next2 = l2, next1 = l1, next0 = l0;
        if (next3 == propagated) {
            next2 += delta;
            if (next2 == propagated) {
                next1 += delta;
                if (next1 == propagated) {
                    next0 += delta;
                }
            }
        }
        return new Binary256(next0, next1, next2, next3);
    }

    private int compareOrdered(Binary256 other) {
        if (isZero() && other.isZero()) {
            return 0;
        }
        if (signBit() != other.signBit()) {
            return signBit() ? -1 : 1;
        }
        int result = Long.compareUnsigned(l0, other.l0);
        if (result == 0) {
            result = Long.compareUnsigned(l1, other.l1);
        }
        if (result == 0) {
            result = Long.compareUnsigned(l2, other.l2);
        }
        if (result == 0) {
            result = Long.compareUnsigned(l3, other.l3);
        }
        return signBit() ? -result : result;
    }

    @Override
    public Comparison compareNumerically(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Comparison.UNORDERED;
        }
        int comparison = compareOrdered(other);
        return comparison < 0 ? Comparison.LESS : comparison > 0 ? Comparison.GREATER : Comparison.EQUAL;
    }

    @Override
    public int compareTo(Binary256 other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Boolean.compare(isNaN(), other.isNaN());
        }
        if (isZero() && other.isZero()) {
            return Boolean.compare(other.signBit(), signBit());
        }
        return compareOrdered(other);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Binary256 other && ((isNaN() && other.isNaN())
                || (l0 == other.l0 && l1 == other.l1 && l2 == other.l2 && l3 == other.l3));
    }

    @Override
    public int hashCode() {
        if (isNaN()) {
            return Long.hashCode(NAN_TOP);
        }
        int hash = Long.hashCode(l0);
        hash = 31 * hash + Long.hashCode(l1);
        hash = 31 * hash + Long.hashCode(l2);
        return 31 * hash + Long.hashCode(l3);
    }

    /** 从普通或科学计数法十进制文本直接按最近偶数规则舍入，详见 {@link Binary#of(String)}。 */
    public static Binary256 of(String value) {
        return Binary256Decimal.parse(value);
    }

    @Override
    public String toString() {
        return Binary256Decimal.format(this, false);
    }

    @Override
    public String toString(int fractionDigits) {
        return Binary256Decimal.format(this, false, fractionDigits);
    }

    @Override
    public String toScientificString() {
        return Binary256Decimal.format(this, true);
    }

    @Override
    public String toScientificString(int fractionDigits) {
        return Binary256Decimal.format(this, true, fractionDigits);
    }

    private long primitiveBits(int precision, int emin, int emax, int exponentBits) {
        int fractionBits = precision - 1;
        long sign = signBit() ? 1L << (fractionBits + exponentBits) : 0;
        long infinity = ((1L << exponentBits) - 1) << fractionBits;
        if (isNaN()) {
            return infinity | (1L << (fractionBits - 1));
        }
        if (isInfinite()) {
            return sign | infinity;
        }
        int exponent = encodedExponent() - BIAS;
        if (exponent > emax) {
            return sign | infinity;
        }
        int minimumScale = emin - fractionBits;
        if (exponent < minimumScale - 1) {
            return sign;
        }
        // 能在 binary32 或 binary64 中舍入为非零的源值必为 Binary256 正常数。
        int quantum = Math.max(exponent - fractionBits, minimumScale);
        int discarded = quantum - exponent + 236;
        long rounded = shiftedMagnitude(discarded);
        if (magnitudeBit(discarded - 1) && ((rounded & 1) != 0 || anyLowMagnitude(discarded - 1))) {
            rounded++;
        }
        if (rounded == 0) {
            return sign;
        }
        exponent = quantum + 63 - Long.numberOfLeadingZeros(rounded);
        if (exponent > emax) {
            return sign | infinity;
        }
        long encoded = exponent < emin ? 0 : exponent + emax;
        return sign | (encoded << fractionBits)
                | (rounded & ((1L << fractionBits) - 1));
    }

    @Override
    public float toFloat() {
        return Float.intBitsToFloat((int) primitiveBits(24, -126, 127, 8));
    }

    @Override
    public double toDouble() {
        return Double.longBitsToDouble(primitiveBits(53, -1022, 1023, 11));
    }

    private long toIntegral(int width) {
        long minimum = width == 64 ? Long.MIN_VALUE : Integer.MIN_VALUE;
        long maximum = width == 64 ? Long.MAX_VALUE : Integer.MAX_VALUE;
        if (isNaN()) {
            return 0;
        }
        if (isInfinite()) {
            return signBit() ? minimum : maximum;
        }
        int exponent = encodedExponent() - BIAS;
        if (exponent >= width - 1) {
            return signBit() ? minimum : maximum;
        }
        if (exponent < 0) {
            return 0;
        }
        long result = shiftedMagnitude(236 - exponent);
        return signBit() ? -result : result;
    }

    @Override
    public int toInt() {
        return (int) toIntegral(32);
    }

    @Override
    public long toLong() {
        return toIntegral(64);
    }
}
