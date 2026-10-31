package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import javax.tools.Diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StaticFieldImplementsCheckTest extends ProcessorTestSupport {
    private static final String CONTRACT = """
            interface Contract<T extends Contract<T>> {
                @StaticFieldImplementsCheck Contract<?> ZERO = null;
            }
            """;

    @ParameterizedTest
    @ValueSource(strings = {
            "class Impl implements Contract<Impl> { public static final Impl ZERO = new Impl(); }",
            "class Impl implements Contract<Impl> { public static Impl ZERO = null; }",
            "enum Impl implements Contract<Impl> { VALUE; public static final Impl ZERO = VALUE; }",
            "record Impl(int value) implements Contract<Impl> { public static final Impl ZERO = new Impl(0); }",
            "abstract class Base implements Contract<Impl> {} class Impl extends Base { public static Impl ZERO; }",
            "interface Child extends Contract<Impl> {} class Impl implements Child { public static Impl ZERO; }",
            "interface Left extends Contract<Impl> {} interface Right extends Contract<Impl> {} "
                    + "class Impl implements Left, Right { public static Impl ZERO; }",
            "class Outer { static class Impl implements Contract<Impl> { public static Impl ZERO; } }"
    })
    void acceptsDirectDeclarations(String implementation) throws IOException {
        assertCompiles(CONTRACT + implementation);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Impl implements Contract<Impl> {}",
            "class Impl implements Contract<Impl> { public static Impl ZERO() { return null; } }",
            "class Impl implements Contract<Impl> { private static Impl ZERO; }",
            "class Impl implements Contract<Impl> { static Impl ZERO; }",
            "class Impl implements Contract<Impl> { public Impl ZERO; }",
            "class Impl implements Contract<Impl> { public static Contract<?> ZERO; }",
            "class Impl implements Contract<Impl> { public static Object ZERO; }",
            "class Impl implements Contract<Impl> { public static Impl OTHER; }",
            "class Base { public static Impl ZERO; } class Impl extends Base implements Contract<Impl> {}",
            "class Base implements Contract<Base> { public static Base ZERO; } class Impl extends Base {}",
            "interface Child extends Contract<Impl> {} class Impl implements Child {}",
            "enum Impl implements Contract<Impl> { ZERO }",
            "record Impl(int value) implements Contract<Impl> {}"
    })
    void rejectsMissingOrMismatchedDeclarations(String implementation) throws IOException {
        Compilation result = compile(CONTRACT + implementation);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static fixture.Impl ZERO"), result.messages());
    }

    @Test
    void checksOnlyAnnotatedFieldsAndSkipsAbstractClasses() throws IOException {
        assertCompiles("""
                interface Contract {
                    @StaticFieldImplementsCheck Contract ZERO = null;
                    String NAME = "contract";
                }
                abstract class Base implements Contract {}
                class Impl implements Contract { public static Impl ZERO; }
                """);
    }

    @Test
    void replacesSelfTypesRecursively() throws IOException {
        assertCompiles("""
                interface Contract {
                    @StaticFieldImplementsCheck Contract[][] ARRAY = null;
                    @StaticFieldImplementsCheck java.util.List<? extends Contract[]> UPPER = null;
                    @StaticFieldImplementsCheck java.util.List<? super Contract> LOWER = null;
                    @StaticFieldImplementsCheck java.util.Map<String, java.util.List<Contract>> NESTED = null;
                    @StaticFieldImplementsCheck Holder<Contract>.Inner<Integer> INNER = null;
                    @StaticFieldImplementsCheck int COUNT = 1;
                }
                class Holder<T> { class Inner<U> {} }
                class Impl implements Contract {
                    public static Impl[][] ARRAY;
                    public static java.util.List<? extends Impl[]> UPPER;
                    public static java.util.List<? super Impl> LOWER;
                    public static java.util.Map<String, java.util.List<Impl>> NESTED;
                    public static Holder<Impl>.Inner<Integer> INNER;
                    public static int COUNT = 2;
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java.util.List<Impl>",
            "java.util.List<? super Impl[]>",
            "java.util.List<? extends Contract[]>",
            "java.util.List<? extends Object[]>",
            "java.util.List",
            "java.util.ArrayList<? extends Impl[]>"
    })
    void rejectsDifferentNestedTypes(String actualType) throws IOException {
        Compilation result = compile("""
                interface Contract {
                    @StaticFieldImplementsCheck java.util.List<? extends Contract[]> VALUES = null;
                }
                class Impl implements Contract { public static %s VALUES; }
                """.formatted(actualType));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("java.util.List<? extends fixture.Impl[]> VALUES"), result.messages());
    }

    @Test
    void reportsOneErrorForDiamondInheritance() throws IOException {
        Compilation result = compile(CONTRACT + """
                interface Left extends Contract<Impl> {}
                interface Right extends Contract<Impl> {}
                class Impl implements Left, Right {}
                """);
        assertFalse(result.success());
        assertEquals(1L, result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).count(), result.messages());
        assertTrue(result.messages().contains("must declare: public static fixture.Impl ZERO"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"class Format {}", "record Format(int precision) {}", "enum Format { VALUE }",
            "interface Format {}", "@interface Format {}"})
    void acceptsInheritedStaticNestedTypeNames(String declaration) throws IOException {
        assertCompiles("""
                interface Contract {
                    %s
                    @StaticFieldImplementsCheck Format FORMAT = null;
                }
                class Impl implements Contract {
                    public static final Format FORMAT = null;
                }
                """.formatted(declaration));
    }

    @Test
    void displaysStaticNestedDeclarationOwner() throws IOException {
        Compilation result = compile("""
                interface Contract {
                    record Format(int precision) {}
                    @StaticFieldImplementsCheck Format FORMAT = null;
                }
                class Impl implements Contract {}
                """);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static fixture.Contract.Format FORMAT"), result.messages());
        assertFalse(result.messages().contains("fixture.Impl.Format"), result.messages());
    }

    @Test
    void checksStaticNestedGenericArguments() throws IOException {
        assertCompiles("""
                interface Contract {
                    class Holder<T> {}
                    @StaticFieldImplementsCheck Holder<Contract> VALUE = null;
                }
                class Impl implements Contract {
                    public static final Holder<Impl> VALUE = null;
                }
                """);
        Compilation result = compile("""
                interface Contract {
                    class Holder<T> {}
                    @StaticFieldImplementsCheck Holder<Contract> VALUE = null;
                }
                class Impl implements Contract {
                    public static final Holder<Contract> VALUE = null;
                }
                """);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("fixture.Contract.Holder<fixture.Impl> VALUE"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Invalid { @StaticFieldImplementsCheck public static Invalid ZERO; }",
            "enum Invalid { @StaticFieldImplementsCheck ZERO }",
            "@interface Invalid { @StaticFieldImplementsCheck String NAME = \"invalid\"; }"
    })
    void rejectsInvalidAnnotationOwnersWithoutImplementations(String source) throws IOException {
        Compilation result = compile(source);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("may only annotate public static fields declared in interfaces"),
                result.messages());
    }

    @Test
    void checksContractsFromDependencyJar() throws IOException {
        Compilation dependency = compile(CONTRACT, false, null);
        assertTrue(dependency.success(), dependency.messages());
        Path jar = createJar(dependency.output());

        Compilation valid = compile("class Impl implements Contract<Impl> { public static Impl ZERO; }", true, jar);
        assertTrue(valid.success(), valid.messages());
        Compilation invalid = compile("class Impl implements Contract<Impl> {}", true, jar);
        assertFalse(invalid.success(), invalid.messages());
        assertTrue(invalid.messages().contains("must declare: public static fixture.Impl ZERO"), invalid.messages());
    }

    @Test
    void rejectsInvalidContractFromDependencyJar() throws IOException {
        Compilation dependency = compile("""
                class Invalid { @StaticFieldImplementsCheck public static Invalid ZERO; }
                """, false, null);
        assertTrue(dependency.success(), dependency.messages());
        Compilation result = compile("class Impl extends Invalid {}", true, createJar(dependency.output()));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("may only annotate public static fields declared in interfaces"),
                result.messages());
    }

    @Test
    void preservesMethodContractsWithGenericTypeParameters() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static <T extends Comparable<T>> java.util.List<? extends Contract> build(T[] values) {
                        return null;
                    }
                    @HaveToImplements String toString();
                }
                class Impl implements Contract {
                    public static <U extends Comparable<U>> java.util.List<? extends Impl> build(U[] values) {
                        return null;
                    }
                    public String toString() { return "value"; }
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public static <U> Impl build(U[] values) { return null; }",
            "public static <U extends Comparable<U>> Impl build(U... values) { return null; }",
            "public static <U extends Comparable<U>> Contract build(U[] values) { return null; }"
    })
    void preservesStaticMethodMismatchChecks(String actualMethod) throws IOException {
        Compilation result = compile("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static <T extends Comparable<T>> Contract build(T[] values) { return null; }
                }
                class Impl implements Contract { %s }
                """.formatted(actualMethod));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static"), result.messages());
    }

    @Test
    void preservesExplicitInstanceMethodChecks() throws IOException {
        Compilation result = compile("""
                interface Contract { @HaveToImplements String toString(); }
                class Impl implements Contract {}
                """);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must explicitly declare an instance method overriding"), result.messages());
    }

}
