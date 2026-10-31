# Long2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Java 25](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)

## Maven 仓库

仓库地址：`https://axorbisinfinitive.github.io/Long2/maven/`。

Gradle：

```groovy
repositories {
    maven {
        url = uri("https://axorbisinfinitive.github.io/Long2/maven/")
        content {
            includeModule("com.inf.long2", "Long2")
        }
    }
}

dependencies {
    implementation("com.inf.long2:Long2:0.1.1")
}
```

Fabric 工程需要将库作为 Jar-in-Jar 分发时，在已有 Loom 配置中加入：

```groovy
dependencies {
    implementation("com.inf.long2:Long2:0.1.1")
    include("com.inf.long2:Long2:0.1.1")
}
```

`implementation` 提供编译和开发运行依赖，`include` 将库嵌入最终 Mod。Long2 为普通 Java 库，目标游戏 JVM 需要 Java 25 或更高版本。

Maven：

```xml
<repositories>
    <repository>
        <id>long2</id>
        <url>https://axorbisinfinitive.github.io/Long2/maven/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.inf.long2</groupId>
        <artifactId>Long2</artifactId>
        <version>0.1.1</version>
    </dependency>
</dependencies>
```

## 发布

发布稳定的 `v主版本.次版本.修订版本` Release 并上传对应的 `Long2-版本.jar` 后，GitHub Actions 自动更新 Maven 仓库。也可以手动运行 `Publish Maven repository`，指定已发布标签。

发布使用 Release 中的原始 JAR、标签内的 POM 和源码，生成 `sources.jar`、版本元数据及校验和。`maven-repository` 分支保留历史制品，同一版本已有文件的内容不能覆盖。

本仓库目前发布数值库。正常使用数值 API 不需要 AP；静态契约检查器通过独立的 `annotationProcessor` 配置使用。

[如何使用 AP 静态方法实现检查器](docs/using_static_method_implements_check.md)

- [Int256.java](src/main/java/com/inf/long2/ints/Int256.java)
- [Binary256.java](src/main/java/com/inf/long2/binaries/Binary256.java)
