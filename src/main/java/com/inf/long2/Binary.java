package com.inf.long2;

import com.inf.long2.util.annotations.HaveToImplements;
import com.inf.long2.util.annotations.KeepContractType;
import com.inf.long2.util.annotations.StaticFieldImplementsCheck;
import com.inf.long2.util.annotations.StaticMethodImplementsCheck;

/**
 * 采用 IEEE 754 二进制浮点数值规则的不可变浮点数，每个实现类型具有固定精度和指数范围。
 * 支持正常数、次正规数、正负零、正负无穷和 NaN，扩展格式可以扩大精度或指数范围。
 * 格式由 {@link Format} 描述，不限定总编码位数、指数偏置或最高有效位是否隐含存储。
 *
 * <p>需要舍入的数值运算和浮点转换都从精确结果直接舍入到目标格式，默认采用最近偶数规则。
 * 恰好处于两个可表示数中间时，选择最低保留位为零的结果。
 * 不允许因经过 double 等中间格式而改变结果，也不允许将次正规数直接冲刷为零。
 * 上溢、下溢和零的符号由舍入规则决定，不能用超出最大有限值就返回无穷的判断代替舍入。
 *
 * <p>数值运算接收 NaN 时返回 NaN，不承诺保留 NaN 的符号、payload 或 signaling 状态。
 * 无效数值组合返回 NaN，浮点除零、上溢和下溢不抛出 ArithmeticException。
 * 本接口固定默认舍入方式，不提供异常状态、trap 或完整 IEEE 754 功能集的保证。
 *
 * <p>实例方法不修改接收对象或参数，可以复用已有实例，不保证返回新对象。
 * 引用参数为 {@code null} 时抛出 {@link NullPointerException}，{@code equals(null)} 返回 false。
 * 同类型运算使用 T，跨格式转换必须显式调用目标实现类型的静态方法。
 *
 * <p>本接口的静态成员是实现类的声明契约，字段初始化值和静态方法体中的 null 均为占位。
 * 调用者应使用具体实现类的对应成员；实现类须直接声明非空的 public static final 常量字段及对应的 public static 方法。
 * AP 默认把声明中的本接口类型替换为实现类类型，标有 {@link KeepContractType} 的参数保留完整声明类型。
 * AP 检查成员声明和类型，不检查 final 修饰符、字段值或方法行为。
 * 以下静态成员的数值、结果和异常规则均针对实现类的对应成员。
 *
 * @param <T> 实现类型自身，同类型数值运算的参数和结果使用该类型
 */
@StaticMethodImplementsCheck
public interface Binary<T extends Binary<T>> extends Comparable<T> {

    /**
     * 固定二进制数值格式，不描述具体编码布局。
     * 正常数的有效数字有 p 位，无偏指数 E 满足 {@code emin <= E <= emax}。
     * 最小正常正数为 {@code 2^emin}，最小非零正数为 {@code 2^(emin-p+1)}，
     * 最大有限正数为 {@code (2-2^(1-p)) * 2^emax}。
     * 指数与精度相加减时须使用 long 中间量，避免 int 溢出。
     *
     * @param precision 有效数字精度 p，包含正常数最高有效位，至少为 2
     * @param minExponent 最小正常指数 emin，不大于 0
     * @param maxExponent 最大正常指数 emax，不小于 0
     */
    record Format(int precision, int minExponent, int maxExponent) {
        /**
         * 验证格式包含正常数一，并具有至少两位有效数字。
         *
         * @throws IllegalArgumentException 精度小于 2、最小正常指数大于 0 或最大正常指数小于 0
         */
        public Format {
            if (precision < 2 || minExponent > 0 || maxExponent < 0) {
                throw new IllegalArgumentException(
                        "precision must be at least 2, minExponent at most 0, and maxExponent at least 0");
            }
        }
    }

