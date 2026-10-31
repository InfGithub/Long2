
package com.inf.long2;

import com.inf.long2.util.annotations.HaveToImplements;
import com.inf.long2.util.annotations.StaticFieldImplementsCheck;
import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

/**
 * Big-endian Integer
 *
 * @param <T>
 */
@StaticMethodImplementsCheck
public interface Int<T extends Int<T>> extends Comparable<T> {

    @StaticFieldImplementsCheck
    public static final Int<?> MAX_VALUE = null;

    @StaticFieldImplementsCheck
    public static final Int<?> MIN_VALUE = null;

    @StaticFieldImplementsCheck
    public static final Int<?> ZERO = null;

    @StaticFieldImplementsCheck
    public static final Int<?> ONE = null;

    // 从 index = -1 的 long 构造整型。
    public static Int<?> of(long last) {
        return null;
    }

    // 从所有 long 构成的数组构造整型。宽度不符时应抛出异常。
    public static Int<?> of(long[] longs) {
        return null;
    }

    // 从 double 构造整型。
    public static Int<?> of(double value) {
        return null;
    }

    /** 转换为 double，按照 IEEE 754 最近偶数规则舍入。 */
    public double toDouble();

    @HaveToImplements
    public String toString();

    public static Int<?> parseInt(String value) {
        return null;
    }

    public String toHexString();

    public static Int<?> parseHexInt(String value) {
        return null;
    }

    @HaveToImplements
    public boolean equals(Object obj);

    @HaveToImplements
    public int hashCode();

    public T and(T other);

    public T or(T other);

    public T not();

    public T xor(T other);

    public T shift(int bits);

    public T add(T other);

    public T subtract(T other);

    public T negate();

    public T successor();

    public T multiply(T other);

    public int signum();

    public boolean isZero();

    public boolean isNegative();

    public long getLong(int index);

    public boolean testBit(int index);

    /** 返回去除符号扩展位后的有效位数。 */
    public int bitLength();

    /** 返回完整位模式中值为 true 的 bit 数量。 */
    public int bitCount();
}
