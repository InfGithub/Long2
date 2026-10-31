package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import javax.tools.Diagnostic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeepContractTypeTest extends ProcessorTestSupport {
    private static final String CONTRACT = """
            @StaticMethodImplementsCheck
            interface Contract<T extends Contract<T>> {
                static Contract<?> of(@KeepContractType Contract<?> value) { return null; }
            }
            """;

    @Test
    void preservesSourceParameterAndSubstitutesReturnType() throws IOException {
        assertCompiles(CONTRACT + """
                class Impl implements Contract<Impl> {
                    public static Impl of(Contract<?> value) { return null; }
                }
                class Other implements Contract<Other> {
                    public static Other of(Contract<?> value) { return null; }
                }
                class Caller { Impl value = Impl.of(new Other()); }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "public static Impl of(Impl value) { return null; }",
            "public static Impl of(Object value) { return null; }",
            "public static Impl of(Contract value) { return null; }",
            "public static Impl of(Contract<Impl> value) { return null; }",
            "public static Contract<?> of(Contract<?> value) { return null; }",
            "static Impl of(Contract<?> value) { return null; }",
            "public Impl of(Contract<?> value) { return null; }"
    })
    void rejectsWrongSourceReturnOrModifiers(String method) throws IOException {
        Compilation result = compile(CONTRACT + "class Impl implements Contract<Impl> { " + method + " }");
        assertMissing(result, "public static fixture.Impl of(fixture.Contract<?> value)");
    }

    @Test
    void scopesPreservationToOneParameter() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static Contract merge(@KeepContractType Contract source, Contract target) { return null; }
                }
                class Impl implements Contract {
                    public static Impl merge(Contract source, Impl target) { return null; }
                }
                """);
    }

    @Test
    void rejectsPreservationLeakingToOtherParameters() throws IOException {
        Compilation result = compile("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static Contract merge(@KeepContractType Contract source, Contract target) { return null; }
                }
                class Impl implements Contract {
                    public static Impl merge(Contract source, Contract target) { return null; }
                }
                """);
        assertMissing(result, "public static fixture.Impl merge(fixture.Contract source, fixture.Impl target)");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Contract<?>[][]",
            "java.util.List<? extends Contract<?>[]>",
            "java.util.List<? super Contract<?>>",
            "java.util.Map<String, java.util.List<Contract<?>>>",
            "Holder<Contract<?>>.Inner<String>"
    })
    void preservesNestedTypes(String type) throws IOException {
        assertCompiles("""
                class Holder<T> { class Inner<U> {} }
                @StaticMethodImplementsCheck
                interface Contract<T extends Contract<T>> {
                    static Contract<?> of(@KeepContractType %s value) { return null; }
                }
                class Impl implements Contract<Impl> {
                    public static Impl of(%s value) { return null; }
                }
                """.formatted(type, type));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java.util.List<? extends Impl[]>",
            "java.util.List<? extends Object[]>",
            "java.util.List<? super Contract<?>[]>",
            "java.util.List<Contract<?>[]>",
            "java.util.List",
            "java.util.ArrayList<? extends Contract<?>[]>"
    })
    void rejectsNestedTypeMismatchAndDisplaysPreservedTypes(String actualType) throws IOException {
        Compilation result = compile("""
                @StaticMethodImplementsCheck
                interface Contract<T extends Contract<T>> {
                    static Contract<?> of(@KeepContractType java.util.List<? extends Contract<?>[]> value) {
                        return null;
                    }
                }
                class Impl implements Contract<Impl> {
                    public static Impl of(%s value) { return null; }
                }
                """.formatted(actualType));
        assertMissing(result,
                "public static fixture.Impl of(java.util.List<? extends fixture.Contract<?>[]> value)");
    }

    @Test
    void preservesEnclosingTypeIdentity() throws IOException {
        Compilation result = compile("""
                class Holder<T> { class Inner<U> {} }
                @StaticMethodImplementsCheck
                interface Contract<T extends Contract<T>> {
                    static Contract<?> of(@KeepContractType Holder<Contract<?>>.Inner<String> value) {
                        return null;
                    }
                }
                class Impl implements Contract<Impl> {
                    public static Impl of(Holder<Impl>.Inner<String> value) { return null; }
                }
                """);
        assertMissing(result,
                "public static fixture.Impl of(fixture.Holder<fixture.Contract<?>>.Inner<java.lang.String> value)");
    }

    @Test
    void preservesVarargsAndReturnTypeSubstitutionRecursively() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static java.util.List<? extends Contract[]> of(@KeepContractType Contract... values) {
                        return null;
                    }
                }
                class Impl implements Contract {
                    public static java.util.List<? extends Impl[]> of(Contract... values) { return null; }
                }
                """);
    }

    @Test
    void rejectsArrayInPlaceOfVarargs() throws IOException {
        Compilation result = compile("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static Contract of(@KeepContractType Contract... values) { return null; }
                }
                class Impl implements Contract {
                    public static Impl of(Contract[] values) { return null; }
                }
                """);
        assertMissing(result, "public static fixture.Impl of(fixture.Contract... values)");
    }

    @Test
    void matchesRenamedMethodTypeParametersByPosition() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static <T extends Comparable<T>> Contract of(
                            @KeepContractType java.util.Map<Contract, T> source, T key) { return null; }
                }
                class Impl implements Contract {
                    public static <U extends Comparable<U>> Impl of(java.util.Map<Contract, U> source, U key) {
                        return null;
                    }
                }
                """);
    }

    @Test
    void substitutesGenericBoundsIndependentlyOfParameterMode() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static <T extends Contract> Contract of(@KeepContractType T value) { return null; }
                }
                class Impl implements Contract {
                    public static <U extends Impl> Impl of(U value) { return null; }
                }
                """);
        Compilation invalid = compile("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static <T extends Contract> Contract of(@KeepContractType T value) { return null; }
                }
                class Impl implements Contract {
                    public static <U extends Contract> Impl of(U value) { return null; }
                }
                """);
        assertMissing(invalid, "public static <T extends fixture.Impl> fixture.Impl of(T value)");
    }

    @Test
    void keepsUnannotatedParameterSubstitution() throws IOException {
        assertCompiles("""
                @StaticMethodImplementsCheck
                interface Contract {
                    static Contract copy(Contract value) { return null; }
                }
                class Impl implements Contract {
                    public static Impl copy(Impl value) { return null; }
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "interface Invalid { static void method(@KeepContractType Object value) {} }",
            "@StaticMethodImplementsCheck interface Invalid { void method(@KeepContractType Object value); }",
            "@StaticMethodImplementsCheck interface Invalid { default void method(@KeepContractType Object value) {} }",
            "@StaticMethodImplementsCheck interface Invalid { private static void method(@KeepContractType Object value) {} }",
            "@StaticMethodImplementsCheck interface Invalid { private void method(@KeepContractType Object value) {} }",
            "class Invalid { public static void method(@KeepContractType Object value) {} }",
            "class Invalid { public void method(@KeepContractType Object value) {} }",
            "class Invalid { Invalid(@KeepContractType Object value) {} }"
    })
    void rejectsInvalidDeclarationsWithoutImplementations(String source) throws IOException {
        Compilation result = compile(source);
        assertInvalidParameter(result);
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR
                && diagnostic.getLineNumber() > 0), result.messages());
    }

    @Test
    void rejectsAnnotationOnImplementationParameter() throws IOException {
        Compilation result = compile(CONTRACT + """
                class Impl implements Contract<Impl> {
                    public static Impl of(@KeepContractType Contract<?> value) { return null; }
                }
                """);
        assertInvalidParameter(result);
        assertFalse(result.messages().contains("must declare:"), result.messages());
    }

    @Test
    void readsPreservationFromDependencyJar() throws IOException {
        Compilation dependency = compile(CONTRACT, false, null);
        assertTrue(dependency.success(), dependency.messages());
        var jar = createJar(dependency.output());
        Compilation valid = compile("""
                interface Child extends Contract<Impl> {}
                class Impl implements Child {
                    public static Impl of(Contract<?> value) { return null; }
                }
                """, true, jar);
        assertTrue(valid.success(), valid.messages());

        Compilation invalid = compile("""
                class Impl implements Contract<Impl> {
                    public static Impl of(Impl value) { return null; }
                }
                """, true, jar);
        assertMissing(invalid, "public static fixture.Impl of(fixture.Contract<?>");
    }

    @Test
    void reportsInvalidDependencyParameterOnlyOnce() throws IOException {
        Compilation dependency = compile("""
                class Invalid { public static void method(@KeepContractType Object value) {} }
                """, false, null);
        assertTrue(dependency.success(), dependency.messages());
        Compilation result = compile("class First extends Invalid {} class Second extends Invalid {}",
                true, createJar(dependency.output()));
        assertInvalidParameter(result);
        assertEquals(1L, result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).count(), result.messages());
    }

    private static void assertMissing(Compilation result, String signature) {
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: " + signature), result.messages());
    }

    private static void assertInvalidParameter(Compilation result) {
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("may only annotate parameters of public static methods"), result.messages());
    }
}