    /** 数值比较的四种互斥结果，NaN 参与比较时不归入小于、相等或大于。 */
    enum Comparison {
        /** 接收值小于参数。 */
        LESS,
        /** 数值相等，包含正零与负零，以及同号无穷。 */
        EQUAL,
        /** 接收值大于参数。 */
        GREATER,
        /** 至少一个操作数为 NaN，包括 NaN 与自身比较。 */
        UNORDERED
    }

    /** 当前实现类型的固定格式，所有实例的 {@link #format()} 必须与其相等。 */
    @StaticFieldImplementsCheck
    Format FORMAT = null;

    /** 正零，符号位为零。 */
    @StaticFieldImplementsCheck
    Binary<?> ZERO = null;

    /** 负零，符号位为一。 */
    @StaticFieldImplementsCheck
    Binary<?> NEGATIVE_ZERO = null;

    /** 精确表示数值一的常量。 */
    @StaticFieldImplementsCheck
    Binary<?> ONE = null;

    /** 最小非零正数，数值为 {@code 2^(emin-p+1)}，不表示最负有限数。 */
    @StaticFieldImplementsCheck
    Binary<?> MIN_VALUE = null;

    /** 最小正常正数，数值为 {@code 2^emin}。 */
    @StaticFieldImplementsCheck
    Binary<?> MIN_NORMAL = null;

    /** 最大有限正数，数值为 {@code (2-2^(1-p)) * 2^emax}，取负后为最负有限数。 */
    @StaticFieldImplementsCheck
    Binary<?> MAX_VALUE = null;

    /** 正无穷，符号位为零。 */
    @StaticFieldImplementsCheck
    Binary<?> POSITIVE_INFINITY = null;

    /** 负无穷，符号位为一。 */
    @StaticFieldImplementsCheck
    Binary<?> NEGATIVE_INFINITY = null;

    /** 正的 quiet NaN 常量，不规定 payload。 */
    @StaticFieldImplementsCheck
    Binary<?> NaN = null;

    /** 从精确的有符号 long 数值直接舍入到目标格式，整数零转换为正零。 */
    static Binary<?> of(long value) {
        return null;
    }

    /** 从 float 的精确源数值直接舍入到目标格式，保留零和无穷的符号，NaN 转为目标格式的 NaN。 */
    static Binary<?> of(float value) {
        return null;
    }

    /** 从 double 的精确源数值直接舍入到目标格式，保留零和无穷的符号，NaN 转为目标格式的 NaN。 */
    static Binary<?> of(double value) {
        return null;
    }

    /**
     * 从普通或科学计数法十进制文本的精确值直接舍入到目标格式，采用最近偶数规则。
     * 有限数语法为 {@code [+-]?(digits(\.digits?)?|\.digits)([eE][+-]?digits)?}，
     * digits 为至少一个 ASCII 数字；允许前导零，不接受空白、分隔符或类型后缀。
     * 另接受大小写严格匹配的 {@code NaN}、{@code Infinity}、{@code +Infinity} 和 {@code -Infinity}。
     * 保留零的符号，上溢和下溢按目标格式舍入；合法指数不因超出 int 或 long 范围而被拒绝。
     *
     * @throws NullPointerException 文本为 null
     * @throws NumberFormatException 文本为空或不符合上述语法
     */
    static Binary<?> of(String value) {
        return null;
    }

    /**
     * 从当前实现自己的大端序原始编码恢复数值，与 {@link #toBytes()} 逐位互逆。
     * 具体实现须说明所需字节长度和编码布局；不同实现之间不承诺字节编码兼容。
     * 不补零、不截断或舍入，完整保留正负零及 NaN 的符号、payload 和 signaling 位。
     * 不修改或持有输入数组，构造后修改数组不影响结果。
     *
     * @throws NullPointerException 数组为 null
     * @throws IllegalArgumentException 数组长度不符合当前实现的编码要求
     */
    static Binary<?> of(byte[] bytes) {
        return null;
    }

