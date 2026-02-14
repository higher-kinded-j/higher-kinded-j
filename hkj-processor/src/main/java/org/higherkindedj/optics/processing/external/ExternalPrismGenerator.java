// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Prism;
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
   */
  public void generateForSealedInterface(TypeAnalysis analysis, String targetPackage) {
    TypeElement sealedInterface = analysis.typeElement();
    String interfaceName = sealedInterface.getSimpleName().toString();
    String prismsClassName = interfaceName + "Prisms";

    ClassName sumTypeName = ClassName.get(sealedInterface);

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(sealedInterface))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate prism methods for each permitted subtype
    for (TypeElement subtype : analysis.permittedSubtypes()) {
      prismsClassBuilder.addMethod(createPrismMethodForSubtype(sumTypeName, subtype));
    }

    writeFile(targetPackage, prismsClassBuilder.build());
  }

  /**
   * Generates a prisms class for an external enum.
   *
   * @param analysis the type analysis for the enum
   * @param targetPackage the target package for the generated class
   */
  public void generateForEnum(TypeAnalysis analysis, String targetPackage) {
    TypeElement enumElement = analysis.typeElement();
    String enumName = enumElement.getSimpleName().toString();
    String prismsClassName = enumName + "Prisms";

    ClassName enumClassName = ClassName.get(enumElement);

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc("Generated optics for {@link $T}. Do not edit.", ClassName.get(enumElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate prism methods for each enum constant
    for (String constantName : analysis.enumConstants()) {
      prismsClassBuilder.addMethod(createPrismMethodForEnumConstant(enumClassName, constantName));
    }

    writeFile(targetPackage, prismsClassBuilder.build());
  }

  private MethodSpec createPrismMethodForSubtype(ClassName sumTypeName, TypeElement subtype) {
    String methodName = ProcessorUtils.toCamelCase(subtype.getSimpleName().toString());
    ClassName subTypeName = ClassName.get(subtype);

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), sumTypeName, subTypeName);

    return MethodSpec.methodBuilder(methodName)
        .addJavadoc(
            "Creates a {@link $T} that focuses on the {@link $T} subtype of the {@link $T} sum"
                + " type.\n\n"
                + "@return A non-null {@code Prism<$T, $T>}.",
            Prism.class,
            subTypeName,
            sumTypeName,
            sumTypeName,
            subTypeName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(prismTypeName)
        .addStatement(
            "return $T.of(source -> source instanceof $T ? $T.of(($T) source) : $T.empty(), value"
                + " -> value)",
            Prism.class,
            subTypeName,
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
