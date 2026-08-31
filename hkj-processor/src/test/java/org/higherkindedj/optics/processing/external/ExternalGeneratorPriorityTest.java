// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import org.higherkindedj.optics.processing.GeneratorRegistry;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.DupGeneratorAlpha;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.DupGeneratorBeta;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.FbDefaultGenerator;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.FbFallbackGenerator;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.PriDefaultGenerator;
import org.higherkindedj.optics.processing.testspi.TestMarkerGenerators.PriOverrideGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@link ExternalLensGenerator} (the {@code @ImportOptics} route) resolves its generator
 * by priority rather than list order, through the explicit-list constructor.
 *
 * <p>The integration route cannot exercise this: {@code TypeKindAnalyser} only flags the standard
 * containers as traversable, and the shipped generators for those never collide. The
 * package-private constructor takes the generators in a chosen registration order, which is what
 * the resolution must beat.
 */
@DisplayName("ExternalLensGenerator priority resolution")
class ExternalGeneratorPriorityTest {

  /** Runs the unit-level calls inside a real processing environment for genuine TypeMirrors. */
  private static class PriorityUnitTestProcessor extends AbstractProcessor {
    private boolean invoked = false;

    private MethodSpec priTraversal;
    private MethodSpec fbTraversal;
    private MethodSpec dupTraversal;
    private TraversableGenerator nullAnchorWinner;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.RELEASE_25;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver() || invoked) {
        return false;
      }

      TypeElement bag = processingEnv.getElementUtils().getTypeElement("com.test.MarkerBag");
      if (bag == null) {
        return false;
      }
      invoked = true;

      List<? extends RecordComponentElement> components = bag.getRecordComponents();
      TypeName bagTypeName = ClassName.get(bag);

      // The override generator sits after the default it overrides.
      ExternalLensGenerator priGenerator =
          new ExternalLensGenerator(
              processingEnv.getFiler(),
              processingEnv.getMessager(),
              List.of(new PriDefaultGenerator(), new PriOverrideGenerator()));
      priTraversal =
          priGenerator.createTraversalMethod(
              FieldInfo.forRecordComponent("pri", components.get(0).asType()),
              bag,
              components,
              bagTypeName);

      // The fallback generator sits before the default that outranks it.
      ExternalLensGenerator fbGenerator =
          new ExternalLensGenerator(
              processingEnv.getFiler(),
              processingEnv.getMessager(),
              List.of(new FbFallbackGenerator(), new FbDefaultGenerator()));
      fbTraversal =
          fbGenerator.createTraversalMethod(
              FieldInfo.forRecordComponent("fb", components.get(1).asType()),
              bag,
              components,
              bagTypeName);

      // Two generators of equal priority: the first registered wins, with a warning.
      ExternalLensGenerator dupGenerator =
          new ExternalLensGenerator(
              processingEnv.getFiler(),
              processingEnv.getMessager(),
              List.of(new DupGeneratorAlpha(), new DupGeneratorBeta()));
      dupTraversal =
          dupGenerator.createTraversalMethod(
              FieldInfo.forRecordComponent("dup", components.get(2).asType()),
              bag,
              components,
              bagTypeName);

      // The same tie resolved with no component to anchor to: the choice is unchanged and no
      // warning is printed, so the whole compilation carries exactly one tie warning.
      nullAnchorWinner =
          GeneratorRegistry.of(
                  List.of(new DupGeneratorAlpha(), new DupGeneratorBeta()),
                  processingEnv.getMessager())
              .generatorFor(components.get(2).asType(), null);

      return false;
    }
  }

  @Test
  @DisplayName("should resolve by priority, not list order, and warn on an equal-priority tie")
  void shouldResolveByPriorityNotListOrder() {
    var pri =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Pri",
            """
            package com.example.hkjtest;

            public class Pri<T> {}
            """);
    var fb =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Fb",
            """
            package com.example.hkjtest;

            public class Fb<T> {}
            """);
    var dup =
        JavaFileObjects.forSourceString(
            "com.example.hkjtest.Dup",
            """
            package com.example.hkjtest;

            public class Dup<T> {}
            """);
    var bag =
        JavaFileObjects.forSourceString(
            "com.test.MarkerBag",
            """
            package com.test;

            import com.example.hkjtest.Dup;
            import com.example.hkjtest.Fb;
            import com.example.hkjtest.Pri;

            public record MarkerBag(Pri<String> pri, Fb<String> fb, Dup<String> dup) {}
            """);

    PriorityUnitTestProcessor processor = new PriorityUnitTestProcessor();
    Compilation compilation = javac().withProcessors(processor).compile(pri, fb, dup, bag);

    assertThat(compilation).succeeded();
    assertThat(processor.invoked).isTrue();

    assertThat(processor.priTraversal.toString())
        .contains("PriOverrideGenerator")
        .doesNotContain("PriDefaultGenerator");
    assertThat(processor.fbTraversal.toString())
        .contains("FbDefaultGenerator")
        .doesNotContain("FbFallbackGenerator");

    assertThat(processor.dupTraversal.toString()).contains("DupGeneratorAlpha");
    assertThat(compilation)
        .hadWarningContaining("Multiple TraversableGenerator SPI providers with equal priority");

    assertThat(processor.nullAnchorWinner).isInstanceOf(DupGeneratorAlpha.class);
    assertThat(
            compilation.warnings().stream()
                .filter(d -> d.getMessage(null).contains("Multiple TraversableGenerator"))
                .count())
        .isEqualTo(1);
  }
}
