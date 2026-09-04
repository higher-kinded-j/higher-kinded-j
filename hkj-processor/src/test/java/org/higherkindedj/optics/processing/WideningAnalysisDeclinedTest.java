// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.WideningAnalysis.Step;
import org.higherkindedj.optics.processing.WideningAnalysis.StepKind;
import org.higherkindedj.optics.processing.WideningAnalysis.Tier;
import org.higherkindedj.optics.processing.WideningAnalysis.Widening;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The analysis names the container it turns away, and its steps stop where that container was met.
 *
 * <p>Read straight off the analysis rather than through the diagnostic, so that the contract the
 * processor reports from is pinned on its own: which layer is declined under which setting, what
 * the layers before it still widen through, and that a {@code @Nullable} component turned away at
 * its outermost layer keeps the {@code .nullable()} widening.
 */
@DisplayName("Widening analysis: declined containers")
class WideningAnalysisDeclinedTest {

  /** One component per way a container can be met, turned away, or left alone. */
  private static final JavaFileObject HOLDER =
      JavaFileObjects.forSourceString(
          "com.example.Holder",
          """
          package com.example;

          import java.util.List;
          import java.util.Map;
          import java.util.Optional;
          import java.util.Set;
          import org.higherkindedj.hkt.either.Either;
          import org.jspecify.annotations.Nullable;

          @SuppressWarnings("rawtypes")
          public record Holder(
              Either<String, ?> either,
              Map<String, ?> map,
              Set<?> set,
              Set raw,
              Optional<Either<String, ?>> nested,
              Map<String, Either<String, ?>> beneath,
              List<String> list,
              @Nullable Set<?> nullable) {}
          """);

  /** What the analysis says of each Holder component, by name, with collections left alone. */
  private static Map<String, Widening> leftAlone;

  /** The same, with collections stepped into. */
  private static Map<String, Widening> steppedInto;

  @BeforeAll
  static void analyseUnderBothSettings() {
    leftAlone = analyse(false);
    steppedInto = analyse(true);
  }

  /** Runs the analysis over Holder's components under one setting. */
  private static Map<String, Widening> analyse(boolean widenCollections) {
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
        List<TraversableGenerator> generators = new ArrayList<>();
        ServiceLoader.load(TraversableGenerator.class, getClass().getClassLoader())
            .forEach(generators::add);
        WideningAnalysis analysis = new WideningAnalysis(processingEnv, generators);
        for (RecordComponentElement component : holder.getRecordComponents()) {
          widenings.put(
              component.getSimpleName().toString(), analysis.analyse(component, widenCollections));
        }
        return false;
      }
    }

    Compilation compilation = javac().withProcessors(new Probe()).compile(HOLDER);
    assertThat(compilation).succeeded();
    return Map.copyOf(widenings);
  }

  /** The declined container as the diagnostic would name it, or null. */
  private static String declined(Widening widening) {
    return widening.declined() == null ? null : ProcessorUtils.simpleTypeName(widening.declined());
  }

  private static List<StepKind> kinds(Widening widening) {
    return widening.steps().stream().map(Step::kind).toList();
  }

  @Test
  @DisplayName("names the container it turned away")
  void namesTheContainerItTurnedAway() {
    assertThat(declined(leftAlone.get("either"))).isEqualTo("Either<String, ?>");
    assertThat(declined(leftAlone.get("set"))).isEqualTo("Set<?>");
    assertThat(declined(leftAlone.get("raw"))).isEqualTo("Set");
    assertThat(declined(leftAlone.get("nested"))).isEqualTo("Either<String, ?>");
  }

  @Test
  @DisplayName("turns nothing away that it widened or left alone")
  void turnsNothingAwayThatItWidenedOrLeftAlone() {
    assertThat(declined(leftAlone.get("list"))).isNull();
    // A ZERO_OR_MORE container is not stepped into by default, so neither it nor the container
    // beneath it is ever asked for an optic.
    assertThat(declined(leftAlone.get("map"))).isNull();
    assertThat(declined(leftAlone.get("beneath"))).isNull();
  }

  @Test
  @DisplayName("turns a ZERO_OR_MORE container away once widenCollections steps into it")
  void turnsAZeroOrMoreContainerAwayOnceWidenCollectionsStepsIntoIt() {
    assertThat(declined(steppedInto.get("map"))).isEqualTo("Map<String, ?>");
    assertThat(declined(steppedInto.get("beneath"))).isEqualTo("Either<String, ?>");
    assertThat(kinds(steppedInto.get("beneath"))).containsExactly(StepKind.SPI_ZERO_OR_MORE);
  }

  @Test
  @DisplayName("builds the same method whether a container is turned away or left alone")
  void buildsTheSameMethodWhetherAContainerIsTurnedAwayOrLeftAlone() {
    // Neither records a step for the container, so the method built from either result is the
    // same; only declined tells them apart. The processor relies on this when it steps into a
    // container on a navigator's behalf that the navigator itself had to hand back.
    Widening turnedAway = steppedInto.get("map");
    Widening untouched = leftAlone.get("map");

    assertThat(turnedAway.tier()).isEqualTo(untouched.tier());
    assertThat(turnedAway.focusType()).isEqualTo(untouched.focusType());
    assertThat(turnedAway.steps()).isEqualTo(untouched.steps());
    assertThat(untouched.declined()).isNull();
  }

  @Test
  @DisplayName("stops its steps where the container was met")
  void stopsItsStepsWhereTheContainerWasMet() {
    Widening nested = leftAlone.get("nested");
    assertThat(kinds(nested)).containsExactly(StepKind.OPTIONAL);
    assertThat(nested.tier()).isEqualTo(Tier.AFFINE);
    assertThat(nested.focusType().toString()).endsWith("Either<java.lang.String, ?>");

    Widening either = leftAlone.get("either");
    assertThat(kinds(either)).isEmpty();
    assertThat(either.tier()).isEqualTo(Tier.FOCUS);
  }

  @Test
  @DisplayName("keeps the nullable() widening of a @Nullable component it turned away")
  void keepsTheNullableWideningOfANullableComponentItTurnedAway() {
    Widening nullable = leftAlone.get("nullable");

    assertThat(declined(nullable)).isEqualTo("Set<?>");
    assertThat(kinds(nullable)).containsExactly(StepKind.NULLABLE);
    assertThat(nullable.tier()).isEqualTo(Tier.AFFINE);
  }
}
