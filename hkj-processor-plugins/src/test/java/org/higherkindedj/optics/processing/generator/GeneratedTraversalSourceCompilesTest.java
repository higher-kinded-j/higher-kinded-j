// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code @GenerateTraversals} never emits source that fails to compile, and focuses the type a
 * container's type argument stands for.
 *
 * <p>The argument is written into the generated optic, and a wildcard cannot be written there:
 * {@code new Traversal<Holder, ? extends Leaf>() {}} is illegal as an anonymous class. The tests of
 * the day missed that because every container they wrote was parameterised with a concrete type.
 *
 * <p>So every container shape is compiled through the real processor, and three things are asserted
 * of what comes back: no error is reported; a method is generated for every component that has one
 * to generate; and each method focuses the type the component's argument resolves to. The last two
 * matter because emitting no method at all compiles perfectly well, and so does a traversal that
 * has quietly widened its focus to {@code Object}.
 */
@DisplayName("Generated Traversal source always compiles")
class GeneratedTraversalSourceCompilesTest {

  /** Marks a generated file in the compile-testing filer's output. */
  private static final String GENERATED_OUTPUT = "SOURCE_OUTPUT";

  /** The type every container below focuses on. */
  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          public record Leaf(String name) {}
          """);

  /**
   * One record component: the type it is declared with, and the element type the traversal
   * generated for it focuses. A {@code null} focus means no method is generated at all, which a raw
   * container and a nested container each have their own reason for.
   */
  private record Component(String type, String focus) {}

  /** A container shape, written out for every container whose arity admits it. */
  private record Shape(String label, List<Component> components) {}

  /** Every component of a shape written with the same focus. */
  private static Shape shape(final String label, final String focus, final String... types) {
    return new Shape(label, Stream.of(types).map(type -> new Component(type, focus)).toList());
  }

  private static final List<Shape> SHAPES =
      List.of(
          shape(
              "concrete",
              "Leaf",
              "Either<String, Leaf>",
              "Try<Leaf>",
              "Validated<String, Leaf>",
              "Maybe<Leaf>",
              "Optional<Leaf>",
              "Map<String, Leaf>",
              "List<Leaf>",
              "Set<Leaf>",
              "Leaf[]"),
          shape(
              "wildcard in the focused type argument",
              "Leaf",
              "Either<String, ? extends Leaf>",
              "Try<? extends Leaf>",
              "Validated<String, ? extends Leaf>",
              "Maybe<? extends Leaf>",
              "Optional<? extends Leaf>",
              "Map<String, ? extends Leaf>",
              "List<? extends Leaf>",
              "Set<? extends Leaf>"),
          shape(
              "wildcard in a type argument the generator does not focus on",
              "Leaf",
              "Either<?, Leaf>",
              "Validated<?, Leaf>",
              "Map<?, Leaf>"),
          shape(
              "unbounded wildcard",
              "Object",
              "Either<String, ?>",
              "Try<?>",
              "Validated<String, ?>",
              "Maybe<?>",
              "Optional<?>",
              "Map<String, ?>",
              "List<?>",
              "Set<?>"),
          shape(
              "super-bounded wildcard",
              "Object",
              "Either<String, ? super Leaf>",
              "Map<String, ? super Leaf>",
              "List<? super Leaf>",
              "Optional<? super Leaf>"),
          // A raw container has no type argument to focus, so the processor generates nothing.
          shape(
              "raw container",
              null,
              "Either",
              "Try",
              "Validated",
              "Maybe",
              "Optional",
              "Map",
              "List",
              "Set"),
          // The container itself is ground here; each of these focuses a different nested type, so
          // the shape asserts compilation alone.
          new Shape(
              "wildcard nested inside a type argument",
              List.of(
                  new Component("Either<String, List<? extends Leaf>>", "List<? extends Leaf>"),
                  new Component(
                      "Optional<Map<String, ? extends Leaf>>", "Map<String, ? extends Leaf>"),
                  new Component("Map<String, List<? extends Leaf>>", "List<? extends Leaf>"),
                  new Component("List<Optional<? extends Leaf>>", "Optional<? extends Leaf>"))),
          // Every other container reaches the processor through the SPI, and reads its type
          // argument through the same generator base.
          shape(
              "third-party container",
              "Leaf",
              "PVector<Leaf>",
              "PMap<String, Leaf>",
              "PSortedMap<String, Leaf>",
              "ImmutableList<Leaf>",
              "io.vavr.collection.List<Leaf>",
              "HashBag<Leaf>"),
          shape(
              "wildcard in a third-party container",
              "Leaf",
              "PVector<? extends Leaf>",
              "PMap<String, ? extends Leaf>",
              "PSortedMap<String, ? extends Leaf>",
              "ImmutableList<? extends Leaf>",
              "io.vavr.collection.List<? extends Leaf>",
              "HashBag<? extends Leaf>"));

  static Stream<Arguments> shapes() {
    return SHAPES.stream().map(shape -> Arguments.of(shape.label(), shape));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("shapes")
  @DisplayName("should compile, and focus the type each component's argument resolves to")
  void shouldNeverEmitUncompilableSource(final String label, final Shape shape) {
    final Compilation compilation =
        javac().withProcessors(new TraversalProcessor()).compile(holder(shape), LEAF);

    assertThat(errorsFrom(compilation, GENERATED_OUTPUT))
        .as(
            "%s: the processor emitted generated source that does not compile. Resolve the type"
                + " argument where it is read, so that what is written is denotable",
            label)
        .isEmpty();
    assertThat(compilation.errors().stream().map(GeneratedTraversalSourceCompilesTest::describe))
        .as("%s: the compilation reported errors", label)
        .isEmpty();

    final String generated = generatedSource(compilation);
    assertThat(expectedMethods(shape))
        .as("%s: a component focuses a type the generated method does not declare", label)
        .allSatisfy(method -> assertThat(generated).contains(method));
  }

  /** The signature each component of the shape must generate, in the order they are declared. */
  private static List<String> expectedMethods(final Shape shape) {
    return IntStream.range(0, shape.components().size())
        .filter(index -> shape.components().get(index).focus() != null)
        .mapToObj(
            index ->
                "Traversal<Holder, %s> f%d()"
                    .formatted(shape.components().get(index).focus(), index))
        .toList();
  }

  /** A record carrying one component per type in the shape. */
  private static JavaFileObject holder(final Shape shape) {
    final String components =
        IntStream.range(0, shape.components().size())
            .mapToObj(index -> shape.components().get(index).type() + " f" + index)
            .collect(Collectors.joining(", "));
    return JavaFileObjects.forSourceString(
        "com.example.Holder",
        """
        package com.example;

