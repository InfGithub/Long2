package com.inf.long2.binaries;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Random;

import com.inf.long2.Binary;
import com.inf.long2.Int;
import com.inf.long2.binaries.baseline.Binary256Baseline;

/** 从原始位生成输入，不通过待验证的算术或转换路径生成用例。 */
final class Binary256CompatibilityFixtures {
    static final BigInteger ONE = BigInteger.ONE;
    static final BigInteger FRACTION_MASK = ONE.shiftLeft(236).subtract(ONE);
    static final Bits ZERO = new Bits(0, 0, 0, 0);
    static final Bits UNIT = new Bits(0x3ffff00000000000L, 0, 0, 0);
    static final Bits MINIMUM = new Bits(0, 0, 0, 1);
    static final Bits MINIMUM_NORMAL = new Bits(0x0000100000000000L, 0, 0, 0);
    static final Bits MAXIMUM = new Bits(0x7fffefffffffffffL, -1, -1, -1);
    static final Bits INFINITY = new Bits(0x7ffff00000000000L, 0, 0, 0);
    static final Bits NAN = new Bits(0x7ffff80000000000L, 0, 0, 0);

    private Binary256CompatibilityFixtures() {
    }

    record Bits(long l0, long l1, long l2, long l3) {
        Binary256 actual() {
            return Binary256.fromRawBits(l0, l1, l2, l3);
        }

        Binary256Baseline baseline() {
            return Binary256Baseline.fromRawBits(l0, l1, l2, l3);
        }

        Bits negative() {
            return new Bits(l0 | Long.MIN_VALUE, l1, l2, l3);
        }

        @Override
        public String toString() {
            return "%016x%016x%016x%016x".formatted(l0, l1, l2, l3);
        }
    }

    static Bits bits(BigInteger raw) {
        return new Bits(raw.shiftRight(192).longValue(), raw.shiftRight(128).longValue(),
                raw.shiftRight(64).longValue(), raw.longValue());
    }

    static Bits normal(boolean negative, int exponent, BigInteger fraction) {
        if (exponent < -262142 || exponent > 262143 || fraction.signum() < 0 || fraction.bitLength() > 236) {
            throw new IllegalArgumentException("Invalid normal encoding");
        }
        Bits value = bits(BigInteger.valueOf(exponent + 262143L).shiftLeft(236).or(fraction));
        return negative ? value.negative() : value;
    }

    static Bits randomFinite(Random random, int exponent) {
        BigInteger fraction = new BigInteger(236, random);
        if (exponent < -262142) {
            Bits value = bits(fraction);
            return random.nextBoolean() ? value.negative() : value;
        }
        return normal(random.nextBoolean(), exponent, fraction);
    }

    static Bits power(int exponent) {
        if (exponent < -262378 || exponent > 262143) {
            throw new IllegalArgumentException("Invalid power exponent");
        }
        return exponent < -262142 ? bits(ONE.shiftLeft(exponent + 262378)) : normal(false, exponent, BigInteger.ZERO);
    }

    /** 只接受可精确编码的非负幅值，防止测试输入构造器先行舍入而隐藏错误。 */
    static Bits exact(BigInteger magnitude, long scale) {
        if (magnitude.signum() == 0) {
            return ZERO;
        }
        long exponent = scale + magnitude.bitLength() - 1;
        long quantum = Math.max(exponent - 236, -262378);
        long shift = scale - quantum;
        if (shift < 0 && magnitude.getLowestSetBit() < -shift) {
            throw new IllegalArgumentException("Input must be exactly representable");
        }
        BigInteger normalized = shift < 0 ? magnitude.shiftRight(Math.toIntExact(-shift))
                : magnitude.shiftLeft(Math.toIntExact(shift));
        if (magnitude.signum() < 0 || normalized.bitLength() > 237 || exponent > 262143) {
            throw new IllegalArgumentException("Invalid finite input");
        }
        long encoded = exponent < -262142 ? 0 : exponent + 262143;
        return bits(BigInteger.valueOf(encoded).shiftLeft(236).or(normalized.and(FRACTION_MASK)));
    }

