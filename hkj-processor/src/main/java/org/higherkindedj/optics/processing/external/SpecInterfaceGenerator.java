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
import java.util.List;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
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

/**
 * Generates concrete utility classes from spec interface analyses.
 *
 * <p>This generator creates a final utility class with static methods that implement the optics
 * defined in the spec interface. For each abstract method, it generates the appropriate optic using
 * the configured copy strategy, prism hint, or traversal hint.
 *
 * <p>Default methods from the spec interface are copied unchanged to the generated class, converted
 * to static methods.
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
   */
  public void generate(SpecAnalysis analysis, String targetPackage) {
    TypeElement specInterface = analysis.specInterface();
    String className = deriveGeneratedClassName(specInterface.getSimpleName().toString());

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.\n\n"
                    + "<p>Generated from spec interface {@link $T}.",
                analysis.sourceTypeElement(),
                specInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate optic methods for abstract methods
    for (OpticMethodInfo opticMethod : analysis.opticMethods()) {
      MethodSpec method =
          generateOpticMethod(
              opticMethod, analysis.sourceType(), analysis.sourceTypeElement(), className);
      classBuilder.addMethod(method);
    }

    // Copy default methods (converted to static)
    for (ExecutableElement defaultMethod : analysis.defaultMethods()) {
      MethodSpec method = copyDefaultMethod(defaultMethod, analysis.sourceType());
      classBuilder.addMethod(method);
    }

    writeFile(targetPackage, classBuilder.build());
  }

  /**
   * Generates a static method for an optic definition.
   *
   * @param opticMethod the optic method info
   * @param sourceType the source type S
   * @param sourceTypeElement the source type element
   * @param className the generated class name
   * @return the generated method spec
   */
  private MethodSpec generateOpticMethod(
      OpticMethodInfo opticMethod,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
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

    // Add type parameters if source type has them
    for (TypeParameterElement typeParam : sourceTypeElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Generate method body based on optic kind
    CodeBlock body = generateOpticBody(opticMethod, sourceType, focusType, className);
    methodBuilder.addCode(body);

    return methodBuilder.build();
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
   * Copies a default method from the spec interface, converting it to a static method.
   *
   * <p>Note: The method body is copied as-is. Users must use explicit class-qualified references
   * (e.g., {@code PersonOptics.name()}) in their default method implementations.
   *
   * @param defaultMethod the default method to copy
   * @param sourceType the source type
   * @return the generated static method
   */
  private MethodSpec copyDefaultMethod(ExecutableElement defaultMethod, TypeMirror sourceType) {
    String methodName = defaultMethod.getSimpleName().toString();

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeName.get(defaultMethod.getReturnType()))
            .addJavadoc(
                "Copied from spec interface default method.\n\n" + "@return The optic instance.");

    // Copy type parameters
    for (TypeParameterElement typeParam : defaultMethod.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Copy parameters (default methods shouldn't have parameters for optics, but handle anyway)
    defaultMethod
        .getParameters()
        .forEach(
            param ->
                methodBuilder.addParameter(
                    TypeName.get(param.asType()), param.getSimpleName().toString()));

    // For default methods, we cannot easily copy the body in annotation processing
    // The user is expected to use explicit static references like PersonOptics.name()
    // We generate a placeholder that throws - in practice, the user should override
    // or the spec interface compilation will handle this
    methodBuilder.addCode(
        "// Default method body cannot be copied during annotation processing.\n"
            + "// Ensure the spec interface is compiled and this class is regenerated.\n"
            + "throw new $T(\"Default method '$L' requires manual implementation or spec recompilation\");\n",
        UnsupportedOperationException.class,
        methodName);

    return methodBuilder.build();
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
  private TypeName getParameterisedTypeName(TypeMirror typeMirror) {
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
