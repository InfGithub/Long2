package com.inf.long2.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Elements.Origin;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
public final class StaticMethodImplementsCheckProcessor
        extends AbstractProcessor {

    private static final String ANNOTATION_NAME = "com.inf.long2.util.annotations.StaticMethodImplementsCheck";

    private static final String HAVE_TO_IMPLEMENTS_ANNOTATION_NAME = "com.inf.long2.util.annotations.HaveToImplements";

    private static final String STATIC_FIELD_ANNOTATION_NAME = "com.inf.long2.util.annotations.StaticFieldImplementsCheck";

    private static final String KEEP_CONTRACT_TYPE_ANNOTATION_NAME = "com.inf.long2.util.annotations.KeepContractType";

    private enum SelfTypeMode {
        SUBSTITUTE,
        PRESERVE
    }

    private final Set<String> checkedImplementations = new HashSet<>();
    private final Set<String> reportedInvalidContracts = new HashSet<>();
    private final Set<String> reportedInvalidInstanceContracts = new HashSet<>();
    private final Set<String> reportedInvalidFieldContracts = new HashSet<>();
    private final Set<String> reportedInvalidParameterContracts = new HashSet<>();

    private Types types;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        types = environment.getTypeUtils();
        elements = environment.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment round) {

        if (round.processingOver()) {
            return false;
        }

        for (Element root : round.getRootElements()) {
            visit(root);
        }

        return false;
    }

    private void visit(Element element) {
        if (!(element instanceof TypeElement type)) {
            return;
        }

        if (hasContractAnnotation(type)
                && type.getKind() != ElementKind.INTERFACE) {
            reportInvalidContractType(type);
        }

        validateAnnotatedInstanceMethods(type);
        validateAnnotatedFields(type);
        validateKeepContractTypeParameters(type);

        if (isConcreteType(type)) {
            String name = type.getQualifiedName().toString();

            if (checkedImplementations.add(name)) {
                validate(type);
            }
        }

        for (Element child : type.getEnclosedElements()) {
            if (child instanceof TypeElement) {
                visit(child);
            }
        }
    }

    private boolean isConcreteType(TypeElement type) {
        return switch (type.getKind()) {
            case CLASS -> !type.getModifiers().contains(Modifier.ABSTRACT);
            case ENUM, RECORD -> true;
            default -> false;
        };
    }

    private void validate(TypeElement implementation) {
        Set<String> visitedTypes = new HashSet<>();
        collectContracts(
                implementation.asType(),
                implementation,
                visitedTypes);
    }

    private void collectContracts(
            TypeMirror current,
            TypeElement implementation,
            Set<String> visitedTypes) {

        for (TypeMirror parentType : types.directSupertypes(current)) {
            if (!(parentType instanceof DeclaredType declared)) {
                continue;
            }

            if (!(declared.asElement() instanceof TypeElement parent)) {
                continue;
            }

            // Include type arguments in the key so distinct declared
            // supertype uses are not accidentally conflated.
            if (!visitedTypes.add(parentType.toString())) {
                continue;
            }

            validateKeepContractTypeParameters(parent);
            if (hasContractAnnotation(parent)) {
                if (parent.getKind() == ElementKind.INTERFACE) {
                    checkContract(parent, implementation);
                } else {
                    // Also catches an invalid annotated supertype loaded
                    // from a dependency rather than from this compilation.
                    reportInvalidContractType(parent);
                }
            }

            checkHaveToImplementsContracts(parent, implementation);
            checkStaticFieldContracts(parent, implementation);
            collectContracts(parentType, implementation, visitedTypes);
        }
    }

    private void validateAnnotatedFields(TypeElement owner) {
        for (Element field : owner.getEnclosedElements()) {
            if (hasAnnotation(field, STATIC_FIELD_ANNOTATION_NAME)) {
                isValidFieldContract(field);
            }
        }
    }

    private boolean isValidFieldContract(Element field) {
        Element enclosing = field.getEnclosingElement();
        Set<Modifier> modifiers = field.getModifiers();

        if (field.getKind() == ElementKind.FIELD
                && enclosing instanceof TypeElement owner
                && owner.getKind() == ElementKind.INTERFACE
                && modifiers.contains(Modifier.PUBLIC)
                && modifiers.contains(Modifier.STATIC)) {
            return true;
        }

        String key = enclosing + "#" + field.getSimpleName();
        if (reportedInvalidFieldContracts.add(key)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@" + STATIC_FIELD_ANNOTATION_NAME
                            + " may only annotate public static fields declared in interfaces: "
                            + key,
                    field);
        }
        return false;
    }

    private void checkStaticFieldContracts(
            TypeElement contract,
            TypeElement implementation) {

        for (Element required : contract.getEnclosedElements()) {
            if (!hasAnnotation(required, STATIC_FIELD_ANNOTATION_NAME)
                    || !isValidFieldContract(required)) {
                continue;
            }

            if (!hasStaticFieldImplementation(required, contract, implementation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + implementation.getQualifiedName()
                                + " must declare: public static "
                                + displayType(required.asType(), contract, implementation)
                                + " " + required.getSimpleName()
                                + " (contract: " + contract.getQualifiedName() + ")",
                        implementation);
            }
        }
    }

    private boolean hasStaticFieldImplementation(
            Element required,
            TypeElement contract,
            TypeElement implementation) {

        for (Element actual : implementation.getEnclosedElements()) {
            if (actual.getKind() != ElementKind.FIELD
                    || !required.getSimpleName().contentEquals(actual.getSimpleName())
                    || elements.getOrigin(actual) != Origin.EXPLICIT) {
                continue;
            }

            Set<Modifier> modifiers = actual.getModifiers();
            if (modifiers.contains(Modifier.PUBLIC)
                    && modifiers.contains(Modifier.STATIC)
                    && sameType(required.asType(), actual.asType(), contract, implementation,
                            List.of(), List.of())) {
                return true;
            }
        }
        return false;
    }

    private void validateKeepContractTypeParameters(TypeElement owner) {
        for (Element element : owner.getEnclosedElements()) {
            if (!(element instanceof ExecutableElement method)) {
                continue;
            }

            for (Element parameter : method.getParameters()) {
                if (!hasAnnotation(parameter, KEEP_CONTRACT_TYPE_ANNOTATION_NAME)) {
                    continue;
                }

                Set<Modifier> modifiers = method.getModifiers();
                if (owner.getKind() == ElementKind.INTERFACE
                        && hasContractAnnotation(owner)
                        && method.getKind() == ElementKind.METHOD
                        && modifiers.contains(Modifier.PUBLIC)
                        && modifiers.contains(Modifier.STATIC)) {
                    continue;
                }

                String key = methodKey(method) + "#" + parameter.getSimpleName();
                if (reportedInvalidParameterContracts.add(key)) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@" + KEEP_CONTRACT_TYPE_ANNOTATION_NAME
                                    + " may only annotate parameters of public static methods declared in interfaces annotated with @"
                                    + ANNOTATION_NAME,
                            parameter);
                }
            }
        }
    }

    private SelfTypeMode parameterMode(Element parameter) {
        return hasAnnotation(parameter, KEEP_CONTRACT_TYPE_ANNOTATION_NAME)
                ? SelfTypeMode.PRESERVE
                : SelfTypeMode.SUBSTITUTE;
    }

    private boolean hasAnnotation(Element element, String qualifiedName) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            Element annotationElement = annotation.getAnnotationType().asElement();

            if (annotationElement instanceof TypeElement annotationType
                    && annotationType.getQualifiedName().contentEquals(qualifiedName)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasContractAnnotation(TypeElement type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            Element annotationElement = annotation.getAnnotationType().asElement();

            if (annotationElement instanceof TypeElement annotationType) {
                Name qualifiedName = annotationType.getQualifiedName();

                if (qualifiedName.contentEquals(ANNOTATION_NAME)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void reportInvalidContractType(TypeElement type) {
        String name = type.getQualifiedName().toString();

        if (!reportedInvalidContracts.add(name)) {
            return;
        }

        String kind = switch (type.getKind()) {
            case CLASS -> "class";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION_TYPE -> "annotation type";
            default -> type.getKind().name().toLowerCase().replace('_', ' ');
        };

        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@" + ANNOTATION_NAME
                        + " may only annotate interfaces; found "
                        + kind + " " + name,
                type);
    }

    /*
     * @HaveToImplements defines an instance-method contract on an interface.
     * Validate declaration-site usage even if no implementation currently
     * references the interface.
     */
    private void validateAnnotatedInstanceMethods(TypeElement owner) {
        for (Element element : owner.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            if (hasAnnotation(method, HAVE_TO_IMPLEMENTS_ANNOTATION_NAME)) {
                isValidInstanceContract(method);
            }
        }
    }

    private boolean isValidInstanceContract(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        String key = methodKey(method);

        if (!(enclosing instanceof TypeElement owner)
                || owner.getKind() != ElementKind.INTERFACE) {
            reportInvalidInstanceContract(
                    method,
                    key,
                    "the annotated method must be declared in an interface");
            return false;
        }

        Set<Modifier> modifiers = method.getModifiers();

        if (modifiers.contains(Modifier.STATIC)) {
            reportInvalidInstanceContract(
                    method,
                    key,
                    "static interface methods cannot define instance-method contracts");
            return false;
        }

        if (modifiers.contains(Modifier.PRIVATE)) {
            reportInvalidInstanceContract(
                    method,
                    key,
                    "private interface methods cannot define instance-method contracts");
            return false;
        }

        return true;
    }

    private void reportInvalidInstanceContract(
            ExecutableElement method,
            String key,
            String reason) {

        if (!reportedInvalidInstanceContracts.add(key)) {
            return;
        }

        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@" + HAVE_TO_IMPLEMENTS_ANNOTATION_NAME
                        + " may only annotate non-static, non-private instance "
                        + "methods declared in interfaces; " + reason
                        + ": " + method,
                method);
    }

    private String methodKey(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        String owner = enclosing instanceof TypeElement type
                ? type.getQualifiedName().toString()
                : enclosing.toString();

        return owner + "#" + method;
    }

    private void checkHaveToImplementsContracts(
            TypeElement parent,
            TypeElement implementation) {

        for (Element element : parent.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement required = (ExecutableElement) element;
            if (!hasAnnotation(required, HAVE_TO_IMPLEMENTS_ANNOTATION_NAME)) {
                continue;
            }

            // Validate annotations found on dependency types too, not just
            // annotations declared in source files of the current round.
            if (!isValidInstanceContract(required)) {
                continue;
            }

            if (parent.getKind() != ElementKind.INTERFACE) {
                continue;
            }

            if (!hasExplicitInstanceImplementation(required, implementation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + implementation.getQualifiedName()
                                + " must explicitly declare an instance method "
                                + "overriding: "
                                + signature(required, parent, implementation, false)
                                + " (contract: "
                                + parent.getQualifiedName() + ")",
                        implementation);
            }
        }
    }

    private boolean hasExplicitInstanceImplementation(
            ExecutableElement required,
            TypeElement implementation) {

        for (Element element : implementation.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement actual = (ExecutableElement) element;

            // Only explicitly declared methods count. Inherited, mandated,
            // and synthetic methods cannot satisfy this contract.
            if (elements.getOrigin(actual) != Origin.EXPLICIT) {
                continue;
            }

            Set<Modifier> modifiers = actual.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC)
                    || modifiers.contains(Modifier.STATIC)
                    || modifiers.contains(Modifier.PRIVATE)) {
                continue;
            }

            if (elements.overrides(actual, required, implementation)) {
                return true;
            }
        }

        return false;
    }

    private void checkContract(
            TypeElement contract,
            TypeElement implementation) {

        for (Element element : contract.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement required = (ExecutableElement) element;
            Set<Modifier> modifiers = required.getModifiers();

            // Preserve the contract rule: only directly declared
            // public static methods define requirements.
            if (!modifiers.contains(Modifier.PUBLIC)
                    || !modifiers.contains(Modifier.STATIC)) {
                continue;
            }

            if (!hasImplementation(required, contract, implementation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + implementation.getQualifiedName()
                                + " must declare: "
                                + signature(required, contract, implementation, true)
                                + " (contract: "
                                + contract.getQualifiedName() + ")",
                        implementation);
            }
        }
    }

    private boolean hasImplementation(
            ExecutableElement required,
            TypeElement contract,
            TypeElement implementation) {

        for (Element element : implementation.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement actual = (ExecutableElement) element;

            if (matches(required, actual, contract, implementation)) {
                return true;
            }
        }

        return false;
    }

    private boolean matches(
            ExecutableElement required,
            ExecutableElement actual,
            TypeElement contract,
            TypeElement implementation) {

        if (!required.getSimpleName().contentEquals(actual.getSimpleName())) {
            return false;
        }

        Set<Modifier> modifiers = actual.getModifiers();

        if (!modifiers.contains(Modifier.PUBLIC)
                || !modifiers.contains(Modifier.STATIC)) {
            return false;
        }

        // An array parameter and a varargs parameter have the same
        // underlying type but different source-level declarations.
        if (required.isVarArgs() != actual.isVarArgs()) {
            return false;
        }

        if (required.getParameters().size() != actual.getParameters().size()) {
            return false;
        }

        List<? extends TypeParameterElement> requiredGenerics = required.getTypeParameters();
        List<? extends TypeParameterElement> actualGenerics = actual.getTypeParameters();

        if (requiredGenerics.size() != actualGenerics.size()) {
            return false;
        }

        for (int i = 0; i < requiredGenerics.size(); i++) {
            List<? extends TypeMirror> expectedBounds = requiredGenerics.get(i).getBounds();
            List<? extends TypeMirror> actualBounds = actualGenerics.get(i).getBounds();

            if (expectedBounds.size() != actualBounds.size()) {
                return false;
            }

            for (int j = 0; j < expectedBounds.size(); j++) {
                if (!sameType(
                        expectedBounds.get(j),
                        actualBounds.get(j),
                        contract,
                        implementation,
                        requiredGenerics,
                        actualGenerics)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < required.getParameters().size(); i++) {
            TypeMirror expected = required.getParameters().get(i).asType();
            TypeMirror found = actual.getParameters().get(i).asType();

            if (!sameType(
                    expected,
                    found,
                    contract,
                    implementation,
                    requiredGenerics,
                    actualGenerics,
                    parameterMode(required.getParameters().get(i)))) {
                return false;
            }
        }

        return sameType(
                required.getReturnType(),
                actual.getReturnType(),
                contract,
                implementation,
                requiredGenerics,
                actualGenerics);
    }

    private boolean sameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            List<? extends TypeParameterElement> requiredParameters,
            List<? extends TypeParameterElement> actualParameters) {

        return sameType(expected, actual, contract, implementation,
                requiredParameters, actualParameters, SelfTypeMode.SUBSTITUTE);
    }

    private boolean sameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            List<? extends TypeParameterElement> requiredParameters,
            List<? extends TypeParameterElement> actualParameters,
            SelfTypeMode mode) {

        TypeKind expectedKind = expected.getKind();
        TypeKind actualKind = actual.getKind();

        /*
         * Method type variables are matched by position, not by their
         * spelling. Thus <T> T f(T) and <U> U f(U) are equivalent here.
         */
        if (expectedKind == TypeKind.TYPEVAR
                || actualKind == TypeKind.TYPEVAR) {
            int expectedIndex = methodTypeParameterIndex(expected, requiredParameters);
            int actualIndex = methodTypeParameterIndex(actual, actualParameters);

            if (expectedIndex >= 0 || actualIndex >= 0) {
                return expectedIndex >= 0 && expectedIndex == actualIndex;
            }

            return types.isSameType(expected, actual);
        }

        if (expectedKind != actualKind) {
            return false;
        }

        if (expectedKind == TypeKind.ARRAY) {
            return sameType(
                    ((ArrayType) expected).getComponentType(),
                    ((ArrayType) actual).getComponentType(),
                    contract,
                    implementation,
                    requiredParameters,
                    actualParameters,
                    mode);
        }

        if (expectedKind == TypeKind.WILDCARD) {
            WildcardType expectedWildcard = (WildcardType) expected;
            WildcardType actualWildcard = (WildcardType) actual;

            return nullableSameType(
                    expectedWildcard.getExtendsBound(),
                    actualWildcard.getExtendsBound(),
                    contract,
                    implementation,
                    requiredParameters,
                    actualParameters,
                    mode)
                    && nullableSameType(
                            expectedWildcard.getSuperBound(),
                            actualWildcard.getSuperBound(),
                            contract,
                            implementation,
                            requiredParameters,
                            actualParameters,
                            mode);
        }

        if (expectedKind == TypeKind.INTERSECTION) {
            List<? extends TypeMirror> expectedBounds = ((IntersectionType) expected).getBounds();
            List<? extends TypeMirror> actualBounds = ((IntersectionType) actual).getBounds();

            if (expectedBounds.size() != actualBounds.size()) {
                return false;
            }

            for (int i = 0; i < expectedBounds.size(); i++) {
                if (!sameType(
                        expectedBounds.get(i),
                        actualBounds.get(i),
                        contract,
                        implementation,
                        requiredParameters,
                        actualParameters,
                        mode)) {
                    return false;
                }
            }

            return true;
        }

        if (expectedKind == TypeKind.DECLARED) {
            DeclaredType expectedDeclared = (DeclaredType) expected;
            DeclaredType actualDeclared = (DeclaredType) actual;

            // 参数的保留规则覆盖整个类型树；未保留的位置继续把契约自身类型替换为实现类类型。
            if (mode == SelfTypeMode.SUBSTITUTE && isContractSelfType(expected, contract)) {
                return types.isSameType(actual, implementation.asType());
            }

            if (!expectedDeclared.asElement().equals(actualDeclared.asElement())) {
                return false;
            }

            // 静态嵌套类型没有外部实例；部分编译器仍提供外部类型，不能据此替换其声明所属类型。
            TypeMirror expectedEnclosing = expectedDeclared.getEnclosingType();
            TypeMirror actualEnclosing = actualDeclared.getEnclosingType();

            if (hasEnclosingInstance(expectedDeclared) && !sameEnclosingType(
                    expectedEnclosing,
                    actualEnclosing,
                    contract,
                    implementation,
                    requiredParameters,
                    actualParameters,
                    mode)) {
                return false;
            }

            List<? extends TypeMirror> expectedArguments = expectedDeclared.getTypeArguments();
            List<? extends TypeMirror> actualArguments = actualDeclared.getTypeArguments();

            if (expectedArguments.size() != actualArguments.size()) {
                return false;
            }

            for (int i = 0; i < expectedArguments.size(); i++) {
                if (!sameType(
                        expectedArguments.get(i),
                        actualArguments.get(i),
                        contract,
                        implementation,
                        requiredParameters,
                        actualParameters,
                        mode)) {
                    return false;
                }
            }

            return true;
        }

        return types.isSameType(expected, actual);
    }

    private boolean hasEnclosingInstance(DeclaredType type) {
        TypeElement declaration = (TypeElement) type.asElement();
        return declaration.getKind() == ElementKind.CLASS
                && !declaration.getModifiers().contains(Modifier.STATIC)
                && declaration.getEnclosingElement() instanceof TypeElement owner
                && owner.getKind() != ElementKind.INTERFACE
                && owner.getKind() != ElementKind.ANNOTATION_TYPE;
    }

    private int methodTypeParameterIndex(
            TypeMirror type,
            List<? extends TypeParameterElement> parameters) {

        if (type.getKind() != TypeKind.TYPEVAR) {
            return -1;
        }

        for (int i = 0; i < parameters.size(); i++) {
            if (types.isSameType(type, parameters.get(i).asType())) {
                return i;
            }
        }

        return -1;
    }

    private boolean sameEnclosingType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            List<? extends TypeParameterElement> requiredParameters,
            List<? extends TypeParameterElement> actualParameters,
            SelfTypeMode mode) {

        boolean expectedNone = expected.getKind() == TypeKind.NONE;
        boolean actualNone = actual.getKind() == TypeKind.NONE;

        if (expectedNone || actualNone) {
            return expectedNone && actualNone;
        }

        return sameType(
                expected,
                actual,
                contract,
                implementation,
                requiredParameters,
                actualParameters,
                mode);
    }

    private boolean nullableSameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            List<? extends TypeParameterElement> requiredParameters,
            List<? extends TypeParameterElement> actualParameters,
            SelfTypeMode mode) {

        if (expected == null || actual == null) {
            return expected == actual;
        }

        return sameType(
                expected,
                actual,
                contract,
                implementation,
                requiredParameters,
                actualParameters,
                mode);
    }

    private boolean isContractSelfType(
            TypeMirror type,
            TypeElement contract) {

        return type.getKind() == TypeKind.DECLARED
                && types.isSameType(
                        types.erasure(type),
                        types.erasure(contract.asType()));
    }

    private String signature(
            ExecutableElement method,
            TypeElement contract,
            TypeElement implementation,
            boolean staticMethod) {

        StringBuilder result = new StringBuilder("public ");
        if (staticMethod) {
            result.append("static ");
        }

        String typeParameters = formatTypeParameters(method, contract, implementation);

        if (!typeParameters.isEmpty()) {
            result.append(typeParameters).append(' ');
        }

        result.append(displayType(
                method.getReturnType(),
                contract,
                implementation));
        result.append(' ').append(method.getSimpleName()).append('(');

        StringJoiner parameters = new StringJoiner(", ");
        List<? extends Element> methodParameters = method.getParameters();

        for (int i = 0; i < methodParameters.size(); i++) {
            Element parameter = methodParameters.get(i);
            TypeMirror parameterType = parameter.asType();
            SelfTypeMode mode = staticMethod ? parameterMode(parameter) : SelfTypeMode.SUBSTITUTE;
            String typeText;

            if (method.isVarArgs()
                    && i == methodParameters.size() - 1
                    && parameterType.getKind() == TypeKind.ARRAY) {
                typeText = displayType(
                        ((ArrayType) parameterType).getComponentType(),
                        contract,
                        implementation,
                        mode) + "...";
            } else {
                typeText = displayType(
                        parameterType,
                        contract,
                        implementation,
                        mode);
            }

            parameters.add(typeText + " " + parameter.getSimpleName());
        }

        result.append(parameters).append(')');
        return result.toString();
    }

    private String formatTypeParameters(
            ExecutableElement method,
            TypeElement contract,
            TypeElement implementation) {

        List<? extends TypeParameterElement> typeParameters = method.getTypeParameters();

        if (typeParameters.isEmpty()) {
            return "";
        }

        StringJoiner formattedParameters = new StringJoiner(", ");

        for (TypeParameterElement parameter : typeParameters) {
            StringBuilder formatted = new StringBuilder(parameter.getSimpleName());

            List<? extends TypeMirror> bounds = parameter.getBounds();

            boolean onlyImplicitObjectBound = bounds.size() == 1 && isJavaLangObject(bounds.get(0));

            if (!bounds.isEmpty() && !onlyImplicitObjectBound) {
                StringJoiner formattedBounds = new StringJoiner(" & ");

                for (TypeMirror bound : bounds) {
                    formattedBounds.add(
                            displayType(bound, contract, implementation));
                }

                formatted.append(" extends ").append(formattedBounds);
            }

            formattedParameters.add(formatted);
        }

        return "<" + formattedParameters + ">";
    }

    private boolean isJavaLangObject(TypeMirror type) {
        TypeElement objectElement = elements.getTypeElement("java.lang.Object");

        return objectElement != null
                && types.isSameType(
                        types.erasure(type),
                        types.erasure(objectElement.asType()));
    }

    private String displayType(
            TypeMirror type,
            TypeElement contract,
            TypeElement implementation) {

        return displayType(type, contract, implementation, SelfTypeMode.SUBSTITUTE);
    }

    private String displayType(
            TypeMirror type,
            TypeElement contract,
            TypeElement implementation,
            SelfTypeMode mode) {

        if (type.getKind() == TypeKind.ARRAY) {
            return displayType(
                    ((ArrayType) type).getComponentType(),
                    contract,
                    implementation,
                    mode) + "[]";
        }

        if (type.getKind() == TypeKind.DECLARED) {
            if (mode == SelfTypeMode.SUBSTITUTE && isContractSelfType(type, contract)) {
                return displayType(implementation.asType(), contract, implementation, mode);
            }

            DeclaredType declared = (DeclaredType) type;
            TypeElement declaredElement = (TypeElement) declared.asElement();
            TypeMirror enclosing = declared.getEnclosingType();

            String rawName;

            if (hasEnclosingInstance(declared) && enclosing.getKind() != TypeKind.NONE) {
                rawName = displayType(enclosing, contract, implementation, mode)
                        + "." + declaredElement.getSimpleName();
            } else {
                rawName = declaredElement.getQualifiedName().toString();
            }

            List<? extends TypeMirror> arguments = declared.getTypeArguments();

            if (arguments.isEmpty()) {
                return rawName;
            }

            StringJoiner formattedArguments = new StringJoiner(", ");

            for (TypeMirror argument : arguments) {
                formattedArguments.add(
                        displayType(argument, contract, implementation, mode));
            }

            return rawName + "<" + formattedArguments + ">";
        }

        if (type.getKind() == TypeKind.WILDCARD) {
            WildcardType wildcard = (WildcardType) type;

            if (wildcard.getExtendsBound() != null) {
                return "? extends " + displayType(
                        wildcard.getExtendsBound(),
                        contract,
                        implementation,
                        mode);
            }

            if (wildcard.getSuperBound() != null) {
                return "? super " + displayType(
                        wildcard.getSuperBound(),
                        contract,
                        implementation,
                        mode);
            }

            return "?";
        }

        if (type.getKind() == TypeKind.INTERSECTION) {
            StringJoiner bounds = new StringJoiner(" & ");

            for (TypeMirror bound : ((IntersectionType) type).getBounds()) {
                bounds.add(displayType(bound, contract, implementation, mode));
            }

            return bounds.toString();
        }

        return type.toString();
    }
}
