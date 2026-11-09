// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.FocusProcessor;
import org.higherkindedj.optics.processing.TraversalProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A traversal and a Focus path over a sorted container keep the order the source keeps.
 *
 * <p>Each container is compiled under {@code @GenerateTraversals} and
 * {@code @GenerateFocus(widenCollections = true)}, beside a probe that names the generated optics
 * as user code would, and the probe is run. A source in reverse order must come back in reverse
 * order from either route, an element with no natural order must be sorted by the source's
 * comparator rather than fail, a source in natural order, whose Eclipse comparator is null, must
 * stay in natural order, and a mutable source must be left as it was. A {@code PSortedSet} or
 * {@code PSortedMap} of another implementation may report a null comparator for natural order, as
 * {@code SortedSet} allows, and must stay in natural order too.
 */
@DisplayName("Generated optics over a sorted container")
class SortedContainerOrderTest {

  private static final String IMPORTS =
      """
      package com.example;

      import java.util.*;
      import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
      import org.eclipse.collections.api.set.sorted.MutableSortedSet;
      import org.higherkindedj.optics.annotations.GenerateFocus;
      import org.higherkindedj.optics.annotations.GenerateTraversals;
      import org.pcollections.PSortedMap;
      import org.pcollections.PSortedSet;

      """;

  private static final String BOTH =
      "@GenerateTraversals\n@GenerateFocus(widenCollections = true)\n";

  private static JavaFileObject source(String name, String body) {
    return JavaFileObjects.forSourceString("com.example." + name, IMPORTS + body);
  }

  private static JavaFileObject record(String name, String component) {
    return source(name, BOTH + "public record %s(%s) {}".formatted(name, component));
  }