        import com.google.common.collect.ImmutableList;
        import org.apache.commons.collections4.bag.HashBag;
        import org.eclipse.collections.api.list.MutableList;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.hkt.maybe.Maybe;
        import org.higherkindedj.hkt.trymonad.Try;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateTraversals;
        import org.pcollections.PMap;
        import org.pcollections.PSortedMap;
        import org.pcollections.PVector;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;

        @GenerateTraversals
        @SuppressWarnings({"rawtypes", "unchecked"})
        public record Holder(%s) {}
        """
            .formatted(components));
  }

  /** The one file the processor generated for the holder. */
  private static String generatedSource(final Compilation compilation) {
    try {
      return compilation
          .generatedSourceFile("com.example.HolderTraversals")
          .orElseThrow(() -> new AssertionError("no traversals were generated for the holder"))
          .getCharContent(true)
          .toString();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read the generated traversals", e);
    }
  }

  /** The errors javac located in a file whose name contains {@code marker}. */
  private static List<String> errorsFrom(final Compilation compilation, final String marker) {
    return compilation.errors().stream()
        .filter(error -> error.getSource() != null)
        .filter(error -> error.getSource().getName().contains(marker))
        .map(GeneratedTraversalSourceCompilesTest::describe)
        .toList();
  }

  private static String describe(final Diagnostic<? extends JavaFileObject> diagnostic) {
    final String source =
        diagnostic.getSource() == null ? "no source" : diagnostic.getSource().getName();
    return source + ":" + diagnostic.getLineNumber() + " " + diagnostic.getMessage(null);
  }
}
