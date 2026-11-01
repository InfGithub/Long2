
package com.inf.long2;

import com.inf.long2.util.annotations.HaveToImplements;
import com.inf.long2.util.annotations.StaticFieldImplementsCheck;
import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

/**
 * 不可变的有符号定宽整数，采用补码表示。
 * 每个实现类型的位宽 W 固定为 {@code 64 * N}，N 为正整数，数值范围为
 * {@code -2^(W-1)} 至 {@code 2^(W-1)-1}。
 *
 * <p>算术运算保留数学结果的低 W 位，并按有符号补码解释，溢出时回绕而不抛异常。
 * 实例方法不修改接收对象或参数，返回整数的实例方法可以复用已有实例。
 *
 * <p>公开的 long 分组按大端序编号，索引 0 为最高组，{@code N-1} 为最低组。
 * bit 索引从最低有效位开始，索引 0 为最低位，{@code W-1} 为符号位。
 * 分组顺序不限定实现内部的存储方式。
 *
 * <p>{@code compareTo} 按有符号数值排序，小于、等于或大于参数时分别返回负数、零或正数。
 * 同一实现类型内，比较结果为零与 {@code equals} 相等一致。
 * 接收同类型整数参数的实例方法在参数为 {@code null} 时抛出 {@link NullPointerException}；
 * {@code equals(null)} 返回 {@code false}。
 *
 * <p>本接口的静态成员用于描述实现类必须直接声明的对应成员。
 * 字段初始化值与静态方法体是契约占位，调用者应使用具体实现类的静态成员。
 * 实现类应提供非空的 {@code public static final} 常量字段及对应的 {@code public static} 方法，
 * 声明类型中的本接口类型替换为实现类类型。
 * AP 检查对应成员的声明和类型，不检查字段值、{@code final} 修饰符或方法的行为。
 * 以下静态成员的数值、结果及异常约定均针对实现类的对应成员。
 *
 * @param <T> 实现类型自身，实例运算的整数参数和结果使用该类型
 */
@StaticMethodImplementsCheck
public interface Int<T extends Int<T>> extends Comparable<T> {

    /** 当前实现位宽下的最大值，数值为 {@code 2^(W-1)-1}。 */
    @StaticFieldImplementsCheck
    public static final Int<?> MAX_VALUE = null;

    /** 当前实现位宽下的最小值，数值为 {@code -2^(W-1)}。 */
    @StaticFieldImplementsCheck
    public static final Int<?> MIN_VALUE = null;

    /** 当前实现类型中数值为零的常量。 */
    @StaticFieldImplementsCheck
    public static final Int<?> ZERO = null;

    /** 当前实现类型中数值为一的常量。 */
    @StaticFieldImplementsCheck
    public static final Int<?> ONE = null;

    /** 将有符号 long 的数值扩展到当前位宽，高于最低 long 分组的位进行符号扩展。 */
    public static Int<?> of(long last) {
        return null;
    }

    /**
     * 按大端序解释 N 个 long 的原始位模式，数组首元素为最高组。
     * 构造后修改数组不影响结果。
     *
     * @throws NullPointerException 数组为 {@code null}
     * @throws IllegalArgumentException 数组长度不等于 N
     */
    public static Int<?> of(long[] longs) {
        return null;
    }

    /**
     * 从恰好 W/8 字节的大端序补码原始位模式恢复整数，索引零为最高有效字节。
     * 所有位模式均合法；不补零、符号扩展或截断，与 {@link #toBytes()} 逐位互逆。
     * 不修改或持有输入数组，构造后修改数组不影响结果。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不等于 W/8
     */
    public static Int<?> of(byte[] bytes) {
        return null;
    }

    /**
     * 返回完整 W 位补码的大端序原始编码，固定为 W/8 字节，与 getLong(0..N-1) 的顺序一致。
     * 保留前导零及负数的全部补码高位；单个 byte 的符号不改变其原始八位模式。
     * 每次返回独立的新数组，修改数组不影响接收对象或其他调用返回的数组。
     */
    public byte[] toBytes();

    /**
     * 将有限 double 向零截断为整数，结果超出数值范围时钳制到对应极值。
     * {@code NaN} 转为零，正负无穷分别转为最大值和最小值，正零和负零均转为零。
     */
    public static Int<?> of(double value) {
        return null;
    }

    /**
     * 按 IEEE 754 最近偶数规则转换为 double，零转换为正零。
     * 舍入结果超出有限 double 范围时返回对应符号的无穷。
     */
    public double toDouble();

    /**
     * 返回有符号数值的 ASCII 十进制字符串。
     * 负数以 {@code -} 开头，非负数不带正号；不保留多余前导零，零表示为 {@code "0"}。
     */
    @HaveToImplements
    public String toString();

    /**
     * 解析可选的一个 ASCII 正负号及至少一位 ASCII 十进制数字，允许前导零。
     * 不接受空白、分隔符、小数点、指数或其他数字字符。
     * 解析结果保留低 W 位，超出数值范围时回绕。
     *
     * @throws NumberFormatException 输入为 {@code null} 或不符合上述格式
     */
    public static Int<?> parseInt(String value) {
        return null;
    }

