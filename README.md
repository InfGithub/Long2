# Long2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)

[如何使用 AP 静态方法实现检查器](docs/using_static_method_implements_check.md)

- [Int256.java](src/main/java/com/inf/long2/ints/Int256.java)
- [Binary256.java](src/main/java/com/inf/long2/binaries/Binary256.java)

## Binary256 十进制转换

```java
Binary256 value = Binary256.of("123.5"); // 也接受 "1.235e2" 和 "1.235E2"
value.toString();             // "123.5"，普通十进制最短可往返表示
value.toScientificString();   // "1.235e2"，固定使用小写 e
value.toString(2);            // "123.50"，小数点后固定两位
value.toScientificString(2);  // "1.24e2"，尾数小数点后固定两位
```

带参数的重载从当前存储的精确二进制值直接按最近偶数规则舍入，不足位数补零，零位小数时省略小数点。
科学计数法发生进位时调整指数。保留负号（包括负零和舍入得到的负零），特殊值仍输出 `NaN`、`Infinity`、`-Infinity`。
负的小数位数或超过字符串长度上限的输出抛出 `IllegalArgumentException`；指定位数的输出不承诺往返恢复原值。

## 作为依赖

**仓库地址**：`https://axorbisinfinitive.github.io/Long2/maven/`。
