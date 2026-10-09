// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.WideningAnalysis.Step;
import org.higherkindedj.optics.processing.WideningAnalysis.StepKind;
import org.higherkindedj.optics.processing.WideningAnalysis.Widening;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The containers the analysis recognises by name widen the same way whatever generators are on the
 * annotation processor path.
 *
 * <p>The analysis writes their expressions itself, so a build without the generator plugins widens
 * them too, and a generator that claims one of them at {@link
 * TraversableGenerator#PRIORITY_OVERRIDE} changes what {@code @GenerateTraversals} generates but
 * not the Focus path.
 */
@DisplayName("Widening analysis: containers recognised by name")
class WideningAnalysisRecognisedTest {

  /** One component per container the analysis recognises by name. */
  private static final JavaFileObject HOLDER =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.util.Collection;
          import java.util.List;
          import java.util.Optional;
          import java.util.Set;
          import org.higherkindedj.hkt.maybe.Maybe;

          public record Holder(
              Optional<String> optional,
              Maybe<String> maybe,
              List<String> list,
              Set<String> set,
              Collection<String> collection) {}
          """);

  /** The step each Holder component widens through, by component name. */
  private static final Map<String, StepKind> EXPECTED =
      Map.of(
          "optional", StepKind.OPTIONAL,
          "maybe", StepKind.MAYBE,
          "list", StepKind.LIST,
          "set", StepKind.SET,
          "collection", StepKind.COLLECTION);

  /**
   * A generator that outranks the built-in ones for all five types and names an optic of its own,
   * so any widening that consulted it would say so.
   */
  private static final TraversableGenerator OVERRIDE =
      new TraversableGenerator() {
        @Override
        public int priority() {
          return PRIORITY_OVERRIDE;
        }

        @Override
        public boolean supports(TypeMirror type) {
          return type instanceof DeclaredType declared
              && Set.of(
                      "java.util.Optional",
                      "org.higherkindedj.hkt.maybe.Maybe",
                      "java.util.List",
                      "java.util.Set",
                      "java.util.Collection")
                  .contains(((TypeElement) declared.asElement()).getQualifiedName().toString());
        }

        @Override
        public String generateOpticExpression() {
          return "Override.each()";
        }

        @Override
        public CodeBlock generateModifyF(
            RecordComponentElement component,
            ClassName recordClassName,
            List<? extends RecordComponentElement> allComponents) {
          throw new UnsupportedOperationException("never asked to generate a traversal here");
        }
      };

  /** What the analysis says of each component with no generator on the path. */
  private static Map<String, Widening> pluginFree;

  /** The same, with the override generator on the path. */
  private static Map<String, Widening> overridden;

  @BeforeAll
  static void analyseWithAndWithoutGenerators() {
    pluginFree = analyse(List.of());
    overridden = analyse(List.of(OVERRIDE));
  }

  /** Runs the analysis over Holder's components, with collections stepped into. */
  private static Map<String, Widening> analyse(List<TraversableGenerator> generators) {
    Map<String, Widening> widenings = new LinkedHashMap<>();

    final class Probe extends AbstractProcessor {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
          return false;
        }
        TypeElement holder = processingEnv.getElementUtils().getTypeElement("com.example.Holder");
        WideningAnalysis analysis = new WideningAnalysis(processingEnv, generators, "com.example");
        for (RecordComponentElement component : holder.getRecordComponents()) {
          widenings.put(component.getSimpleName().toString(), analysis.analyse(component, true));
        }
        return false;
      }
    }

    Compilation compilation = javac().withProcessors(new Probe()).compile(HOLDER);
    assertThat(compilation).succeeded();
    return Map.copyOf(widenings);
  }

  private static List<StepKind> kinds(Widening widening) {
    return widening.steps().stream().map(Step::kind).toList();
  }

  @Test
  @DisplayName("widens every recognised container with no generator on the path")
  void widensEveryRecognisedContainerWithNoGeneratorOnThePath() {
    EXPECTED.forEach(
        (component, kind) -> assertThat(kinds(pluginFree.get(component))).containsExactly(kind));
  }

  @Test
  @DisplayName("asks no generator, even one that outranks the built-in ones")
  void asksNoGeneratorEvenOneThatOutranksTheBuiltInOnes() {
    EXPECTED.forEach(
        (component, kind) -> {
          Widening widening = overridden.get(component);
          assertThat(kinds(widening)).containsExactly(kind);
          assertThat(widening.steps()).allSatisfy(step -> assertThat(step.generator()).isNull());
          assertThat(widening.tier()).isEqualTo(pluginFree.get(component).tier());
          assertThat(widening.focusType()).isEqualTo(pluginFree.get(component).focusType());
        });
  }
}