    /**
     * 判断文本是否符合 {@link #of(String)} 接受的语法，null、空串或非法格式返回 false。
     * 仅校验语法，不要求数值能被目标格式精确表示或舍入为有限非零数；
     * 合法的超长指数、NaN 及正负无穷同样返回 true。
     * 校验不执行数值转换，时间复杂度为 O(n)，额外空间为 O(1)，n 为文本长度。
     * 本静态方法是实现类的声明契约，调用者应使用具体实现类的对应方法。
     */
    static boolean isValid(String value) {
        return false;
    }

    /**
     * 从完整的有符号整数数值直接舍入到目标格式，整数零转换为正零。
     * 不得先通过 {@link Int#toDouble()} 转换，以免丢失源值精度或范围。
     */
    static Binary<?> of(Int<?> value) {
        return null;
    }

    /**
     * 从任意 Binary 实现的精确源数值直接舍入到目标格式，不经过中间浮点格式。
     * 有限值通过符号、{@link #scaleExponent()} 和 {@link #getSignificandLong(int)} 无损读取。
     * 保留零和无穷的符号，NaN 转为目标格式的 NaN，不保证保留其编码信息。
     */
    static Binary<?> of(@KeepContractType Binary<?> value) {
        return null;
    }

    /** 返回非空的固定格式，所有实例均与当前实现类型的 FORMAT 相等，包括零、无穷和 NaN。 */
    Format format();

    /**
     * 返回当前值原始二进制编码的大端序字节数组，索引零为最高有效字节。
     * 具体实现须说明编码布局和字节长度；格式描述 {@link Format} 不限定这些编码细节。
     * 完整保留符号、指数、小数字段，以及 NaN 的符号、payload 和 signaling 位，不执行数值转换。
     * 每次返回独立的新数组，修改数组不会影响接收对象或其他调用返回的数组。
     * 单个 byte 的符号不改变其原始八位模式。
     */
    byte[] toBytes();

    /**
     * 返回当前值的符号位，负零和负无穷返回 true，正零和正无穷返回 false。
     * NaN 同样可读取当前符号，但构造和数值运算不承诺保留该符号。
     */
    boolean signBit();

    /** 仅当值为 NaN 时返回 true，该值与包括自身在内的任何值进行数值比较均为无序。 */
    boolean isNaN();

    /** 仅当值为正无穷或负无穷时返回 true。 */
    boolean isInfinite();

    /** 仅当值为零、正常数或次正规数时返回 true。 */
    boolean isFinite();

    /** 对正零和负零返回 true，与正常数、次正规数、无穷和 NaN 分类互斥。 */
    boolean isZero();

    /** 仅当值为有限非零数且绝对值不小于 MIN_NORMAL 时返回 true。 */
    boolean isNormal();

    /** 仅当值为有限非零数且绝对值小于 MIN_NORMAL 时返回 true。 */
    boolean isSubnormal();

    /**
     * 返回有限值唯一分解 {@code (-1)^s * M * 2^q} 中的 q，s 来自 {@link #signBit()}。
     * M 为 {@link #getSignificandLong(int)} 提供的非负整数，有效数字精度 p 和指数边界来自 {@link #format()}。
     * 正常数满足 {@code 2^(p-1) <= M < 2^p}，且 {@code q = E-p+1}，{@code emin <= E <= emax}。
     * 次正规数满足 {@code 1 <= M < 2^(p-1)}，且 {@code q = emin-p+1}。
     * 零固定为 {@code M = 0}、{@code q = emin-p+1}，由符号位区分正负零。
     * 不得任意移走 M 中的二进制因子，该结果不是原始指数字段或正常数的无偏指数 E。
     *
     * @throws IllegalStateException 值为无穷或 NaN
     */
    long scaleExponent();

    /**
     * 返回 {@link #scaleExponent()} 定义的 M 的一个原始 64 位分组，M 不包含符号位或指数编码。
     * 分组数量 {@code G = (p + 63L) / 64}，索引 0 为最高组，{@code G-1} 为最低组。
     * 最高组超出 p 位的部分必须为零，零的全部分组为零，单组 long 的符号不改变 M 的非负性质。
     *
     * @throws IllegalStateException 值为无穷或 NaN
     * @throws IndexOutOfBoundsException 有限值的索引小于 0 或不小于 G
     */
    long getSignificandLong(int index);

