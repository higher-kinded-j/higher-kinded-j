// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.GenerateForComprehensions;

/**
 * Annotation processor that generates extended-arity for-comprehension support classes.
 *
 * <p>When applied to a {@code package-info.java} annotated with {@link GenerateForComprehensions},
 * this processor generates:
 *
 * <ul>
 *   <li>{@code TupleN} records for the requested arity range
 *   <li>{@code MonadicStepsN} and {@code FilterableStepsN} classes for the {@code For}
 *       comprehension builder
 *   <li>{@code *PathStepsN} classes for each ForPath effect type
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateForComprehensions")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ForComprehensionProcessor extends AbstractProcessor {

  private final Set<String> processedPackages = new HashSet<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.PACKAGE) {
          error(
              "The @GenerateForComprehensions annotation can only be applied to packages"
                  + " (package-info.java).",
              element);
          continue;
        }

        String packageName = element.toString();
        if (processedPackages.contains(packageName)) {
          continue;
        }
        processedPackages.add(packageName);

        GenerateForComprehensions ann = element.getAnnotation(GenerateForComprehensions.class);
        if (ann == null) {
          error("Could not read @GenerateForComprehensions annotation.", element);
          continue;
        }

        int minArity = ann.minArity();
        int maxArity = ann.maxArity();

        if (minArity < 2) {
          error("minArity must be >= 2, but was " + minArity, element);
          continue;
        }
        if (maxArity < minArity) {
          error("maxArity (" + maxArity + ") must be >= minArity (" + minArity + ")", element);
          continue;
        }
        if (maxArity > 26) {
          error("maxArity must be <= 26, but was " + maxArity, element);
          continue;
        }

        try {
          TupleGenerator.generate(minArity, maxArity, processingEnv);
        } catch (Exception e) {
          error("Could not generate Tuple classes: " + e.getMessage(), element);
        }

        try {
          ForStepGenerator.generate(minArity, maxArity, processingEnv);
        } catch (Exception e) {
          error("Could not generate For step classes: " + e.getMessage(), element);
        }

        try {
          ForPathStepGenerator.generate(minArity, maxArity, processingEnv);
        } catch (Exception e) {
          error("Could not generate ForPath step classes: " + e.getMessage(), element);
        }
      }
    }
    return true;
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
