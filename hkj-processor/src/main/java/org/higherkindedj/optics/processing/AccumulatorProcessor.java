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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.GenerateAccumulators;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;

/**
 * Annotation processor that generates the staged accumulating-assembly builder classes (issue
 * #581).
 *
 * <p>When applied to a {@code package-info.java} annotated with {@link GenerateAccumulators}, this
 * processor generates the six stage families ({@code ValidatedAccumN} / {@code ValidatedFieldsN} /
 * {@code ValidationPathAccumN} / {@code ValidationPathFieldsN} / {@code EitherOrBothAccumN} /
 * {@code EitherOrBothFieldsN}) for the requested arity range. The hand-written arity-0 entry stages
 * and the {@code FieldError} record live alongside the generated classes.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateAccumulators")
public class AccumulatorProcessor extends AbstractProcessor {

  /** The arity ceiling: the shipped {@code FunctionN} and {@code TupleN} families stop at 12. */
  static final int MAX_SUPPORTED_ARITY = 12;

  /** Creates a new AccumulatorProcessor. */
  public AccumulatorProcessor() {}

  private final Set<String> processedPackages = new HashSet<>();

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.PACKAGE) {
          error(
              "@GenerateAccumulators was applied to "
                  + element
                  + ", which is not a package. The stage families are generated per package."
                  + " Fix: move the annotation onto a package-info.java.",
              element);
          continue;
        }

        String packageName = element.toString();
        if (processedPackages.contains(packageName)) {
          continue;
        }
        processedPackages.add(packageName);

        GenerateAccumulators ann = element.getAnnotation(GenerateAccumulators.class);
        if (ann == null) {
          error("Could not read the @GenerateAccumulators annotation.", element);
          continue;
        }

        int minArity = ann.minArity();
        int maxArity = ann.maxArity();

        if (minArity < 1) {
          error(
              "@GenerateAccumulators on "
                  + packageName
                  + ": minArity was "
                  + minArity
                  + ", but the stage families start at arity 1 (the arity-0 entry stages are"
                  + " hand-written). Fix: set minArity between 1 and "
                  + MAX_SUPPORTED_ARITY
                  + ".",
              element);
          continue;
        }
        if (maxArity < minArity) {
          error(
              "@GenerateAccumulators on "
                  + packageName
                  + ": maxArity ("
                  + maxArity
                  + ") was less than minArity ("
                  + minArity
                  + "), so no arity range exists. Fix: set maxArity >= minArity.",
              element);
          continue;
        }
        if (maxArity > MAX_SUPPORTED_ARITY) {
          error(
              "@GenerateAccumulators on "
                  + packageName
                  + ": maxArity was "
                  + maxArity
                  + ", but the shipped FunctionN and TupleN families stop at "
                  + MAX_SUPPORTED_ARITY
                  + ", so stages beyond that cannot be expressed. Fix: set maxArity <= "
                  + MAX_SUPPORTED_ARITY
                  + " (nest a sub-record for wider assemblies).",
              element);
          continue;
        }

        runAccumulatorStepGenerator(minArity, maxArity, element);
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void runAccumulatorStepGenerator(int minArity, int maxArity, Element element) {
    try {
      AccumulatorStepGenerator.generate(minArity, maxArity, processingEnv, element);
    } catch (Exception e) {
      error("Could not generate accumulator stage classes: " + e.getMessage(), element);
    }
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
