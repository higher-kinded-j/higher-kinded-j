// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * An equal-priority generator conflict is reported once for each component that reaches the tied
 * type, however many navigators walk into the component.
 *
 * <p>The test SPI registers two equal-priority generators for {@code com.example.hkjtest.Dup}. The
 * declaration pass over {@code Holder} reports the tie against each component that reaches a {@code
 * Dup}; a navigator on {@code Holder} itself, and every record that navigates into it, walks the
 * same component again and must stay silent.
 */
@DisplayName("Equal-priority generator conflicts are reported once per component")
class GeneratorConflictReportedOnceTest {

  private static final String CONFLICT =
      "Multiple TraversableGenerator SPI providers with equal priority";

  private static final JavaFileObject DUP_MARKER =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Dup",
          """
          package com.example.hkjtest;

          public class Dup<T> {}
          """);

  private static final JavaFileObject ADDRESS =
      JavaFileObjects.forSourceString(
          "com.example.Address",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Address(String street) {}
          """);

  /** A record that navigates into Holder. */
  private static final JavaFileObject OUTER =
      JavaFileObjects.forSourceString(
          "com.example.Outer",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Outer(Holder holder) {}
          """);

  /** A second record that navigates into Holder, directly and through Outer. */
  private static final JavaFileObject OUTER_AGAIN =
      JavaFileObjects.forSourceString(
          "com.example.OuterAgain",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record OuterAgain(Outer outer, Holder holder) {}
          """);

  private static JavaFileObject holder(String components, String settings) {
    return JavaFileObjects.forSourceString(
        "com.example.Holder",
        """
        package com.example;

        import com.example.hkjtest.Dup;
        import java.util.Optional;
        import org.higherkindedj.optics.annotations.GenerateFocus;

        @GenerateFocus(%s)
        public record Holder(%s) {}
        """
            .formatted(settings, components));
  }

  /**
   * The conflict warnings a compilation reported, each as the source it was reported in and the
   * tied type it names, so that two reports against one component cannot pass for one against each.
   */
  private static List<String> conflictWarnings(Compilation compilation) {
    return compilation.diagnostics().stream()
        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.WARNING)
        .filter(diagnostic -> diagnostic.getMessage(null).contains(CONFLICT))
        .map(
            diagnostic -> {
              String message = diagnostic.getMessage(null);
              String type = message.substring(message.indexOf("support type ")).split(":")[0];
              String source =
                  diagnostic.getSource() == null ? "no source" : diagnostic.getSource().getName();
              return source + " / " + type;
            })
        .toList();
  }

  @ParameterizedTest(name = "Holder({0}) [{1}], navigated into by {2} record(s)")
  @CsvSource(
      delimiter = '|',
      value = {
        "Dup<String> d                  | generateNavigators = true | 0 | 1",
        "Dup<String> d                  | generateNavigators = true | 1 | 1",
        "Dup<String> d                  | generateNavigators = true | 2 | 1",
        "Dup<Address> d                 | generateNavigators = true | 0 | 1",
        "Dup<Address> d                 | generateNavigators = true | 2 | 1",
        "Dup<Address> d                 | ''                        | 2 | 1",
        "Optional<Dup<String>> d        | generateNavigators = true | 2 | 1",
        "Dup<String> d, Dup<Address> e  | generateNavigators = true | 2 | 2"
      })
  @DisplayName("should report each component's conflict once, from the record that declares it")
  void shouldReportEachComponentsConflictOnce(
      String components, String settings, int navigatingRecords, int expected) {
    List<JavaFileObject> sources =
        new ArrayList<>(List.of(DUP_MARKER, ADDRESS, holder(components, settings)));
    sources.addAll(List.of(OUTER, OUTER_AGAIN).subList(0, navigatingRecords));

    Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sources);

    assertThat(compilation).succeeded();
    assertThat(conflictWarnings(compilation))
        .hasSize(expected)
        .doesNotHaveDuplicates()
        .allSatisfy(warning -> assertThat(warning).contains("Holder.java"));
  }
}