    /**
     * 返回数值四态比较结果，正负零相等，同号无穷相等。
     * 任一操作数为 NaN 时返回 {@link Comparison#UNORDERED}，包括 NaN 与自身比较。
     */
    Comparison compareNumerically(T other);

    /**
     * 按负无穷、负有限数、负零、正零、正有限数、正无穷、NaN 的顺序排序，有限非零数按数值排序。
     * 所有 NaN 排序相等，正负零排序不同；返回值仅以正负和零表达关系。
     * 同一实现类型内，返回零当且仅当 {@code equals(other)} 为 true。
     * 此排序不区分 NaN 编码，不是区分全部 NaN 编码的 IEEE totalOrder。
     */
    @Override
    int compareTo(T other);

    /**
     * 仅对同一实现类型判断相等，普通值按数值相等，正负零不相等，所有 NaN 彼此相等。
     * null 和其他类型均返回 false，与 {@link #compareNumerically(Binary)} 的数值相等规则不同。
     */
    @HaveToImplements
    boolean equals(Object obj);

    /** 与 equals 相等的值须返回相同的 hash，所有 NaN 忽略符号及 payload 差异，具体公式由实现类型决定。 */
    @HaveToImplements
    int hashCode();

    /**
     * 返回最短可往返十进制数的普通表示，不使用指数或区域格式。
     * 在能由当前实现的 of(String) 最近偶数舍入恢复原值的十进制数中，先最小化有效数字位数，
     * 再选择最接近原值者，距离相等时选择末位有效数字为偶数者。
     * 去除多余前导零、小数部分末尾零和无用小数点；零返回 "0" 或 "-0"。
     * 无穷返回 "Infinity" 或 "-Infinity"，所有 NaN 返回 "NaN"，不保留 NaN 编码信息。
     * 普通表示可能因指数范围很大而包含大量补零。
     */
    @HaveToImplements
    @Override
    String toString();

    /**
     * 将当前精确二进制数值直接按最近偶数规则舍入为普通十进制，小数点后固定 fractionDigits 位。
     * 不足的位数补零，fractionDigits 为零时省略小数点，不使用指数或区域格式。
     * 保留负号，包括负零和负数舍入得到的零；无穷和 NaN 的拼写与 toString() 相同。
     * 不先经过最短十进制表示，不承诺解析后恢复原值。
     *
     * @throws IllegalArgumentException fractionDigits 小于零（包括特殊值），或输出长度超过 Integer.MAX_VALUE
     */
    String toString(int fractionDigits);

    /**
     * 将与 toString() 相同的最短可往返十进制数写为科学计数法。
     * 非零有限数的小数点前恰好一位，小数点后至少一位，使用小写 e，
     * 指数不带多余前导零或正号，例如 "1.0e0"、"1.235e2"、"1.25e-3"。
     * 正负零分别返回 "0.0e0" 和 "-0.0e0"，无穷和 NaN 的拼写与 toString() 相同。
     */
    String toScientificString();

    /**
     * 将当前精确二进制数值直接按最近偶数规则舍入为科学计数法，尾数小数点后固定 fractionDigits 位。
     * 非零结果小数点前恰好一位，不足位数补零；进位后重新归一化并调整指数。
     * fractionDigits 为零时省略小数点；固定使用小写 e，指数不带多余前导零或正号。
     * 保留负号，正负零的指数均为零，例如 "-0e0" 或 "-0.00e0"。
     * 无穷和 NaN 的拼写与 toString() 相同；不先经过最短十进制表示，不承诺解析后恢复原值。
     *
     * @throws IllegalArgumentException fractionDigits 小于零（包括特殊值），或输出长度超过 Integer.MAX_VALUE
     */
    String toScientificString(int fractionDigits);

