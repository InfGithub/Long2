package com.inf.long2.util.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 保留静态方法契约中一个参数的完整声明类型，该参数内的契约接口类型不替换为实现类类型。
 * 数组元素、泛型参数、通配符边界和外部类型递归沿用保留规则，其余签名位置不受影响。
 * 仅用于标有 {@link StaticMethodImplementsCheck} 的接口中 public static 方法的参数，
 * 实现类参数无须重复标注，仍须严格匹配契约要求的类型。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface KeepContractType {
}
