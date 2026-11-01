package com.inf.long2.binaries.baseline;

import java.util.Objects;

import com.inf.long2.Binary;
import com.inf.long2.Int;

/**
 * 数值兼容性基线，浮点计算与整数内核均独立于生产实现。
 * 优化生产代码时保持此基线不变；契约变更须单独审查基线和预期结果。
 */
public final class Binary256Baseline implements Binary<Binary256Baseline> {
    // 新增转换与文本校验不属于冻结数值基线；这里只补齐接口契约，不充当这些功能的参考实现。
    public static Binary256Baseline of(String value) {
        throw new UnsupportedOperationException("Decimal conversion is outside the frozen baseline");
    }

    public static boolean isValid(String value) {
        throw new UnsupportedOperationException("Text validation is outside the frozen baseline");
    }

    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Byte conversion is outside the frozen baseline");
    }

    @Override
    public String toScientificString() {
        throw new UnsupportedOperationException("Decimal conversion is outside the frozen baseline");
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public String toString(int fractionDigits) {
        throw new UnsupportedOperationException("Decimal conversion is outside the frozen baseline");
    }

    @Override
    public String toScientificString(int fractionDigits) {
        throw new UnsupportedOperationException("Decimal conversion is outside the frozen baseline");
    }

    private static final int BIAS = 262143;
    private static final int QMIN = -262378;
    private static final long FRACTION_MASK = 0x0000_0fff_ffff_ffffL;
    private static final long HIDDEN_BIT = 0x0000_1000_0000_0000L;
    private static final long INFINITY_TOP = 0x7fff_f000_0000_0000L;
    private static final long NAN_TOP = 0x7fff_f800_0000_0000L;

    public static final Format FORMAT = new Format(237, -262142, 262143);

    public static final Binary256Baseline ZERO = new Binary256Baseline(0, 0, 0, 0);
    public static final Binary256Baseline NEGATIVE_ZERO = new Binary256Baseline(Long.MIN_VALUE, 0, 0, 0);
    public static final Binary256Baseline ONE = new Binary256Baseline(0x3fff_f000_0000_0000L, 0, 0, 0);
    public static final Binary256Baseline MIN_VALUE = new Binary256Baseline(0, 0, 0, 1);
    public static final Binary256Baseline MIN_NORMAL = new Binary256Baseline(HIDDEN_BIT, 0, 0, 0);
    public static final Binary256Baseline MAX_VALUE = new Binary256Baseline(0x7fff_efff_ffff_ffffL, -1, -1, -1);
    public static final Binary256Baseline POSITIVE_INFINITY = new Binary256Baseline(INFINITY_TOP, 0, 0, 0);
    public static final Binary256Baseline NEGATIVE_INFINITY = new Binary256Baseline(INFINITY_TOP | Long.MIN_VALUE, 0, 0, 0);
    public static final Binary256Baseline NaN = new Binary256Baseline(NAN_TOP, 0, 0, 0);

    private final long l0;
    private final long l1;
    private final long l2;
    private final long l3;

    private Binary256Baseline(long l0, long l1, long l2, long l3) {
        this.l0 = l0;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
    }

    public static Binary256Baseline fromRawBits(long l0, long l1, long l2, long l3) {
        return new Binary256Baseline(l0, l1, l2, l3);
    }

    public long getRawLong(int index) {
        return switch (index) {
            case 0 -> l0;
            case 1 -> l1;
            case 2 -> l2;
            case 3 -> l3;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public static Binary256Baseline of(long value) {
        // Long.MIN_VALUE 取负后的位模式仍是正确的无符号幅值。
        return fromDyadic(value < 0, UnsignedWords.trim(new long[] {value < 0 ? -value : value}), 0);
    }

    public static Binary256Baseline of(float value) {
        int bits = Float.floatToRawIntBits(value);
        return fromPrimitive(bits < 0, (bits >>> 23) & 255, bits & 0x7fffffL, 23, 127, 255);
    }

    public static Binary256Baseline of(double value) {
        long bits = Double.doubleToRawLongBits(value);
        return fromPrimitive(bits < 0, (int) ((bits >>> 52) & 2047), bits & 0xfffffffffffffL, 52, 1023, 2047);
    }

    private static Binary256Baseline fromPrimitive(boolean negative, int exponent, long fraction,
            int fractionBits, int bias, int infinityExponent) {
        if (exponent == infinityExponent) {
            return fraction == 0 ? infinity(negative) : NaN;
        }
        long magnitude = exponent == 0 ? fraction : fraction | (1L << fractionBits);
        long scale = (exponent == 0 ? 1L : exponent) - bias - fractionBits;
        return fromDyadic(negative, UnsignedWords.trim(new long[] {magnitude}), scale);
    }

    public static Binary256Baseline of(Int<?> value) {
        Objects.requireNonNull(value);
        boolean negative = value.isNegative();
        Int<?> magnitude = negative ? value.negate() : value;
        long length = (long) magnitude.bitLength() + (magnitude.isNegative() ? 1 : 0);
        if (length > FORMAT.maxExponent() + 1L) {
            return infinity(negative);
        }
        long[] words = new long[(int) ((length + 63) / 64)];
        for (int i = 0; i < length; i++) {
            if (magnitude.testBit(i)) {
                words[i / 64] |= 1L << (i & 63);
            }
        }
        return fromDyadic(negative, UnsignedWords.trim(words), 0);
    }

    public static Binary256Baseline of(Binary<?> value) {
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
        if (value instanceof Binary256Baseline same) {
            return same;
        }
        int groups = (int) ((value.format().precision() + 63L) / 64);
        int first = 0;
        long top;
        do {
            top = value.getSignificandLong(first++);
        } while (top == 0 && first < groups);
        long length = (groups - (long) first) * 64 + 64 - Long.numberOfLeadingZeros(top);
        long scale = value.scaleExponent();
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
        for (int i = 0; i < kept; i++) {
            long bit = dropped + i;
            long word = value.getSignificandLong(groups - 1 - (int) (bit / 64));
            if ((word & (1L << (bit & 63))) != 0) {
                magnitude[i / 64] |= 1L << (i & 63);
            }
        }
        // 保留两位舍入余量后，把更低的非零尾项并入最低位；该位始终低于最终 guard。
        boolean sticky = false;
        int whole = (int) (dropped / 64);
        for (int i = 0; i < whole && !sticky; i++) {
            sticky = value.getSignificandLong(groups - 1 - i) != 0;
        }
        int partial = (int) (dropped & 63);
        if (!sticky && partial != 0) {
            sticky = (value.getSignificandLong(groups - 1 - whole) & ((1L << partial) - 1)) != 0;
        }
        if (sticky) {
            magnitude[0] |= 1;
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

    private static Binary256Baseline zero(boolean negative) {
        return negative ? NEGATIVE_ZERO : ZERO;
    }

    private static Binary256Baseline infinity(boolean negative) {
        return negative ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
    }

    private record Rounded(long[] magnitude, long scale, boolean infinite) {
    }

    private static Rounded roundDyadic(long[] magnitude, long scale, int precision, int emin, int emax) {
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return new Rounded(new long[0], 0, false);
        }
        long exponent = scale + length - 1;
        long minimumScale = (long) emin - precision + 1;
        if (exponent > emax) {
            return new Rounded(new long[0], 0, true);
        }
        if (exponent < minimumScale - 1) {
            return new Rounded(new long[0], 0, false);
        }
        long quantum = Math.max(exponent - precision + 1, minimumScale);
        long discarded = quantum - scale;
        long[] result;
        if (discarded > 0) {
            result = UnsignedWords.right(magnitude, discarded);
            if (UnsignedWords.bit(magnitude, discarded - 1)
                    && (UnsignedWords.anyLow(magnitude, discarded - 1) || UnsignedWords.bit(result, 0))) {
                result = UnsignedWords.add(result, new long[] {1});
            }
        } else {
            result = UnsignedWords.left(magnitude, Math.toIntExact(-discarded));
        }
        return finish(result, quantum, precision, emax);
    }

    private static Rounded finish(long[] magnitude, long scale, int precision, int emax) {
        if (UnsignedWords.bitLength(magnitude) > precision) {
            magnitude = UnsignedWords.right(magnitude, 1);
            scale++;
        }
        boolean infinite = magnitude.length != 0 && scale + UnsignedWords.bitLength(magnitude) - 1 > emax;
        return new Rounded(magnitude, scale, infinite);
    }

    private static Binary256Baseline fromDyadic(boolean negative, long[] magnitude, long scale) {
        return pack(negative, roundDyadic(magnitude, scale, 237, -262142, 262143));
    }

    private static Binary256Baseline pack(boolean negative, Rounded rounded) {
        if (rounded.infinite()) {
            return infinity(negative);
        }
        long[] magnitude = rounded.magnitude();
        int length = UnsignedWords.bitLength(magnitude);
        if (length == 0) {
            return zero(negative);
        }
        long exponent = rounded.scale() + length - 1;
        long encoded = exponent < FORMAT.minExponent() ? 0 : exponent + BIAS;
        return new Binary256Baseline((negative ? Long.MIN_VALUE : 0) | (encoded << 44)
                | (UnsignedWords.word(magnitude, 3) & FRACTION_MASK),
                UnsignedWords.word(magnitude, 2), UnsignedWords.word(magnitude, 1), UnsignedWords.word(magnitude, 0));
    }

    private static Binary256Baseline sum(boolean an, long[] a, long as, boolean bn, long[] b, long bs) {
        if (a.length == 0 && b.length == 0) {
            return zero(an && bn);
        }
        if (a.length == 0) {
            return fromDyadic(bn, b, bs);
        }
        if (b.length == 0) {
            return fromDyadic(an, a, as);
        }
        long scale = Math.min(as, bs);
        a = UnsignedWords.left(a, Math.toIntExact(as - scale));
        b = UnsignedWords.left(b, Math.toIntExact(bs - scale));
        if (an == bn) {
            return fromDyadic(an, UnsignedWords.add(a, b), scale);
        }
        int comparison = UnsignedWords.compare(a, b);
        return comparison == 0 ? ZERO : comparison > 0
                ? fromDyadic(an, UnsignedWords.subtract(a, b), scale)
                : fromDyadic(bn, UnsignedWords.subtract(b, a), scale);
    }

    @Override
    public Binary256Baseline add(Binary256Baseline other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return NaN;
        }
        if (isInfinite() || other.isInfinite()) {
            return isInfinite() && other.isInfinite() && signBit() != other.signBit()
                    ? NaN : isInfinite() ? this : other;
        }
        return sum(signBit(), magnitude(), scaleExponent(), other.signBit(), other.magnitude(), other.scaleExponent());
    }

    @Override
    public Binary256Baseline subtract(Binary256Baseline other) {
        return add(Objects.requireNonNull(other).negate());
    }

    @Override
    public Binary256Baseline multiply(Binary256Baseline other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN() || (isZero() && other.isInfinite()) || (isInfinite() && other.isZero())) {
            return NaN;
        }
        boolean negative = signBit() ^ other.signBit();
        if (isInfinite() || other.isInfinite()) {
            return infinity(negative);
        }
        return fromDyadic(negative, UnsignedWords.multiply(magnitude(), other.magnitude()),
                scaleExponent() + other.scaleExponent());
    }

    @Override
    public Binary256Baseline divide(Binary256Baseline other) {
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
        if (half > 0 || (half == 0 && UnsignedWords.bit(result, 0))) {
            result = UnsignedWords.add(result, new long[] {1});
        }
        return pack(negative, finish(result, quantum, 237, FORMAT.maxExponent()));
    }

    @Override
    public Binary256Baseline remainder(Binary256Baseline other) {
        return remainder(other, false);
    }

    @Override
    public Binary256Baseline ieeeRemainder(Binary256Baseline other) {
        return remainder(other, true);
    }

    private Binary256Baseline remainder(Binary256Baseline other, boolean nearest) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN() || isInfinite() || other.isZero()) {
            return NaN;
        }
        if (isZero() || other.isInfinite()) {
            return this;
        }
        long[] a = magnitude(), b = other.magnitude(), remainder;
        long difference = scaleExponent() - other.scaleExponent();
        long scale;
        boolean odd;
        if (difference >= 0) {
            long[] modulus = UnsignedWords.left(b, 1);
            long[] power = UnsignedWords.powerOfTwoMod(difference, modulus);
            long[] reduced = UnsignedWords.divide(UnsignedWords.multiply(a, power), modulus).remainder();
            odd = UnsignedWords.compare(reduced, b) >= 0;
            remainder = odd ? UnsignedWords.subtract(reduced, b) : reduced;
            scale = other.scaleExponent();
        } else {
            if (UnsignedWords.bitLength(b) - difference > UnsignedWords.bitLength(a) + 1L) {
                return this;
            }
            b = UnsignedWords.left(b, Math.toIntExact(-difference));
            UnsignedWords.Division division = UnsignedWords.divide(a, b);
            remainder = division.remainder();
            odd = UnsignedWords.bit(division.quotient(), 0);
            scale = scaleExponent();
        }
        boolean negative = signBit();
        int half = UnsignedWords.compare(UnsignedWords.left(remainder, 1), b);
        if (nearest && (half > 0 || (half == 0 && odd))) {
            remainder = UnsignedWords.subtract(b, remainder);
            negative = !negative;
        }
        return fromDyadic(negative, remainder, scale);
    }

    @Override
    public Binary256Baseline negate() {
        return isNaN() ? NaN : new Binary256Baseline(l0 ^ Long.MIN_VALUE, l1, l2, l3);
    }

    @Override
    public Binary256Baseline abs() {
        return isNaN() ? NaN : signBit() ? new Binary256Baseline(l0 & Long.MAX_VALUE, l1, l2, l3) : this;
    }

    @Override
    public Binary256Baseline fma(Binary256Baseline multiplier, Binary256Baseline addend) {
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
        // 不对乘积提前编码，保留中间溢出后仍可被加数取消的全部有效位。
        return sum(negative, UnsignedWords.multiply(magnitude(), multiplier.magnitude()),
                scaleExponent() + multiplier.scaleExponent(),
                addend.signBit(), addend.magnitude(), addend.scaleExponent());
    }

    @Override
    public Binary256Baseline sqrt() {
        if (isNaN() || (signBit() && !isZero())) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        long[] magnitude = magnitude();
        long scale = scaleExponent();
        long exponent = Math.floorDiv(scale + UnsignedWords.bitLength(magnitude) - 1, 2);
        long quantum = exponent - 236;
        long[] radicand = UnsignedWords.left(magnitude, Math.toIntExact(scale - 2 * quantum));
        UnsignedWords.Root root = UnsignedWords.sqrt(radicand);
        long[] result = root.value();
        // 比较 4R 与中点平方等价于比较 4 * remainder 与 4 * root + 1，无须再次计算大平方。
        int half = UnsignedWords.compare(UnsignedWords.left(root.remainder(), 2),
                UnsignedWords.add(UnsignedWords.left(result, 2), new long[] {1}));
        if (half > 0 || (half == 0 && UnsignedWords.bit(result, 0))) {
            result = UnsignedWords.add(result, new long[] {1});
        }
        // 本格式任意正有限输入的平方根都在正常范围内，整数舍入后的结果可精确编码。
        return fromDyadic(false, result, quantum);
    }

    @Override
    public Binary256Baseline scalb(long scaleFactor) {
        if (isNaN()) {
            return NaN;
        }
        if (isZero() || isInfinite()) {
            return this;
        }
        long[] magnitude = magnitude();
        long scale = scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        // 先与有限的小范围界限比较，避免将 Long.MIN_VALUE 或 Long.MAX_VALUE 直接加到指数上。
        if (scaleFactor > FORMAT.maxExponent() - exponent) {
            return infinity(signBit());
        }
        if (scaleFactor < QMIN - 1L - exponent) {
            return zero(signBit());
        }
        return fromDyadic(signBit(), magnitude, scale + scaleFactor);
    }

    @Override
    public Binary256Baseline nextUp() {
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
    public Binary256Baseline nextDown() {
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

    private Binary256Baseline stepRaw(boolean increment) {
        long[] raw = {l3, l2, l1, l0};
        long[] result = increment ? UnsignedWords.add(raw, new long[] {1})
                : UnsignedWords.subtract(raw, new long[] {1});
        return new Binary256Baseline(UnsignedWords.word(result, 3), UnsignedWords.word(result, 2),
                UnsignedWords.word(result, 1), UnsignedWords.word(result, 0));
    }

    private int compareOrdered(Binary256Baseline other) {
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
    public Comparison compareNumerically(Binary256Baseline other) {
        Objects.requireNonNull(other);
        if (isNaN() || other.isNaN()) {
            return Comparison.UNORDERED;
        }
        int comparison = compareOrdered(other);
        return comparison < 0 ? Comparison.LESS : comparison > 0 ? Comparison.GREATER : Comparison.EQUAL;
    }

    @Override
    public int compareTo(Binary256Baseline other) {
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
        return obj instanceof Binary256Baseline other && ((isNaN() && other.isNaN())
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
        Rounded rounded = roundDyadic(magnitude(), scaleExponent(), precision, emin, emax);
        if (rounded.infinite()) {
            return sign | infinity;
        }
        int length = UnsignedWords.bitLength(rounded.magnitude());
        if (length == 0) {
            return sign;
        }
        long exponent = rounded.scale() + length - 1;
        long encoded = exponent < emin ? 0 : exponent + emax;
        return sign | (encoded << fractionBits)
                | (UnsignedWords.word(rounded.magnitude(), 0) & ((1L << fractionBits) - 1));
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
        long[] magnitude = magnitude();
        long scale = scaleExponent();
        long exponent = scale + UnsignedWords.bitLength(magnitude) - 1;
        if (exponent >= width - 1) {
            return signBit() ? minimum : maximum;
        }
        if (exponent < 0 || magnitude.length == 0) {
            return 0;
        }
        long[] integer = scale < 0 ? UnsignedWords.right(magnitude, -scale)
                : UnsignedWords.left(magnitude, Math.toIntExact(scale));
        long result = UnsignedWords.word(integer, 0);
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