  /** Names every generated optic as user code would, and reports what each hands back. */
  private static final JavaFileObject PROBE =
      JavaFileObjects.forSourceString(
          "com.example.Probe",
          """
          package com.example;

          import java.lang.reflect.Proxy;
          import java.util.*;
          import java.util.function.Function;
          import org.eclipse.collections.api.factory.SortedSets;
          import org.higherkindedj.optics.util.Traversals;
          import org.pcollections.PSortedMap;
          import org.pcollections.PSortedSet;
          import org.pcollections.TreePMap;
          import org.pcollections.TreePSet;

          public final class Probe {
            private static final Comparator<String> REVERSE = Comparator.reverseOrder();
            private static final Comparator<Leaf> BY_NAME_REVERSED =
                Comparator.comparing(Leaf::name).reversed();
            private static final Function<String, String> UP = String::toUpperCase;
            private static final Function<Leaf, Leaf> LEAF_UP =
                leaf -> new Leaf(leaf.name().toUpperCase());
            private static final Function<Integer, Integer> TENFOLD = n -> n * 10;

            private Probe() {}

            private static List<String> strings(Iterable<?> elements) {
              List<String> out = new ArrayList<>();
              elements.forEach(element -> out.add(String.valueOf(element)));
              return out;
            }

            private static List<String> leaves(Iterable<Leaf> elements) {
              List<String> out = new ArrayList<>();
              elements.forEach(leaf -> out.add(leaf.name()));
              return out;
            }

            private static List<String> entries(Map<?, ?> map) {
              return map.entrySet().stream()
                  .map(entry -> keyName(entry.getKey()) + "=" + entry.getValue())
                  .toList();
            }

            private static String keyName(Object key) {
              return key instanceof Leaf leaf ? leaf.name() : String.valueOf(key);
            }

            /**
             * Another implementation of a PCollections interface, which reports a null comparator
             * for natural order, as SortedSet allows, and hands every other call to the delegate.
             */
            private static <T> T nullComparator(Class<T> type, Object delegate) {
              return type.cast(
                  Proxy.newProxyInstance(
                      type.getClassLoader(),
                      new Class<?>[] {type},
                      (proxy, method, args) ->
                          method.getName().equals("comparator")
                              ? null
                              : method.invoke(delegate, args)));
            }

            private static final Ec EC = new Ec(SortedSets.mutable.of(REVERSE, "a", "b", "c"));
            private static final Ei EI = new Ei(SortedSets.immutable.of(REVERSE, "a", "b", "c"));
            private static final Pc PC = new Pc(TreePSet.from(REVERSE, List.of("a", "b", "c")));
            private static final Pm PM =
                new Pm(TreePMap.from(REVERSE, Map.of("a", 1, "b", 2, "c", 3)));
            private static final Ec EC_NATURAL = new Ec(SortedSets.mutable.of("c", "a", "b"));
            private static final Ei EI_NATURAL = new Ei(SortedSets.immutable.of("c", "a", "b"));
            @SuppressWarnings("unchecked") // a proxy's class names no type argument
            private static final Pc PC_FOREIGN =
                new Pc(nullComparator(PSortedSet.class, TreePSet.from(List.of("c", "a", "b"))));
            @SuppressWarnings("unchecked") // a proxy's class names no type argument
            private static final Pm PM_FOREIGN =
                new Pm(
                    nullComparator(PSortedMap.class, TreePMap.from(Map.of("c", 3, "a", 1, "b", 2))));
            private static final EcLeaf EC_LEAF =
                new EcLeaf(SortedSets.mutable.of(BY_NAME_REVERSED, new Leaf("a"), new Leaf("b")));
            private static final EiLeaf EI_LEAF =
                new EiLeaf(
                    SortedSets.immutable.of(BY_NAME_REVERSED, new Leaf("a"), new Leaf("b")));
            private static final PcLeaf PC_LEAF =
                new PcLeaf(TreePSet.from(BY_NAME_REVERSED, List.of(new Leaf("a"), new Leaf("b"))));
            private static final PmLeaf PM_LEAF =
                new PmLeaf(
                    TreePMap.from(BY_NAME_REVERSED, Map.of(new Leaf("a"), 1, new Leaf("b"), 2)));

            public static List<String> run(String which) {
              return switch (which) {
                case "ec traversal" -> strings(Traversals.modify(EcTraversals.names(), UP, EC).names());
                case "ec focus" -> strings(EcFocus.names().modifyAll(UP, EC).names());
                case "ei traversal" -> strings(Traversals.modify(EiTraversals.names(), UP, EI).names());
                case "ei focus" -> strings(EiFocus.names().modifyAll(UP, EI).names());
                case "pc traversal" -> strings(Traversals.modify(PcTraversals.names(), UP, PC).names());
                case "pc focus" -> strings(PcFocus.names().modifyAll(UP, PC).names());
                case "pm traversal" ->
                    entries(Traversals.modify(PmTraversals.scores(), TENFOLD, PM).scores());
                case "pm focus" -> entries(PmFocus.scores().modifyAll(TENFOLD, PM).scores());
                case "ec natural traversal" ->
                    strings(Traversals.modify(EcTraversals.names(), UP, EC_NATURAL).names());
                case "ec natural focus" -> strings(EcFocus.names().modifyAll(UP, EC_NATURAL).names());
                case "ei natural traversal" ->
                    strings(Traversals.modify(EiTraversals.names(), UP, EI_NATURAL).names());
                case "ei natural focus" -> strings(EiFocus.names().modifyAll(UP, EI_NATURAL).names());
                case "pc foreign traversal" ->
                    strings(Traversals.modify(PcTraversals.names(), UP, PC_FOREIGN).names());
                case "pc foreign focus" -> strings(PcFocus.names().modifyAll(UP, PC_FOREIGN).names());
                case "pm foreign traversal" ->
                    entries(Traversals.modify(PmTraversals.scores(), TENFOLD, PM_FOREIGN).scores());
                case "pm foreign focus" ->
                    entries(PmFocus.scores().modifyAll(TENFOLD, PM_FOREIGN).scores());
                case "ec leaf traversal" ->
                    leaves(Traversals.modify(EcLeafTraversals.leaves(), LEAF_UP, EC_LEAF).leaves());
                case "ec leaf focus" ->
                    leaves(EcLeafFocus.leaves().modifyAll(LEAF_UP, EC_LEAF).leaves());
                case "ei leaf traversal" ->
                    leaves(Traversals.modify(EiLeafTraversals.leaves(), LEAF_UP, EI_LEAF).leaves());
                case "ei leaf focus" ->
                    leaves(EiLeafFocus.leaves().modifyAll(LEAF_UP, EI_LEAF).leaves());
                case "pc leaf traversal" ->
                    leaves(Traversals.modify(PcLeafTraversals.leaves(), LEAF_UP, PC_LEAF).leaves());
                case "pc leaf focus" ->
                    leaves(PcLeafFocus.leaves().modifyAll(LEAF_UP, PC_LEAF).leaves());
                case "pm leaf traversal" ->
                    entries(Traversals.modify(PmLeafTraversals.scores(), TENFOLD, PM_LEAF).scores());
                case "pm leaf focus" ->
                    entries(PmLeafFocus.scores().modifyAll(TENFOLD, PM_LEAF).scores());
                case "ec source untouched" -> {
                  // A mutable source is read, never rebuilt in place, by either route.
                  var traversed = Traversals.modify(EcTraversals.names(), UP, EC);
                  var focused = EcFocus.names().modifyAll(UP, EC);
                  if (traversed.names() == EC.names() || focused.names() == EC.names()) {
                    throw new AssertionError("the rebuild handed back the source's own set");
                  }
                  yield strings(EC.names());
                }
                default -> throw new IllegalArgumentException(which);
              };
            }
          }
          """);

