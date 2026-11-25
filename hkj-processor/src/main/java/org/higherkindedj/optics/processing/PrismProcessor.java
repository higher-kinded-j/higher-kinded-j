// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.Reachability;
import org.higherkindedj.optics.processing.util.SubtypePrismGenerator;

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
public class PrismProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new PrismProcessor. */
  public PrismProcessor() {}

  /** The sum types waiting for a type a later round brings (see {@link WaitingOptics}). */
  private final WaitingOptics waiting = new WaitingOptics(GeneratePrisms.class);

  /**
   * {@inheritDoc}
   *
   * <p>This implementation scans for types annotated with {@link GeneratePrisms}, validates that
   * they are sealed interfaces or enums, and triggers the generation of the corresponding prisms
   * companion class. One naming a type a later round brings waits for that round.
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    WaitingOptics.Round round = waiting.round(processingEnv, roundEnv);
    for (Element element : round.elements()) {
      if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.ENUM) {
        error(
            "The @GeneratePrisms annotation can only be applied to sealed interfaces or enums.",
            element);
        continue;
      }
      if (round.ready(element)) {
        writePrismsFile((TypeElement) element);
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writePrismsFile(TypeElement element) {
    try {
      generatePrismsFile(element);
    } catch (IOException e) {
      error("Could not generate prisms file: " + e.getMessage(), element);
    }
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
    if (!Reachability.check(
        processingEnv,
        "@GeneratePrisms",
        sumTypeElement,
        Reachability.companion(packageName, defaultPackage),
        Reachability.sum(processingEnv.getTypeUtils(), sumTypeElement))) {
      return;
    }

    String prismsClassName = sumTypeName + "Prisms";

    // Define the ClassName for your custom @Generated annotation
    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    TypeSpec.Builder prismsClassBuilder =
        TypeSpec.classBuilder(prismsClassName)
            .addOriginatingElement(sumTypeElement)
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
        MethodSpec prism =
            SubtypePrismGenerator.prismMethodFor(
                processingEnv.getElementUtils(),
                processingEnv.getMessager(),
                "@GeneratePrisms",
                sumTypeElement,
                subtypeElement,
                packageName);
        if (prism != null) {
          prismsClassBuilder.addMethod(prism);
        }
      }
    }
    // Handle Enums
    else if (sumTypeElement.getKind() == ElementKind.ENUM) {
      for (VariableElement enumConstant : SubtypePrismGenerator.enumConstantsOf(sumTypeElement)) {
        prismsClassBuilder.addMethod(
            SubtypePrismGenerator.enumConstantPrismMethodFor(
                processingEnv.getElementUtils(), sumTypeElement, enumConstant));
      }
    }

    JavaFile.builder(packageName, prismsClassBuilder.build())
        .addFileComment("Generated by hkj-optics-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
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