    /**
     * 仅校验 {@link #parseInt(String)} 的十进制语法，接受可选的一个 ASCII 正负号及至少一位 ASCII 数字。
     * 允许前导零；null、空串或非法格式返回 false，不接受十六进制、小数点、指数或空白。
     * 不检查数值范围，合法的超长整数同样返回 true，与解析时保留低 W 位的规则一致。
     * 不执行数值转换，时间复杂度为 O(n)，额外空间为 O(1)，n 为文本长度。
     */
    public static boolean isValid(String value) {
        return false;
    }

    /**
     * 返回完整 W 位原始位模式，以 {@code 0x} 开头，后接恰好 {@code W/4} 位小写十六进制数字。
     * 保留前导零，负数使用补码位模式，不另加负号。
     */
    public String toHexString();

    /**
     * 解析 {@code 0x} 前缀及至少一位 ASCII 小写十六进制数字，按原始位模式解释。
     * 不接受正负号、空白或分隔符；不足 W 位时高位补零，超长时保留低 W 位。
     * 全部输入数字都必须合法，包括截断时丢弃的高位数字。
     *
     * @throws NullPointerException 输入为 {@code null}
     * @throws NumberFormatException 输入不符合上述格式
     */
    public static Int<?> parseHexInt(String value) {
        return null;
    }

    /**
     * 仅校验 {@link #parseHexInt(String)} 的语法，接受小写 0x 前缀及至少一位 ASCII 小写十六进制数字。
     * null、空串或非法格式返回 false，不接受正负号、空白或大写字母。
     * 允许前导零和超长文本，全部数字都须合法，包括解析时截断丢弃的高位。
     * 不执行数值转换，时间复杂度为 O(n)，额外空间为 O(1)，n 为文本长度。
     */
    public static boolean isHexValid(String value) {
        return false;
    }

    /** 仅当参数属于同一实现类型且数值相等时返回 true，{@code null} 和其他类型均返回 false。 */
    @HaveToImplements
    public boolean equals(Object obj);

    /** 相等值必须返回相同的 hash，具体计算方式由实现类型决定。 */
    @HaveToImplements
    public int hashCode();

    /** 对包括符号位在内的完整 W 位执行按位与。 */
    public T and(T other);

    /** 对包括符号位在内的完整 W 位执行按位或。 */
    public T or(T other);

    /** 对包括符号位在内的完整 W 位逐位取反。 */
    public T not();

    /** 对包括符号位在内的完整 W 位执行按位异或。 */
    public T xor(T other);

    /**
     * bits 为正时左移并在低位补零，为负时算术右移并在高位进行符号扩展，为零时保持数值。
     * 左移丢弃超出位宽的高位，右移丢弃低位；右移负数向负无穷取整。
     * 实际距离为 bits 的数学绝对值对 W 取模，距离为零时保持数值。
     * 所有 int 参数均有效，包括 {@link Integer#MIN_VALUE}，其绝对值按超出 int 范围的正数计算。
     */
    public T shift(int bits);

    /** 返回 {@code this + other} 的低 W 位，溢出时按补码回绕。 */
    public T add(T other);

    /** 返回 {@code this - other} 的低 W 位，溢出时按补码回绕。 */
    public T subtract(T other);

    /** 返回 {@code -this} 的低 W 位，最小值取负仍为最小值。 */
    public T negate();

    /** 返回 {@code this + 1} 的低 W 位，最大值的后继为最小值。 */
    public T successor();

    /** 返回 {@code this * other} 的低 W 位，溢出时按补码回绕。 */
    public T multiply(T other);

    /** 负数、零和正数分别返回 {@code -1}、{@code 0} 和 {@code 1}。 */
    public int signum();

    /** 仅当完整位模式全为零时返回 true。 */
    public boolean isZero();

    /** 仅当符号位为一时返回 true。 */
    public boolean isNegative();

    /**
     * 返回大端序索引对应分组的原始 64 位模式，0 为最高组，{@code N-1} 为最低组。
     * 返回的 long 符号不改变该组在完整整数中的位权。
     *
     * @throws IndexOutOfBoundsException 索引小于 0 或不小于 N
     */
    public long getLong(int index);

    /**
     * 返回指定 bit 是否为一，0 为最低有效位，{@code W-1} 为符号位。
     *
     * @throws IndexOutOfBoundsException 索引小于 0 或不小于 W
     */
    public boolean testBit(int index);

    /**
     * 返回不计符号位、去除冗余符号扩展位后的有效位数，范围为 0 至 {@code W-1}。
     * 非负数按自身计算，负数按完整 W 位取反后的值计算。
     * 零和负一均返回 0，负二返回 1；该值不是负数绝对值的位数。
     */
    public int bitLength();

    /** 返回完整 W 位模式中值为一的 bit 数量，范围为 0 至 W，负一返回 W。 */
    public int bitCount();
}