    /**
     * 对精确和进行一次舍入，异号非零数精确抵消时得到正零，同号零相加保留符号。
     * 正负零相加得到正零，异号无穷相加得到 NaN。
     */
    T add(T other);

    /** 按接收值加上参数取负后的数值计算差，零符号规则与 add 一致，同号无穷相减得到 NaN。 */
    T subtract(T other);

    /** 对精确积进行一次舍入，非 NaN 结果的符号为操作数符号异或，零乘无穷得到 NaN。 */
    T multiply(T other);

    /**
     * 对精确商进行一次舍入，非 NaN 结果的符号为操作数符号异或。
     * 非零有限数除以零得到对应无穷，有限数除以无穷得到对应零，零除以零或无穷除以无穷得到 NaN。
     */
    T divide(T other);

    /**
     * 返回 {@code this - n * other}，n 为精确商向零截断后的数学整数，对应 Java 浮点余数。
     * 任一操作数为 NaN、被除数为无穷或除数为零时返回 NaN；有限数对无穷取余返回被除数。
     * 零余数保留被除数符号，确定 n 时不得使用已经舍入的 divide 结果。
     */
    T remainder(T other);

    /**
     * 返回 {@code this - n * other}，n 为精确商的最近数学整数，中点取偶数。
     * NaN、无穷、零除数和零余数符号的处理与 remainder 一致，但两者选取 n 的规则不同。
     * 例如五对三的 IEEE 余数为负一，Java 浮点余数为二。
     */
    T ieeeRemainder(T other);

    /** 改变有限数和无穷的符号，交换正负零，NaN 返回 NaN 而不保证其编码信息。 */
    T negate();

    /** 返回绝对值，将负零变为正零、负无穷变为正无穷，NaN 返回 NaN。 */
    T abs();

    /**
     * 精确计算 {@code this * multiplier + addend} 后仅舍入一次，不等价于先调用 multiply 再调用 add。
     * 零乘无穷或无穷乘积加上异号无穷时返回 NaN，零结果按精确乘积与加数的浮点加法规则确定符号。
     */
    T fma(T multiplier, T addend);

    /** 返回正确舍入的非负平方根，负的非零数返回 NaN，负零保留负号，正无穷返回正无穷。 */
    T sqrt();

    /**
     * 精确缩放为 {@code this * 2^scaleFactor} 后直接舍入，指数不取模，全部 long 参数均有效。
     * 保留零和无穷的符号；有限非零结果上溢、下溢或舍入为零时按目标格式处理。
     * 内部指数加减不得因 long 溢出而改变结果。
     */
    T scalb(long scaleFactor);

    /**
     * 返回朝正无穷方向的相邻数值，正负零均得到 MIN_VALUE，负 MIN_VALUE 得到负零。
     * 正无穷保持不变，负无穷得到最负有限数，最大有限正数得到正无穷，NaN 返回 NaN。
     */
    T nextUp();

    /**
     * 返回朝负无穷方向的相邻数值，正负零均得到负 MIN_VALUE，MIN_VALUE 得到正零。
     * 负无穷保持不变，正无穷得到最大有限正数，最负有限数得到负无穷，NaN 返回 NaN。
     */
    T nextDown();

    /** 直接舍入到 binary32，保留零和无穷的符号，NaN 转为 float NaN，不得先经 toDouble 转换。 */
    float toFloat();

    /** 直接舍入到 binary64，保留零和无穷的符号，NaN 转为 double NaN。 */
    double toDouble();

    /**
     * 向零截断到 int，越界和正负无穷分别钳制到对应 int 极值，NaN 和正负零转换为零。
     * 不得先调用 toLong 再强制转换，以免用低位截断代替目标范围钳制。
     */
    int toInt();

    /** 向零截断到 long，越界和正负无穷分别钳制到对应 long 极值，NaN 和正负零转换为零。 */
    long toLong();
}
