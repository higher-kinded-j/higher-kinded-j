// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * An annotation processor that generates a companion 'Prisms' class for any sealed interface or
 * enum annotated with {@link GeneratePrisms}.
 *
 * <p>For a sealed interface {@code Shape} with implementations {@code Circle} and {@code Square},
 * this processor will generate a final class {@code ShapePrisms} containing static factory methods
 * to create a {@link Prism} for each subtype (e.g., {@code ShapePrisms.circle()}, {@code
 * ShapePrisms.square()}).
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GeneratePrisms")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class PrismProcessor extends AbstractProcessor {

  /**
   * {@inheritDoc}
   *
   * <p>This implementation scans for types annotated with {@link GeneratePrisms}, validates that
   * they are sealed interfaces or enums, and triggers the generation of the corresponding prisms
   * companion class.
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.ENUM) {
          error(
              "The @GeneratePrisms annotation can only be applied to sealed interfaces or enums.",
              element);
          continue;
        }
        try {
          generatePrismsFile((TypeElement) element);
        } catch (IOException e) {
          error("Could not generate prisms file: " + e.getMessage(), element);
        }
      }
    }
    return true;
  }

  /**
   * Generates the complete '...Prisms' companion class file for a given sum type.
   *
   * @param sumTypeElement The {@link TypeElement} representing the sealed interface or enum.
   * @throws IOException if the generated file cannot be written.
   */
  private void generatePrismsFile(TypeElement sumTypeElement) throws IOException {
    String sumTypeName = sumTypeElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(sumTypeElement).getQualifiedName().toString();

    // Check for custom target package in annotation
    GeneratePrisms annotation = sumTypeElement.getAnnotation(GeneratePrisms.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;

    String prismsClassName = sumTypeName + "Prisms";

    // Define the ClassName for your custom @Generated annotation
    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            // Add the @Generated annotation to the class
            .addAnnotation(generatedAnnotation)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(sumTypeElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Handle Sealed Interfaces
    if (sumTypeElement.getModifiers().contains(Modifier.SEALED)) {
      for (var permittedSubclass : sumTypeElement.getPermittedSubclasses()) {
        var subtypeElement =
            (TypeElement) processingEnv.getTypeUtils().asElement(permittedSubclass);
        prismsClassBuilder.addMethod(createPrismMethodForSubtype(sumTypeElement, subtypeElement));
      }
    }
    // Handle Enums
    else if (sumTypeElement.getKind() == ElementKind.ENUM) {
      List<VariableElement> enumConstants =
          sumTypeElement.getEnclosedElements().stream()
              .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
              .map(VariableElement.class::cast)
              .collect(Collectors.toList());

      for (VariableElement enumConstant : enumConstants) {
        prismsClassBuilder.addMethod(createPrismMethodForEnum(sumTypeElement, enumConstant));
      }
    }

    JavaFile.builder(packageName, prismsClassBuilder.build())
        .addFileComment("Generated by hkj-optics-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  /**
   * Creates the {@link MethodSpec} for a static factory method that generates a {@link Prism} for a
   * specific subtype of a sealed interface.
   *
   * @param sumType The parent sealed interface element.
   * @param subtype The specific permitted subclass to create the prism for.
   * @return A complete {@code MethodSpec} for the prism factory method.
   */
  private MethodSpec createPrismMethodForSubtype(TypeElement sumType, TypeElement subtype) {
    String methodName = ProcessorUtils.toCamelCase(subtype.getSimpleName().toString());
    ClassName sumTypeName = ClassName.get(sumType);
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

  /**
   * Creates the {@link MethodSpec} for a static factory method that generates a {@link Prism} for a
   * specific enum constant.
   *
   * @param enumType The parent enum element.
   * @param enumConstant The specific enum constant to create the prism for.
   * @return A complete {@code MethodSpec} for the prism factory method.
   */
  private MethodSpec createPrismMethodForEnum(TypeElement enumType, VariableElement enumConstant) {
    String methodName = ProcessorUtils.toCamelCase(enumConstant.getSimpleName().toString());
    ClassName enumClassName = ClassName.get(enumType);

    ParameterizedTypeName prismTypeName =
        ParameterizedTypeName.get(ClassName.get(Prism.class), enumClassName, enumClassName);

    return MethodSpec.methodBuilder(methodName)
        .addJavadoc(
            "Creates a {@link $T} that focuses on the {@code $L} constant of the {@link $T}"
                + " enum.\n\n"
                + "@return A non-null {@code Prism<$T, $T>}.",
            Prism.class,
            enumConstant.getSimpleName(),
            enumClassName,
            enumClassName,
            enumClassName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(prismTypeName)
        .addStatement(
            "return $T.of(source -> source == $T.$L ? $T.of(source) : $T.empty(), value -> value)",
            Prism.class,
            enumClassName,
            enumConstant.getSimpleName(),
            Optional.class,
            Optional.class)
        .build();
  }

  /**
   * A utility method for reporting a processing error linked to a specific code element.
   *
   * @param msg The error message to report.
   * @param e The element to which the error should be attached.
   */
  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
