// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Generates prism classes for external types (sealed interfaces and enums).
 *
 * <p>This generator creates a utility class with static methods that return {@link Prism} instances
 * for each permitted subtype of a sealed interface or each constant of an enum.
 */
public class ExternalPrismGenerator {

  private static final ClassName GENERATED_ANNOTATION =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private final Filer filer;
  private final Messager messager;

  /**
   * Creates a new ExternalPrismGenerator.
   *
   * @param filer the filer for writing generated files
   * @param messager the messager for reporting diagnostics
   */
  public ExternalPrismGenerator(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;
  }

  /**
   * Generates a prisms class for an external sealed interface.
   *
   * @param analysis the type analysis for the sealed interface
   * @param targetPackage the target package for the generated class
   * @param originatingElement the annotated element that triggered generation
   */
  public void generateForSealedInterface(
      TypeAnalysis analysis, String targetPackage, Element originatingElement) {
    TypeElement sealedInterface = analysis.typeElement();
    String interfaceName = sealedInterface.getSimpleName().toString();
    String prismsClassName = interfaceName + "Prisms";

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(sealedInterface))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(originatingElement);

    // Generate prism methods for each permitted subtype
    for (TypeElement subtype : analysis.permittedSubtypes()) {
      prismsClassBuilder.addMethod(createPrismMethodForSubtype(sealedInterface, subtype));
    }

    writeFile(targetPackage, prismsClassBuilder.build());
  }

  /**
   * Generates a prisms class for an external enum.
   *
   * @param analysis the type analysis for the enum
   * @param targetPackage the target package for the generated class
   * @param originatingElement the annotated element that triggered generation
   */
  public void generateForEnum(
      TypeAnalysis analysis, String targetPackage, Element originatingElement) {
    TypeElement enumElement = analysis.typeElement();
    String enumName = enumElement.getSimpleName().toString();
    String prismsClassName = enumName + "Prisms";

    ClassName enumClassName = ClassName.get(enumElement);

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc("Generated optics for {@link $T}. Do not edit.", ClassName.get(enumElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(originatingElement);

    // Generate prism methods for each enum constant
    for (String constantName : analysis.enumConstants()) {
      prismsClassBuilder.addMethod(createPrismMethodForEnumConstant(enumClassName, constantName));
    }

    writeFile(targetPackage, prismsClassBuilder.build());
  }

  private MethodSpec createPrismMethodForSubtype(TypeElement sumType, TypeElement subtype) {
    String methodName = ProcessorUtils.toCamelCase(subtype.getSimpleName().toString());

    // The prism is written in the subtype's vocabulary: its own parameters, and the sum type as
    // its own extends/implements clause instantiates it. Reading the sum type's declaration
    // instead would name variables the method never declares.
    DeclaredType namedSumType = ProcessorUtils.sumTypeAsNamedBy(sumType, subtype);
    TypeName sourceTypeName =
        namedSumType == null ? ClassName.get(sumType) : TypeName.get(namedSumType);
    TypeName subTypeName =
        subtype.getTypeParameters().isEmpty()
            ? ClassName.get(subtype)
            : ParameterizedTypeName.get(
                ClassName.get(subtype),
                subtype.getTypeParameters().stream()
                    .map(TypeVariableName::get)
                    .toArray(TypeName[]::new));

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), sourceTypeName, subTypeName);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addJavadoc(
                "Creates a {@link $T} that focuses on the {@link $T} subtype of the {@link $T} sum"
                    + " type.\n\n"
                    + "@return A non-null {@code Prism<$T, $T>}.",
                Prism.class,
                ClassName.get(subtype),
                ClassName.get(sumType),
                sourceTypeName,
                subTypeName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(prismTypeName);

    for (TypeParameterElement typeParameter : subtype.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParameter));
    }

    // instanceof tests an erasure, so a parameterised subtype narrows through the raw name. Where
    // every one of its parameters appears in the clause, the hierarchy still pins them - a
    // Shape<T> that is a Circle can only be a Circle<T> - and javac sees it, so nothing warns.
    // A parameter the clause does not bind is the one javac cannot pin, and the only case that
    // needs answering here rather than in the consuming build.
    boolean unbound =
        namedSumType != null
            && subtype.getTypeParameters().stream()
                .anyMatch(parameter -> !ProcessorUtils.mentions(namedSumType, parameter));
    if (unbound) {
      methodBuilder.addAnnotation(
          AnnotationSpec.builder(SuppressWarnings.class)
              .addMember("value", "$S", "unchecked")
              .build());
    }

    return methodBuilder
        .addStatement(
            "return $T.of(source -> source instanceof $T ? $T.of(($T) source) : $T.empty(), value"
                + " -> value)",
            Prism.class,
            ClassName.get(subtype),
            Optional.class,
            subTypeName,
            Optional.class)
        .build();
  }

  private MethodSpec createPrismMethodForEnumConstant(
      ClassName enumClassName, String constantName) {
    String methodName = ProcessorUtils.toCamelCase(constantName);

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), enumClassName, enumClassName);

    return MethodSpec.methodBuilder(methodName)
        .addJavadoc(
            "Creates a {@link $T} that focuses on the {@code $L} constant of the {@link $T}"
                + " enum.\n\n"
                + "@return A non-null {@code Prism<$T, $T>}.",
            Prism.class,
            constantName,
            enumClassName,
            enumClassName,
            enumClassName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(prismTypeName)
        .addStatement(
            "return $T.of(source -> source == $T.$L ? $T.of(source) : $T.empty(), value -> value)",
            Prism.class,
            enumClassName,
            constantName,
            Optional.class,
            Optional.class)
        .build();
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
