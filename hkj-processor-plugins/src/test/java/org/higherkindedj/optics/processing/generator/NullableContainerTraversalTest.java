// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.FocusProcessor;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/**
 * A traversal over a {@code @Nullable} container finds no elements in a null one, whichever
 * generator writes its body, as the Focus path does.
 *
 * <p>Each container family is compiled as a record of one nullable component under
 * {@code @GenerateTraversals} and {@code @GenerateFocus(widenCollections = true)}, beside a probe
 * that names the generated optics as user code would, and the probe is run. Over a null component
 * the traversal must read no element, hand the record back as it is from {@code modify}, and agree
 * with the Focus path. Over a component of one element it must rewrite that element as before, and
 * leave the source as it was.
 */
@DisplayName("A generated traversal over a null @Nullable container")
class NullableContainerTraversalTest {

  /** A record of one nullable component {@code f}, and the expressions the probe builds it from. */
  private record Row(String name, String component, List<String> imports, String one) {
    JavaFileObject source() {
      StringBuilder source =
          new StringBuilder(
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.optics.annotations.GenerateTraversals;
              import org.jspecify.annotations.Nullable;
              """);
      imports.forEach(type -> source.append("import ").append(type).append(";\n"));
      source
          .append("\n@GenerateTraversals\n@GenerateFocus(widenCollections = true)\n")
          .append("public record %s(%s f) {}\n".formatted(name, component));
      return JavaFileObjects.forSourceString("com.example." + name, source.toString());
    }

    /** The probe's case for this row. */
    String probeCase() {
      return "      case \"%1$s\" -> report(%1$sTraversals.f(), %1$sFocus.f(), new %1$s(null), new %1$s(%2$s));\n"
          .formatted(name, one);
    }

    @Override
    public String toString() {
      return name + " (" + component + ")";
    }
  }

  private static Row row(String name, String component, String one, String... imports) {
    return new Row(name, component, List.of(imports), one);
  }

  /** One row for each generator family, and for each base class a family's generators share. */
  private static final List<Row> ROWS =
      List.of(
          row("JdkList", "@Nullable List<String>", "List.of(\"a\")", "java.util.List"),
          row("JdkSet", "@Nullable Set<String>", "Set.of(\"a\")", "java.util.Set"),
          row(
              "JdkCollection",
              "@Nullable Collection<String>",
              "List.of(\"a\")",
              "java.util.Collection"),
          row("JdkMap", "@Nullable Map<String, String>", "Map.of(\"k\", \"a\")", "java.util.Map"),
          row("JdkArray", "String @Nullable []", "new String[] {\"a\"}"),
          row(
              "JdkOptional",
              "@Nullable Optional<String>",
              "Optional.of(\"a\")",
              "java.util.Optional"),
          row(
              "HkjEither",
              "@Nullable Either<String, String>",
              "Either.right(\"a\")",
              "org.higherkindedj.hkt.either.Either"),
          row(
              "HkjMaybe",
              "@Nullable Maybe<String>",
              "Maybe.just(\"a\")",
              "org.higherkindedj.hkt.maybe.Maybe"),
          row(
              "HkjTry",
              "@Nullable Try<String>",
              "Try.success(\"a\")",
              "org.higherkindedj.hkt.trymonad.Try"),
          row(
              "HkjValidated",
              "@Nullable Validated<String, String>",
              "Validated.valid(\"a\")",
              "org.higherkindedj.hkt.validated.Validated"),
          row(
              "ApacheMultiSet",
              "@Nullable HashMultiSet<String>",
              "new HashMultiSet<>(List.of(\"a\"))",
              "org.apache.commons.collections4.multiset.HashMultiSet"),
          row(
              "ApacheTreeMultiSet",
              "@Nullable TreeMultiSet<String>",
              "new TreeMultiSet<>(List.of(\"a\"))",
              "org.apache.commons.collections4.multiset.TreeMultiSet"),
          row(
              "ApacheBag",
              "@Nullable HashBag<String>",
              "new HashBag<>(List.of(\"a\"))",
              "org.apache.commons.collections4.bag.HashBag"),
          row(
              "EclipseList",
              "@Nullable MutableList<String>",
              "Lists.mutable.of(\"a\")",
              "org.eclipse.collections.api.list.MutableList"),
          row(
              "EclipseSortedSet",
              "@Nullable MutableSortedSet<String>",
              "SortedSets.mutable.of(\"a\")",
              "org.eclipse.collections.api.set.sorted.MutableSortedSet"),
          row(
              "GuavaList",
              "@Nullable ImmutableList<String>",
              "ImmutableList.of(\"a\")",
              "com.google.common.collect.ImmutableList"),
          row(
              "PcVector",
              "@Nullable PVector<String>",
              "TreePVector.singleton(\"a\")",
              "org.pcollections.PVector"),
          row(
              "PcMap",
              "@Nullable PMap<String, String>",
              "HashTreePMap.singleton(\"k\", \"a\")",
              "org.pcollections.PMap"),
          row(
              "VavrList",
              "@Nullable List<String>",
              "io.vavr.collection.List.of(\"a\")",
              "io.vavr.collection.List"));

  /** Names every generated optic as user code would, and reports what each hands back. */
  private static JavaFileObject probe() {
    StringBuilder cases = new StringBuilder();
    ROWS.forEach(row -> cases.append(row.probeCase()));
    return JavaFileObjects.forSourceString(
        "com.example.Probe",
        """
        package com.example;

        import com.google.common.collect.ImmutableList;
        import java.util.*;
        import java.util.function.Function;
        import org.apache.commons.collections4.bag.HashBag;
        import org.apache.commons.collections4.multiset.HashMultiSet;
        import org.apache.commons.collections4.multiset.TreeMultiSet;
        import org.eclipse.collections.api.factory.Lists;
        import org.eclipse.collections.api.factory.SortedSets;
        import org.higherkindedj.hkt.either.Either;
        import org.higherkindedj.hkt.maybe.Maybe;
        import org.higherkindedj.hkt.trymonad.Try;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.Traversal;
        import org.higherkindedj.optics.focus.AffinePath;
        import org.higherkindedj.optics.focus.TraversalPath;
        import org.higherkindedj.optics.util.Traversals;
        import org.pcollections.HashTreePMap;
        import org.pcollections.TreePVector;

        public final class Probe {
          private static final Function<String, String> UP = s -> s.toUpperCase(Locale.ROOT);

          private Probe() {}

          private static <S> S focusModify(Object path, S source) {
            return switch (path) {
              case AffinePath<?, ?> affine -> modify(affine, source);
              case TraversalPath<?, ?> traversal -> modifyAll(traversal, source);
              default -> throw new AssertionError("widened to neither");
            };
          }

          @SuppressWarnings("unchecked") // the generated path's type arguments erase to these
          private static <S> S modify(AffinePath<?, ?> path, S source) {
            return ((AffinePath<S, String>) path).modify(UP, source);
          }

          @SuppressWarnings("unchecked") // the generated path's type arguments erase to these
          private static <S> S modifyAll(TraversalPath<?, ?> path, S source) {
            return ((TraversalPath<S, String>) path).modifyAll(UP, source);
          }

          /**
           * What the traversal reads from a null component, whether modify hands the record back
           * as it is, whether the Focus path agrees, then what it reads from one element rewritten
           * and from that one element read again afterwards.
           */
          private static <S> String report(Traversal<S, String> traversal, Object path, S none, S one) {
            S modified = Traversals.modify(traversal, UP, none);
            S rewritten = Traversals.modify(traversal, UP, one);
            return Traversals.getAll(traversal, none)
                + (modified == none ? " same" : " rebuilt")
                + (modified.equals(focusModify(path, none)) ? " agrees" : " differs")
                + " | "
                + Traversals.getAll(traversal, rewritten)
                + " "
                + Traversals.getAll(traversal, one);
          }

          public static String run(String which) {
            return switch (which) {
        %s      default -> throw new IllegalArgumentException(which);
            };
          }
        }
        """
            .formatted(cases));
  }

  private static Compilation compilation;
  private static GeneratedClassLoader loader;

  @BeforeAll
  static void compileOnce() {
    List<JavaFileObject> sources = new ArrayList<>();
    ROWS.forEach(row -> sources.add(row.source()));
    sources.add(probe());
    compilation =
        javac()
            .withProcessors(new TraversalProcessor(), new FocusProcessor())
            .withOptions("-Xlint:all")
            .compile(sources);
    loader = new GeneratedClassLoader(compilation);
  }

  @Test
  @DisplayName("should compile, with no warning in a generated file under -Xlint:all")
  void shouldCompileWithoutAWarning() {
    assertThat(compilation.errors().stream().map(NullableContainerTraversalTest::describe))
        .isEmpty();
    assertThat(
            compilation.warnings().stream()
                .filter(warning -> warning.getSource() != null)
                .filter(warning -> warning.getSource().getName().contains("SOURCE_OUTPUT"))
                .map(NullableContainerTraversalTest::describe))
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("ROWS")
  @DisplayName("should find no element in a null container, as the Focus path does")
  void shouldFindNoElementInANullContainer(Row row) throws Exception {
    Object result =
        loader.load("com.example.Probe").getMethod("run", String.class).invoke(null, row.name());

    assertThat(result).isEqualTo("[] same agrees | [A] [a]");
  }

  private static String describe(Diagnostic<? extends JavaFileObject> diagnostic) {
    String file = diagnostic.getSource() == null ? "-" : diagnostic.getSource().getName();
    return file + ":" + diagnostic.getLineNumber() + " " + diagnostic.getMessage(null);
  }
}
