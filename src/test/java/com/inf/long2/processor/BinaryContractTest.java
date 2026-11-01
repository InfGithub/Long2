package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.inf.long2.Binary;

class BinaryContractTest extends ProcessorTestSupport {
    private static final String DECLARATIONS = """
            public static final com.inf.long2.Binary.Format FORMAT = null;
            public static final Target ZERO = null;
            public static final Target NEGATIVE_ZERO = null;
            public static final Target ONE = null;
            public static final Target MIN_VALUE = null;
            public static final Target MIN_NORMAL = null;
            public static final Target MAX_VALUE = null;
            public static final Target POSITIVE_INFINITY = null;
            public static final Target NEGATIVE_INFINITY = null;
            public static final Target NaN = null;
            public static Target of(long value) { return null; }
            public static Target of(float value) { return null; }
            public static Target of(double value) { return null; }
            public static Target of(java.lang.String value) { return null; }
            public static Target of(byte[] value) { return null; }
            public static Target of(com.inf.long2.Int<?> value) { return null; }
            public static Target of(com.inf.long2.Binary<?> value) { return null; }
            public static boolean isValid(java.lang.String value) { return false; }
            """;

    @Test
    void acceptsContractAndCrossFormatCallsWithoutNumericImplementation() throws IOException {
        assertCompiles(fixture(DECLARATIONS) + fixture(DECLARATIONS).replace("Target", "Peer") + """
                class Caller {
                    static Target convert(long integer, float single, double binary64,
                            com.inf.long2.Int<?> wide, Peer peer) {
                        Target a = Target.of(integer);
                        Target b = Target.of(single);
                        Target c = Target.of(binary64);
                        Target d = Target.of(wide);
                        return Target.of(peer);
                    }
                    static <T extends com.inf.long2.Binary<T>> int compare(T left, T right) {
                        return switch (left.compareNumerically(right)) {
                            case LESS -> -1;
                            case EQUAL -> 0;
                            case GREATER -> 1;
                            case UNORDERED -> 2;
                        };
                    }
                    static <T extends com.inf.long2.Binary<T>> String format(T value, int digits) {
                        return value.toString(digits) + value.toScientificString(digits);
                    }
                    static byte[] bytes(com.inf.long2.Binary<?> value) {
                        return value.toBytes();
                    }
                    static Target restore(byte[] value) {
                        return Target.of(value);
                    }
                    static boolean valid(String value) {
                        return Target.isValid(value);
                    }
                    static <T extends com.inf.long2.Binary<T>> T ulp(T value) {
                        return value.ulp();
                    }
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FORMAT", "ZERO", "NEGATIVE_ZERO", "ONE", "MIN_VALUE", "MIN_NORMAL",
            "MAX_VALUE", "POSITIVE_INFINITY", "NEGATIVE_INFINITY", "NaN"
    })
    void rejectsMissingDirectConstant(String name) throws IOException {
        String declarations = DECLARATIONS.lines()
                .filter(line -> !line.contains(" " + name + " ="))
                .collect(Collectors.joining("\n"));
        Compilation result = compile(fixture(declarations));
        assertFalse(result.success(), result.messages());
        String type = name.equals("FORMAT") ? "com.inf.long2.Binary.Format" : "fixture.Target";
        assertTrue(result.messages().contains("must declare: public static " + type + " " + name), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"long", "float", "double", "java.lang.String", "byte[]", "com.inf.long2.Int<?>", "com.inf.long2.Binary<?>"})
    void rejectsMissingStaticFactory(String parameterType) throws IOException {
        String declarations = DECLARATIONS.replace(
                "public static Target of(" + parameterType + " value) { return null; }", "");
        assertMissingFactory(compile(fixture(declarations)), parameterType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Target", "Object", "com.inf.long2.Binary", "com.inf.long2.Binary<Target>"})
    void rejectsChangedCrossFormatSourceType(String parameterType) throws IOException {
        String declarations = DECLARATIONS.replace("of(com.inf.long2.Binary<?> value)",
                "of(" + parameterType + " value)");
        assertMissingFactory(compile(fixture(declarations)), "com.inf.long2.Binary<?>");
    }

    @Test
    void rejectsContractReturnTypeInsteadOfTargetType() throws IOException {
        String declarations = DECLARATIONS.replace("Target of(com.inf.long2.Binary<?> value)",
                "com.inf.long2.Binary<?> of(com.inf.long2.Binary<?> value)");
        assertMissingFactory(compile(fixture(declarations)), "com.inf.long2.Binary<?>");
    }

    @Test
    void rejectsWrongFormatFieldType() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS.replace(
                "com.inf.long2.Binary.Format FORMAT", "Object FORMAT")));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static com.inf.long2.Binary.Format FORMAT"),
                result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"equals", "hashCode", "toString"})
    void requiresExplicitObjectMethodContracts(String methodName) throws IOException {
        Compilation result = compile(fixture(DECLARATIONS, methodName));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must explicitly declare an instance method overriding:"), result.messages());
        assertTrue(result.messages().contains(methodName + "("), result.messages());
    }

    private static void assertMissingFactory(Compilation result, String parameterType) {
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static fixture.Target of(" + parameterType + " "),
                result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrongReturn", "wrongParameter", "inherited"})
    void requiresDirectStaticStringValidation(String variant) throws IOException {
        String declaration = "public static boolean isValid(java.lang.String value) { return false; }";
        String replacement = switch (variant) {
            case "wrongReturn" -> "public static java.lang.Boolean isValid(java.lang.String value) { return false; }";
            case "wrongParameter" -> "public static boolean isValid(java.lang.Object value) { return false; }";
            default -> "";
        };
        String source = fixture(DECLARATIONS.replace(declaration, replacement));
        if (variant.equals("inherited")) {
            source = "class Base { " + declaration + " }\n"
                    + source.replace("class Target implements", "class Target extends Base implements");
        }
        Compilation result = compile(source);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static boolean isValid(java.lang.String "),
                result.messages());
    }

    @Test
    void requiresTheRawByteConversion() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS, "toBytes"));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("toBytes()"), result.messages());
    }

    @Test
    void requiresTheUlpInstanceMethod() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS, "ulp"));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("ulp()"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"toString", "toScientificString"})
    void requiresTheFixedFractionOverloads(String methodName) throws IOException {
        String declaration = "public java.lang.String " + methodName
                + "(int value0) { throw new UnsupportedOperationException(); }\n";
        String source = fixture(DECLARATIONS);
        assertTrue(source.contains(declaration));
        Compilation result = compile(source.replace(declaration, ""));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains(methodName + "(int)"), result.messages());
    }

    private static String fixture(String declarations) {
        return fixture(declarations, "");
    }

    private static String fixture(String declarations, String omittedMethod) {
        StringBuilder source = new StringBuilder("class Target implements com.inf.long2.Binary<Target> {\n");
        source.append(declarations);
        // 实例方法仅用于通过声明检查，调用即失败，避免把编译夹具误用作浮点实现。
        for (Method method : Binary.class.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || method.isSynthetic()
                    || method.getName().equals(omittedMethod)) {
                continue;
            }
            source.append("public ").append(typeName(method.getGenericReturnType())).append(' ')
                    .append(method.getName()).append('(');
            Type[] parameters = method.getGenericParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    source.append(", ");
                }
                source.append(typeName(parameters[i])).append(" value").append(i);
            }
            source.append(") { throw new UnsupportedOperationException(); }\n");
        }
        return source.append("}\n").toString();
    }

    private static String typeName(Type type) {
        if (type instanceof TypeVariable<?>) {
            return "Target";
        }
        if (type instanceof Class<?> raw) {
            return raw.getCanonicalName();
        }
        throw new IllegalArgumentException("Unsupported fixture type: " + type);
    }
}
