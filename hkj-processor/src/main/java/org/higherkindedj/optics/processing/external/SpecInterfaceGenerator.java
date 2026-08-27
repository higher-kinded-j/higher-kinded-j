// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.OpticKind;
import org.higherkindedj.optics.processing.external.SpecAnalysis.OpticMethodInfo;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Generates concrete utility classes from spec interface analyses.
 *
 * <p>This generator creates a final utility class with static methods that implement the optics
 * defined in the spec interface. For each abstract method, it generates the appropriate optic using
 * the configured copy strategy, prism hint, or traversal hint.
 *
 * <p>Every method in the analysis is an abstract optic declaration: the analyser rejects {@code
 * default} methods, whose bodies cannot be read during annotation processing.
 */
public class SpecInterfaceGenerator {

  private static final ClassName GENERATED_ANNOTATION =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private final Filer filer;
  private final Messager messager;
  private final CopyStrategyCodeGenerator copyStrategyGenerator;
  private final PrismCodeGenerator prismGenerator;
  private final TraversalCodeGenerator traversalGenerator;

  /**
   * Creates a new SpecInterfaceGenerator.
   *
   * @param filer the filer for writing generated files
   * @param messager the messager for reporting diagnostics
   */
  public SpecInterfaceGenerator(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;
    this.copyStrategyGenerator = new CopyStrategyCodeGenerator();
    this.prismGenerator = new PrismCodeGenerator();
    this.traversalGenerator = new TraversalCodeGenerator();
  }

  /**
   * Generates a utility class from a spec interface analysis.
   *
   * <p>The generated class name is determined as follows:
   *
   * <ul>
   *   <li>If the interface name ends with "Spec", the suffix is removed (e.g., PersonOpticsSpec →
   *       PersonOptics)
   *   <li>Otherwise, "Impl" is appended (e.g., PersonOptics → PersonOpticsImpl)
   * </ul>
   *
   * @param analysis the spec interface analysis
   * @param targetPackage the target package for the generated class
   * @param originatingElement the annotated element that triggered generation
   */
  public void generate(SpecAnalysis analysis, String targetPackage, Element originatingElement) {
    TypeElement specInterface = analysis.specInterface();
    String className = deriveGeneratedClassName(specInterface.getSimpleName().toString());

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.\n\n"
                    + "<p>Generated from spec interface {@link $T}.",
                ClassName.get(analysis.sourceTypeElement()),
                ClassName.get(specInterface))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(originatingElement);

    // Generate optic methods for abstract methods
    for (OpticMethodInfo opticMethod : analysis.opticMethods()) {
      MethodSpec method =
          generateOpticMethod(opticMethod, analysis.sourceType(), specInterface, className);
      classBuilder.addMethod(method);
    }