    static Binary<?> binary(Binary.Format format, boolean negative, BigInteger magnitude, long scale) {
        int precision = format.precision();
        long minimumScale = (long) format.minExponent() - precision + 1;
        long exponent = scale + magnitude.bitLength() - 1;
        boolean normal = magnitude.bitLength() == precision;
        if (magnitude.signum() < 0 || magnitude.bitLength() > precision
                || (normal ? exponent < format.minExponent() || exponent > format.maxExponent() : scale != minimumScale)) {
            throw new IllegalArgumentException("Invalid Binary decomposition");
        }
        int groups = (int) ((precision + 63L) / 64);
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "signBit" -> negative;
                    case "isNaN", "isInfinite" -> false;
                    case "isFinite" -> true;
                    case "isZero" -> magnitude.signum() == 0;
                    case "isNormal" -> normal;
                    case "isSubnormal" -> !normal && magnitude.signum() != 0;
                    case "scaleExponent" -> scale;
                    case "getSignificandLong" -> {
                        int index = (int) arguments[0];
                        if (index < 0 || index >= groups) {
                            throw new IndexOutOfBoundsException(index);
                        }
                        yield magnitude.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** 稀疏分组允许验证 int 极值精度，无须分配完整有效数字。 */
    static Binary<?> sparseBinary(int exponent) {
        int precision = Integer.MAX_VALUE;
        int groups = (int) ((precision + 63L) / 64);
        Binary.Format format = new Binary.Format(precision, Integer.MIN_VALUE, Integer.MAX_VALUE);
        long[] setBits = {precision - 1L, precision - 238L, 0};
        return (Binary<?>) Proxy.newProxyInstance(Binary.class.getClassLoader(), new Class<?>[] {Binary.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "format" -> format;
                    case "signBit", "isNaN", "isInfinite", "isZero", "isSubnormal" -> false;
                    case "isFinite", "isNormal" -> true;
                    case "scaleExponent" -> (long) exponent - precision + 1;
                    case "getSignificandLong" -> {
                        int index = (int) arguments[0];
                        if (index < 0 || index >= groups) {
                            throw new IndexOutOfBoundsException(index);
                        }
                        int group = groups - 1 - index;
                        long word = 0;
                        for (long bit : setBits) {
                            if (bit / 64 == group) {
                                word |= 1L << (bit & 63);
                            }
                        }
                        yield word;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Int<?> integer(int width, BigInteger value) {
        BigInteger modulus = ONE.shiftLeft(width);
        BigInteger raw = value.mod(modulus);
        BigInteger signed = raw.testBit(width - 1) ? raw.subtract(modulus) : raw;
        int groups = width / 64;
        if (width < 64 || width % 64 != 0) {
            throw new IllegalArgumentException("Invalid Int width");
        }
        return (Int<?>) Proxy.newProxyInstance(Int.class.getClassLoader(), new Class<?>[] {Int.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isNegative" -> signed.signum() < 0;
                    case "isZero" -> signed.signum() == 0;
                    case "signum" -> signed.signum();
                    case "negate" -> integer(width, signed.negate());
                    case "bitLength" -> signed.bitLength();
                    case "bitCount" -> raw.bitCount();
                    case "testBit" -> {
                        int index = (int) arguments[0];
                        if (index < 0 || index >= width) {
                            throw new IndexOutOfBoundsException(index);
                        }
                        yield raw.testBit(index);
                    }
                    case "getLong" -> {
                        int index = (int) arguments[0];
                        if (index < 0 || index >= groups) {
                            throw new IndexOutOfBoundsException(index);
                        }
                        yield raw.shiftRight((groups - 1 - index) * 64).longValue();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
