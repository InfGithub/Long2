package com.inf.long2.util.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求每个具体实现类直接声明同名的 public static 字段，继承的字段不满足契约。
 * 字段类型中的契约接口类型替换为实现类类型，其余类型严格匹配。
 * 实现类字段的 final 修饰符与初始化值不参与检查。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface StaticFieldImplementsCheck {
}