    writeFile(targetPackage, classBuilder.build());
  }

  /**
   * Generates a static method for an optic definition.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type S
   * @param specInterface the spec interface, whose type parameters the method's are drawn from
   * @param className the generated class name
   * @return the generated method spec
   */
  private MethodSpec generateOpticMethod(
      OpticMethodInfo opticMethod,
      TypeMirror sourceType,
      TypeElement specInterface,
      String className) {

    String methodName = opticMethod.methodName();
    TypeMirror focusType = opticMethod.focusType();
    OpticKind opticKind = opticMethod.opticKind();

    // Build return type
    TypeName returnType = buildOpticReturnType(opticKind, sourceType, focusType);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnType)
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} property of a {@link $T}.\n\n"
                    + "@return A non-null optic instance.",
                getOpticClass(opticKind),
                methodName,
                TypeName.get(sourceType));

    // Add @SuppressWarnings("unchecked") for THROUGH_FIELD traversals
    // This is needed because container subtypes (e.g., ArrayList) are composed with
    // supertype traversals (e.g., Traversal<List, A>) using an unchecked cast
    if (opticKind == OpticKind.TRAVERSAL
        && opticMethod.traversalHint() == SpecAnalysis.TraversalHintKind.THROUGH_FIELD) {
      methodBuilder.addAnnotation(
          AnnotationSpec.builder(SuppressWarnings.class)
              .addMember("value", "$S", "unchecked")
              .build());
    }

    // The method's type parameters are the spec's, not the source type's declaration: the source
    // type may instantiate that declaration under other names, or only in part, and only the
    // variables this signature actually names can be inferred at the call.
    for (TypeParameterElement typeParam :
        methodTypeParameters(specInterface, sourceType, focusType)) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Generate method body based on optic kind
    CodeBlock body = generateOpticBody(opticMethod, sourceType, focusType, className);
    methodBuilder.addCode(body);

    return methodBuilder.build();
  }

  /**
   * The spec's type parameters that this method's signature names, in the order the spec declares
   * them.
   *
   * <p>A parameter earns its place by appearing in the source or focus type, or in the bound of a
   * parameter that does: {@code <T, V extends List<T>>} focused through {@code V} needs {@code T}
   * declared alongside it for {@code V}'s own bound to resolve.
   *
   * @param specInterface the spec interface declaring the parameters
   * @param sourceType the source type S
   * @param focusType the type the optic focuses on
   * @return the parameters to declare on the generated method (non-null, possibly empty)
   */
  private static List<TypeParameterElement> methodTypeParameters(
      TypeElement specInterface, TypeMirror sourceType, TypeMirror focusType) {

    // Copied, not the live view: this needs a List<TypeParameterElement> rather than the
    // wildcard-typed one getTypeParameters() returns, so the filtered result types cleanly.
    List<TypeParameterElement> declared = List.copyOf(specInterface.getTypeParameters());
    Set<TypeParameterElement> named = new LinkedHashSet<>();
    Deque<TypeParameterElement> pending = new ArrayDeque<>();
    for (TypeParameterElement candidate : declared) {
      if (ProcessorUtils.mentions(sourceType, candidate)
          || ProcessorUtils.mentions(focusType, candidate)) {
        named.add(candidate);
        pending.addLast(candidate);
      }
    }

    while (!pending.isEmpty()) {
      TypeParameterElement current = pending.removeFirst();
      for (TypeMirror bound : current.getBounds()) {
        for (TypeParameterElement candidate : declared) {
          if (!named.contains(candidate) && ProcessorUtils.mentions(bound, candidate)) {
            named.add(candidate);
            pending.addLast(candidate);
          }
        }
      }
    }

    return declared.stream().filter(named::contains).toList();
  }

  /**
   * Generates the method body for an optic method.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type
   * @param focusType the focus type
   * @param className the generated class name
   * @return the code block for the method body
   */
  private CodeBlock generateOpticBody(
      OpticMethodInfo opticMethod, TypeMirror sourceType, TypeMirror focusType, String className) {

    return switch (opticMethod.opticKind()) {
      case LENS -> generateLensBody(opticMethod, sourceType, focusType);
      case PRISM -> generatePrismBody(opticMethod, sourceType, focusType);
      case TRAVERSAL -> generateTraversalBody(opticMethod, sourceType, focusType, className);
      case AFFINE, ISO, GETTER, FOLD ->
          // These would need additional implementation
          CodeBlock.of(
              "throw new $T(\"$L optics are not yet supported in spec interfaces\");\n",
              UnsupportedOperationException.class,
              opticMethod.opticKind());
    };
  }

  /**
   * Generates the body for a lens method.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type
   * @param focusType the focus type
   * @return the code block
   */
  private CodeBlock generateLensBody(
      OpticMethodInfo opticMethod, TypeMirror sourceType, TypeMirror focusType) {

    String fieldName = opticMethod.methodName();
    CopyStrategyInfo copyStrategyInfo = opticMethod.copyStrategyInfo();

    CodeBlock getterLambda =
        copyStrategyGenerator.generateGetterLambda(fieldName, copyStrategyInfo, sourceType);
    CodeBlock setterLambda =
        copyStrategyGenerator.generateSetterLambda(
            opticMethod.copyStrategy(), copyStrategyInfo, fieldName, sourceType, focusType);

    return CodeBlock.builder()
        .add("return $T.of(\n", Lens.class)
        .indent()
        .add("$L,\n", getterLambda)
        .add("$L", setterLambda)
        .unindent()
        .add(");\n")
        .build();
  }

  /**
   * Generates the body for a prism method.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type
   * @param focusType the focus type
   * @return the code block
   */
  private CodeBlock generatePrismBody(
      OpticMethodInfo opticMethod, TypeMirror sourceType, TypeMirror focusType) {

    return prismGenerator.generatePrismReturnStatement(
        opticMethod.prismHint(), opticMethod.prismHintInfo(), sourceType, focusType);
  }

  /**
   * Generates the body for a traversal method.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type
   * @param focusType the focus type
   * @param className the generated class name
   * @return the code block
   */
  private CodeBlock generateTraversalBody(
      OpticMethodInfo opticMethod, TypeMirror sourceType, TypeMirror focusType, String className) {

    return traversalGenerator.generateTraversalReturnStatement(
        opticMethod.traversalHint(),
        opticMethod.traversalHintInfo(),
        sourceType,
        focusType,
        className);
  }

  /**
   * Builds the parameterised return type for an optic method.
   *
   * @param opticKind the kind of optic
   * @param sourceType the source type
   * @param focusType the focus type
   * @return the parameterised type name
   */
  private TypeName buildOpticReturnType(
      OpticKind opticKind, TypeMirror sourceType, TypeMirror focusType) {

    TypeName sourceTypeName = getParameterisedTypeName(sourceType);
    TypeName focusTypeName = TypeName.get(focusType).box();
    ClassName opticClass = getOpticClass(opticKind);

    return ParameterizedTypeName.get(opticClass, sourceTypeName, focusTypeName);
  }

  /**
   * Gets the class name for an optic kind.
   *
   * @param opticKind the optic kind
   * @return the class name
   */
  private ClassName getOpticClass(OpticKind opticKind) {
    return switch (opticKind) {
      case LENS -> ClassName.get(Lens.class);
      case PRISM -> ClassName.get(Prism.class);
      case TRAVERSAL -> ClassName.get(Traversal.class);
      case AFFINE -> ClassName.get("org.higherkindedj.optics", "Affine");
      case ISO -> ClassName.get("org.higherkindedj.optics", "Iso");
      case GETTER -> ClassName.get("org.higherkindedj.optics", "Getter");
      case FOLD -> ClassName.get("org.higherkindedj.optics", "Fold");
    };
  }

  /**
   * Gets the parameterised type name for a type mirror.
   *
   * @param typeMirror the type mirror
   * @return the type name, parameterised if the type has type arguments
   */
  // Package-private for tests.
  TypeName getParameterisedTypeName(TypeMirror typeMirror) {
    if (typeMirror instanceof DeclaredType declaredType) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        TypeName[] typeArgNames = typeArgs.stream().map(TypeName::get).toArray(TypeName[]::new);
        return ParameterizedTypeName.get(ClassName.get(typeElement), typeArgNames);
      }
    }
    return TypeName.get(typeMirror);
  }

  /**
   * Derives the generated class name from the spec interface name.
   *
   * <p>If the interface name ends with "Spec", that suffix is removed. Otherwise, "Impl" is
   * appended to avoid naming conflicts with the source interface.
   *
   * @param interfaceName the spec interface simple name
   * @return the generated class name
   */
  private String deriveGeneratedClassName(String interfaceName) {
    if (interfaceName.endsWith("Spec")) {
      return interfaceName.substring(0, interfaceName.length() - 4);
    }
    return interfaceName + "Impl";
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeFile(String packageName, TypeSpec typeSpec) {
    try {
      JavaFile.builder(packageName, typeSpec)
          .addFileComment("Generated by hkj-optics-processor. Do not edit.")
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Could not write generated file: " + e.getMessage());
    }
  }
}
