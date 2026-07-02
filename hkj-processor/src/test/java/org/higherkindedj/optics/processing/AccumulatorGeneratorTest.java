// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Structural and diagnostic tests for the accumulator-stage generator (issue #581).
 *
 * <p>The generated family compiles to a full {@code SUCCESS}: hkj-core is on the compile-testing
 * classpath at test runtime, and the emitted stages are self-consistent, so this suite pins both
 * the generated surface and the generated-to-generated cross-stage references.
 */
@DisplayName("Accumulator Generator Tests")
class AccumulatorGeneratorTest {

  private static final String PACKAGE = "org.higherkindedj.hkt.assembly.";

  private static Compilation compilation;

  @BeforeAll
  static void compileOnce() {
    compilation =
        javac()
            .withProcessors(new AccumulatorProcessor())
            .compile(
                JavaFileObjects.forSourceString(
                    "com.example.trigger.package-info",
                    """
                    @GenerateAccumulators(minArity = 1, maxArity = 12)
                    package com.example.trigger;

                    import org.higherkindedj.optics.annotations.GenerateAccumulators;
                    """));
  }

  private static String generatedSource(String simpleName) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(PACKAGE + simpleName);
    assertThat(file).as("Generated file should exist: %s", simpleName).isPresent();
    return file.get().getCharContent(true).toString();
  }

  @Test
  @DisplayName("The generated families compile to a full SUCCESS")
  void compilationSucceeds() {
    assertThat(compilation).succeeded();
  }

  @Test
  @DisplayName("All six families are generated across the arity range")
  void allFamiliesGenerated() throws IOException {
    for (String family :
        List.of(
            "ValidatedAccum",
            "ValidatedFields",
            "ValidationPathAccum",
            "ValidationPathFields",
            "EitherOrBothAccum",
            "EitherOrBothFields")) {
      for (int arity : List.of(1, 2, 6, 12)) {
        assertThat(generatedSource(family + arity)).contains("@Generated");
      }
    }
  }

  @Nested
  @DisplayName("Stage surface")
  class StageSurface {

    @Test
    @DisplayName("Arity 1 exposes apply(Function); arity 2 BiFunction; arity 3+ FunctionN")
    void applyAritiesMatchFunctionShapes() throws IOException {
      assertThat(generatedSource("ValidatedAccum1"))
          .contains("apply(Function<? super A, ? extends R> f)");
      assertThat(generatedSource("ValidatedAccum2"))
          .contains("apply(BiFunction<? super A, ? super B, ? extends R> f)");
      assertThat(generatedSource("ValidatedAccum3"))
          .contains("apply(Function3<? super A, ? super B, ? super C, ? extends R> f)");
      assertThat(generatedSource("ValidatedAccum12")).contains("Function12<");
    }

    @Test
    @DisplayName("The terminal arity has apply only: no and(), no field()")
    void terminalArityHasNoGrowth() throws IOException {
      for (String family :
          List.of(
              "ValidatedAccum12",
              "ValidatedFields12",
              "ValidationPathAccum12",
              "ValidationPathFields12",
              "EitherOrBothAccum12",
              "EitherOrBothFields12")) {
        String source = generatedSource(family);
        assertThat(source).doesNotContain(" and(").doesNotContain(" field(");
        assertThat(source).contains("apply(");
      }
    }

    @Test
    @DisplayName("Fields flavour labels via FieldError.at; Accum flavour has no field()")
    void fieldsFlavourLabelsAccumFlavourDoesNot() throws IOException {
      assertThat(generatedSource("ValidatedFields2"))
          .contains("field(String label,")
          .contains("errors.map(err -> err.at(label))");
      assertThat(generatedSource("ValidatedAccum2")).doesNotContain(" field(");
    }

    @Test
    @DisplayName("Accum flavour is generic in the error payload X; Fields flavour is not")
    void errorChannelGenerics() throws IOException {
      assertThat(generatedSource("ValidatedAccum2"))
          .contains("class ValidatedAccum2<X, A, B>")
          .contains("NonEmptyList<X>");
      assertThat(generatedSource("ValidatedFields2"))
          .contains("class ValidatedFields2<A, B>")
          .contains("NonEmptyList<FieldError>");
    }
  }

  @Nested
  @DisplayName("Carrier delegation")
  class CarrierDelegation {

    @Test
    @DisplayName("Validated stages merge via ap with the NEL semigroup, accumulator function-side")
    void validatedMergesViaAp() throws IOException {
      assertThat(generatedSource("ValidatedAccum2"))
          .contains("value.ap(")
          .contains("NonEmptyList.semigroup()");
    }

    @Test
    @DisplayName("ValidationPath stages merge via zipWithAccum")
    void validationPathMergesViaZipWithAccum() throws IOException {
      assertThat(generatedSource("ValidationPathAccum2"))
          .contains("accumulated.zipWithAccum(")
          .contains("ValidationPath<NonEmptyList<X>,");
    }

    @Test
    @DisplayName("EitherOrBoth stages wrap via Path.eitherOrBoth and unwrap at apply")
    void eitherOrBothWrapsAndUnwraps() throws IOException {
      String source = generatedSource("EitherOrBothAccum2");
      assertThat(source)
          .contains("Path.eitherOrBoth(value, NonEmptyList.semigroup())")
          .contains("accumulated.<R>map(")
          .contains(".run();");
    }

    @Test
    @DisplayName("ValidationPath fields() re-wraps the labelled value via Path.validatedNel")
    void validationPathFieldsRewraps() throws IOException {
      assertThat(generatedSource("ValidationPathFields2")).contains("Path.validatedNel(");
    }
  }

  @Nested
  @DisplayName("Diagnostics (what / why / fix)")
  class Diagnostics {

    private Compilation compileTrigger(String annotationArgs) {
      return javac()
          .withProcessors(new AccumulatorProcessor())
          .compile(
              JavaFileObjects.forSourceString(
                  "com.example.bad.package-info",
                  """
                  @GenerateAccumulators(%s)
                  package com.example.bad;

                  import org.higherkindedj.optics.annotations.GenerateAccumulators;
                  """
                      .formatted(annotationArgs)));
    }

    @Test
    @DisplayName("minArity below 1 is rejected with a what/why/fix message")
    void minArityTooLow() {
      Compilation bad = compileTrigger("minArity = 0, maxArity = 12");
      assertThat(bad).failed();
      assertThat(bad).hadErrorContaining("minArity was 0");
      assertThat(bad).hadErrorContaining("Fix: set minArity between 1 and 12");
    }

    @Test
    @DisplayName("maxArity above 12 is rejected with a what/why/fix message")
    void maxArityTooHigh() {
      Compilation bad = compileTrigger("minArity = 1, maxArity = 13");
      assertThat(bad).failed();
      assertThat(bad).hadErrorContaining("maxArity was 13");
      assertThat(bad).hadErrorContaining("nest a sub-record");
    }

    @Test
    @DisplayName("maxArity below minArity is rejected with a what/why/fix message")
    void maxBelowMin() {
      Compilation bad = compileTrigger("minArity = 5, maxArity = 3");
      assertThat(bad).failed();
      assertThat(bad).hadErrorContaining("maxArity (3) was less than minArity (5)");
      assertThat(bad).hadErrorContaining("Fix: set maxArity >= minArity");
    }
  }
}
