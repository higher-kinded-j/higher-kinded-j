// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * An optic generated over a deprecated type answers for the deprecation in the file it writes. The
 * deprecation is the author's: they chose the type by declaring the component, where their own
 * {@code @SuppressWarnings} answers for it, and that annotation cannot reach a generated file, so a
 * {@code -Werror} build would fail on a warning no one can silence.
 *
 * <p>Each row compiles one generator over a record whose components are a deprecated type and a
 * list of it, under {@code -Xlint:all}, and the warnings located in generated files must be none.
 * The same row is then compiled with the type left undeprecated, and must draw no warning either,
 * so a row whose fixture reached no deprecated type would be seen to prove nothing: its deprecated
 * form must first warn once the generated suppression is out of the picture, which the {@code
 * forRemoval} rows show for the {@code removal} category that {@code deprecation} does not quiet.
 */
@DisplayName("Optic generators - a deprecated type in a generated file")
class OpticsDeprecationTest {

  private record Row(
      String label, Supplier<Processor> processor, String annotation, String legacy) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static final String DEPRECATED = "@Deprecated";
  private static final String REMOVAL = "@Deprecated(forRemoval = true)";

  private record Generator(String label, Supplier<Processor> processor, String annotation) {}

  static Stream<Row> rows() {
    return Stream.of(
            new Generator("Lenses", LensProcessor::new, "GenerateLenses"),
            new Generator("Getters", GetterProcessor::new, "GenerateGetters"),
            new Generator("Setters", SetterProcessor::new, "GenerateSetters"),
            new Generator("Folds", FoldProcessor::new, "GenerateFolds"),
            new Generator("Traversals", TraversalProcessor::new, "GenerateTraversals"),
            new Generator("Focus", FocusProcessor::new, "GenerateFocus"))
        .flatMap(
            generator ->
                Stream.of(DEPRECATED, REMOVAL)
                    .map(
                        legacy ->
                            new Row(
                                generator.label() + (legacy.equals(REMOVAL) ? " for removal" : ""),
                                generator.processor(),
                                generator.annotation(),
                                legacy)));
  }

  private static JavaFileObject[] sources(Row row, String legacy) {
    return new JavaFileObject[] {
      JavaFileObjects.forSourceString(
          "com.example.Legacy",
          "package com.example;\n" + legacy + "\npublic record Legacy(String name) {}\n"),
      JavaFileObjects.forSourceString(
          "com.example.Basket",
          """
          package com.example;

          import java.util.List;
          import org.higherkindedj.optics.annotations.*;

          @%s
          @SuppressWarnings({"deprecation", "removal"})
          public record Basket(Legacy item, List<Legacy> items) {}
          """
              .formatted(row.annotation()))
    };
  }

  private static List<String> generatedWarnings(Compilation compilation) {
    return compilation.diagnostics().stream()
        .filter(
            diagnostic ->
                Set.of(Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING)
                    .contains(diagnostic.getKind()))
        .filter(
            diagnostic ->
                diagnostic.getSource() != null
                    && diagnostic.getSource().getName().contains("SOURCE_OUTPUT"))
        .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
        .toList();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("should compile without a warning in the generated file")
  void shouldAnswerForTheDeprecationInTheGeneratedFile(Row row) {
    Compilation compilation =
        javac()
            .withProcessors(row.processor().get())
            .withOptions("-Xlint:all")
            .compile(sources(row, row.legacy()));

    assertThat(compilation.errors()).isEmpty();
    assertThat(generatedWarnings(compilation)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  @DisplayName("should draw no suppression for a type that is not deprecated")
  void shouldLeaveAnUndeprecatedTypeUnsuppressed(Row row) {
    Compilation compilation =
        javac()
            .withProcessors(row.processor().get())
            .withOptions("-Xlint:all")
            .compile(sources(row, ""));

    assertThat(compilation.errors()).isEmpty();
    assertThat(compilation.generatedSourceFiles())
        .allSatisfy(
            file -> assertThat(content(file)).doesNotContain("\"deprecation\"", "\"removal\""));
  }

  private static String content(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