  private static Compilation compilation;
  private static GeneratedClassLoader loader;

  @BeforeAll
  static void compileOnce() {
    compilation =
        javac()
            .withProcessors(new TraversalProcessor(), new FocusProcessor())
            .withOptions("-Xlint:all")
            .compile(
                source("Leaf", "public record Leaf(String name) {}"),
                record("Ec", "MutableSortedSet<String> names"),
                record("Ei", "ImmutableSortedSet<String> names"),
                record("Pc", "PSortedSet<String> names"),
                record("Pm", "PSortedMap<String, Integer> scores"),
                record("EcLeaf", "MutableSortedSet<Leaf> leaves"),
                record("EiLeaf", "ImmutableSortedSet<Leaf> leaves"),
                record("PcLeaf", "PSortedSet<Leaf> leaves"),
                record("PmLeaf", "PSortedMap<Leaf, Integer> scores"),
                PROBE);
    loader = new GeneratedClassLoader(compilation);
  }

  @Test
  @DisplayName("should compile, with no warning in a generated file under -Xlint:all")
  void shouldCompileWithoutAWarning() {
    assertThat(compilation.errors().stream().map(SortedContainerOrderTest::describe)).isEmpty();
    assertThat(
            compilation.warnings().stream()
                .filter(warning -> warning.getSource() != null)
                .filter(warning -> warning.getSource().getName().contains("SOURCE_OUTPUT"))
                .map(SortedContainerOrderTest::describe))
        .isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          ec traversal         | C, B, A
          ec focus             | C, B, A
          ei traversal         | C, B, A
          ei focus             | C, B, A
          pc traversal         | C, B, A
          pc focus             | C, B, A
          pm traversal         | c=30, b=20, a=10
          pm focus             | c=30, b=20, a=10
          ec natural traversal | A, B, C
          ec natural focus     | A, B, C
          ei natural traversal | A, B, C
          ei natural focus     | A, B, C
          pc foreign traversal | A, B, C
          pc foreign focus     | A, B, C
          pm foreign traversal | a=10, b=20, c=30
          pm foreign focus     | a=10, b=20, c=30
          ec leaf traversal    | B, A
          ec leaf focus        | B, A
          ei leaf traversal    | B, A
          ei leaf focus        | B, A
          pc leaf traversal    | B, A
          pc leaf focus        | B, A
          pm leaf traversal    | b=20, a=10
          pm leaf focus        | b=20, a=10
          ec source untouched  | c, b, a
          """)
  @DisplayName("should keep the source's order")
  void shouldKeepTheSourcesOrder(String route, String expected) throws Exception {
    Object result =
        loader.load("com.example.Probe").getMethod("run", String.class).invoke(null, route);

    assertThat(result).isEqualTo(List.of(expected.split(", ")));
  }

  private static String describe(Diagnostic<? extends JavaFileObject> diagnostic) {
    String file = diagnostic.getSource() == null ? "-" : diagnostic.getSource().getName();
    return file + ":" + diagnostic.getLineNumber() + " " + diagnostic.getMessage(null);
  }
}
