// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code @GenerateFocus} never emits Focus source that fails to compile.
 *
 * <p>This is a guard on the shape of a failure rather than on any one cause. Issue #718 was a
 * widening call javac could not apply to the path, and it surfaced as an error inside the generated
 * file instead of at the declaration that caused it; the tests of the day missed it because every
 * SPI container they wrote was parameterised with a concrete type.
 *
 * <p>So every container shape is compiled under every widening setting, and two things are asserted
 * of whatever comes back: no error is located in generated output, and every error carries the
 * {@code @GenerateFocus} tag, which only a diagnostic reported against a declaration does. A new
 * container shape, a new setting, or a widening site that skips the guard fails here without anyone
 * having to predict the mechanism first.
 *
 * <p>Rejecting everything would satisfy that on its own, so the corpus below pins the other half:
 * every record in it compiles today, and a guard that grows too eager fails on it.
 */
@DisplayName("Generated Focus source always compiles")
class GeneratedFocusSourceCompilesTest {

  /** Marks a generated file in the compile-testing filer's output. */
  private static final String GENERATED_OUTPUT = "SOURCE_OUTPUT";

  /** The type every container below focuses on, navigable so that navigators reach into it. */
  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus
          public record Leaf(String name) {}
          """);

  /** A container shape, written out for every container whose arity admits it. */
  private record Shape(String label, List<String> types) {}

  private static final List<Shape> SHAPES =
      List.of(
          new Shape(
              "concrete",
              List.of(
                  "Either<String, Leaf>",
                  "Try<Leaf>",
                  "Validated<String, Leaf>",
                  "Maybe<Leaf>",
                  "Optional<Leaf>",
                  "Map<String, Leaf>",
                  "List<Leaf>",
                  "Set<Leaf>",
                  "Leaf[]")),
          new Shape(
              "wildcard in the focused type argument",
              List.of(
                  "Either<String, ? extends Leaf>",
                  "Try<? extends Leaf>",
                  "Validated<String, ? extends Leaf>",
                  "Maybe<? extends Leaf>",
                  "Optional<? extends Leaf>",
                  "Map<String, ? extends Leaf>",
                  "List<? extends Leaf>",
                  "Set<? extends Leaf>")),
          new Shape(
              "wildcard in a type argument the generator does not focus on",
              List.of("Either<?, Leaf>", "Validated<?, Leaf>", "Map<?, Leaf>")),
          new Shape(
              "unbounded wildcard",
              List.of(
                  "Either<String, ?>",
                  "Try<?>",
                  "Validated<String, ?>",
                  "Maybe<?>",
                  "Optional<?>",
                  "Map<String, ?>",
                  "List<?>",
                  "Set<?>")),
          new Shape(
              "super-bounded wildcard",
              List.of(
                  "Either<String, ? super Leaf>",
                  "Map<String, ? super Leaf>",
                  "List<? super Leaf>",
                  "Optional<? super Leaf>")),
          new Shape(
              "raw container",
              List.of("Either", "Try", "Validated", "Maybe", "Optional", "Map", "List", "Set")),
          new Shape(
              "wildcard nested inside a type argument",
              List.of(
                  "Either<String, List<? extends Leaf>>",
                  "Optional<Map<String, ? extends Leaf>>",
                  "Map<String, List<? extends Leaf>>",
                  "List<Optional<? extends Leaf>>")));

  /** The widening settings a container can be read under. */
  private static final List<String> SETTINGS =
      List.of(
          "",
          "widenCollections = true",
          "generateNavigators = true",
          "generateNavigators = true, widenCollections = true");

  static Stream<Arguments> shapesUnderEverySetting() {
    return SHAPES.stream()
        .flatMap(
            shape ->
                SETTINGS.stream()
                    .map(
                        setting ->
                            Arguments.of(
                                shape.label() + " / " + (setting.isEmpty() ? "defaults" : setting),
                                shape,
                                setting)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shapesUnderEverySetting")
  @DisplayName("should report at the declaration and never from generated output")
  void shouldNeverEmitUncompilableSource(String label, Shape shape, String setting) {
    Compilation compilation =
        javac().withProcessors(new FocusProcessor()).compile(holder(shape, setting), LEAF);

    assertThat(errorsFrom(compilation, GENERATED_OUTPUT))
        .as(
            "%s: the processor emitted generated source that does not compile. Reject the"
                + " declaration instead, so the error names the component that caused it",
            label)
        .isEmpty();

    assertThat(errorsNotTagged(compilation))
        .as("%s: an error was reported that no @GenerateFocus diagnostic explains", label)
        .isEmpty();
  }

  /**
   * A component and the settings it is read under, which together must keep compiling.
   *
   * <p>Every entry compiles before the undenotable-container rule exists, so each one is a record
   * the rule must leave alone. A container the analysis never widens is the subtle half: it stays a
   * plain {@code FocusPath}, nothing beneath it is ever asked for an optic, and an undenotable type
   * argument down there costs it nothing.
   */
  private record StillCompiles(String component, String setting) {}

  private static final List<StillCompiles> CORPUS =
      List.of(
          // The built-in widenings take a wildcard: .some() and .each() unify nothing.
          new StillCompiles("List<? extends Leaf> f", ""),
          new StillCompiles("Set<? extends Leaf> f", ""),
          new StillCompiles("Optional<? extends Leaf> f", ""),
          new StillCompiles("Optional<? super Leaf> f", ""),
          // A ZERO_OR_MORE SPI container is not widened by the static method on its own. With
          // navigators it is, which is why that setting is absent here and rejected instead.
          new StillCompiles("Map<String, ? extends Leaf> f", ""),
          // Nothing beneath an un-widened container is asked for an optic either.
          new StillCompiles("Map<String, Either<String, ? extends Leaf>> f", ""),
          new StillCompiles(
              "Map<String, Either<String, ? extends Leaf>> f", "generateNavigators = true"),
          new StillCompiles("Optional<Map<String, ? extends Leaf>> f", "generateNavigators = true"),
          // A wildcard inside a type argument still leaves the container itself ground.
          new StillCompiles("Either<String, List<? extends Leaf>> f", ""),
          new StillCompiles("Optional<Map<String, ? extends Leaf>> f", ""),
          // Deeper than the analysis descends, so the Either is never widened.
          new StillCompiles("Optional<Optional<Optional<Either<String, ? extends Leaf>>>> f", ""),
          // Concrete containers widen exactly as they always did.
          new StillCompiles("Either<String, Leaf> f", ""),
          new StillCompiles("Map<String, Leaf> f", "widenCollections = true"),
          new StillCompiles("Map<String, Leaf> f", "generateNavigators = true"));

  static Stream<Arguments> corpus() {
    return CORPUS.stream()
        .map(
            entry ->
                Arguments.of(
                    entry.component()
                        + " / "
                        + (entry.setting().isEmpty() ? "defaults" : entry.setting()),
                    entry));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corpus")
  @DisplayName("should leave a record the analysis never widens alone")
  void shouldKeepCompilingWhatAlreadyCompiled(String label, StillCompiles entry) {
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(holder(new Shape(label, List.of()), entry.setting(), entry.component()), LEAF);

    assertThat(
            compilation.errors().stream().map(GeneratedFocusSourceCompilesTest::describe).toList())
        .as(
            "%s compiles without the undenotable-container rule, so the rule must leave it alone",
            label)
        .isEmpty();
  }

  /** A record carrying one component per type in the shape, read under the given settings. */
  private static JavaFileObject holder(Shape shape, String setting) {
    return holder(
        shape,
        setting,
        IntStream.range(0, shape.types().size())
            .mapToObj(index -> shape.types().get(index) + " f" + index)
            .reduce((left, right) -> left + ", " + right)
            .orElseThrow());
  }

  /** A record carrying the given components, read under the given settings. */
  private static JavaFileObject holder(Shape shape, String setting, String components) {
    return JavaFileObjects.forSourceString(
        "com.example.Holder",
        """
        package com.example;

        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.hkt.maybe.Maybe;
        import org.higherkindedj.hkt.trymonad.Try;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;

        @GenerateFocus(%s)
        @SuppressWarnings({"rawtypes", "unchecked"})
        public record Holder(%s) {}
        """
            .formatted(setting, components));
  }

  /** The errors javac located in a file whose name contains {@code marker}. */
  private static List<String> errorsFrom(Compilation compilation, String marker) {
    return compilation.errors().stream()
        .filter(error -> error.getSource() != null)
        .filter(error -> error.getSource().getName().contains(marker))
        .map(GeneratedFocusSourceCompilesTest::describe)
        .toList();
  }

  /** The errors that carry no {@code @GenerateFocus} tag, so no declaration diagnostic explains. */
  private static List<String> errorsNotTagged(Compilation compilation) {
    return compilation.errors().stream()
        .filter(error -> !String.valueOf(error.getMessage(null)).contains("@GenerateFocus"))
        .map(GeneratedFocusSourceCompilesTest::describe)
        .toList();
  }

  private static String describe(Diagnostic<? extends JavaFileObject> error) {
    String source = error.getSource() == null ? "<no source>" : error.getSource().getName();
    return source
        + ":"
        + error.getLineNumber()
        + " "
        + String.valueOf(error.getMessage(null)).lines().findFirst().orElse("");
  }
}
