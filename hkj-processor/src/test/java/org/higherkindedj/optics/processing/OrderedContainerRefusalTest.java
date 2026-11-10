// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A container its generator orders by a wildcard is refused a traversal, and {@code @GenerateFocus}
 * offers {@code @GenerateTraversals} in its place only where that annotation takes the component.
 *
 * <p>The test-scope {@code OrdGenerator} supports {@code com.example.hkjtest.Ord<K, V>}, focusing
 * {@code V} and ordered by {@code K}, as a sorted map's generator is. A wildcard in {@code K} is
 * the refused shape; a wildcard in {@code V} is traversed as any other.
 */
@DisplayName("A container ordered by a wildcard")
class OrderedContainerRefusalTest {

  private static final JavaFileObject ORD =
      JavaFileObjects.forSourceString(
          "com.example.hkjtest.Ord",
          """
          package com.example.hkjtest;

          import org.higherkindedj.optics.Each;

          /** A container ordered by K, holding V. */
          public class Ord<K, V> {
            public static <S, A> Each<S, A> each() {
              throw new UnsupportedOperationException();
            }
          }
          """);

  private static final JavaFileObject LEAF =
      JavaFileObjects.forSourceString(
          "com.example.Leaf",
          """
          package com.example;

          public record Leaf(String name) {}
          """);

  private static JavaFileObject holder(String annotation, String components) {
    return JavaFileObjects.forSourceString(
        "com.example.Holder",
        """
        package com.example;

        import com.example.hkjtest.Ord;
        import java.util.Optional;
        import org.higherkindedj.optics.annotations.GenerateFocus;
        import org.higherkindedj.optics.annotations.GenerateTraversals;

        %s
        public record Holder(%s) {}
        """
            .formatted(annotation, components));
  }

  @Test
  @DisplayName("should draw a note and no traversal under @GenerateTraversals")
  void shouldNoteTheRefusalUnderGenerateTraversals() {
    Compilation compilation =
        javac()
            .withProcessors(new TraversalProcessor())
            .compile(
                ORD,
                LEAF,
                holder(
                    "@GenerateTraversals",
                    "Ord<? extends Leaf, Leaf> ordered, Ord<Leaf, ? extends Leaf> valued"));

    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadNoteContaining(
            "@GenerateTraversals: no traversal was generated for component 'Holder.ordered' of"
                + " type Ord<? extends Leaf, Leaf>. Ord<? extends Leaf, Leaf> is rebuilt with the"
                + " source's comparator, and the wildcard it is ordered by leaves that comparator"
                + " typed for an unknown type, so the generated rebuild would not compile."
                + " Declare the component with concrete type arguments, such as Ord<Leaf, Leaf>.");
    assertGeneratedCodeDoesNotContain(compilation, "com.example.HolderTraversals", "ordered()");
    // A wildcard in the argument the container is not ordered by is traversed as any other.
    assertGeneratedCodeContains(
        compilation, "com.example.HolderTraversals", "Traversal<Holder, Leaf> valued()");
  }

  @Test
  @DisplayName(
      "should offer @GenerateFocus users @GenerateTraversals only where it takes the field")
  void shouldOfferGenerateTraversalsOnlyWhereItTakesTheComponent() {
    Compilation compilation =
        javac()
            .withProcessors(new FocusProcessor())
            .compile(
                ORD,
                LEAF,
                holder(
                    "@GenerateFocus(widenCollections = true)",
                    "Ord<? extends Leaf, Leaf> ordered, Ord<Leaf, ? extends Leaf> valued,"
                        + " Optional<Ord<? extends Leaf, Leaf>> nested, Ord raw"));

    assertThat(compilation).failed();
    // @GenerateTraversals refuses the component itself, so only the lens is offered.
    assertThat(errorFor(compilation, "Holder.ordered"))
        .contains("has a wildcard type argument in Ord<? extends Leaf, Leaf>.")
        .contains(
            "or drop @GenerateFocus from the record: @GenerateLenses composes no optic instance"
                + " and takes the component as written.")
        .doesNotContain("@GenerateTraversals");
    // A wildcard the container is not ordered by: @GenerateTraversals takes it.
    assertThat(errorFor(compilation, "Holder.valued"))
        .contains("@GenerateLenses and @GenerateTraversals compose no optic instance");
    // The ordered container sits inside an Optional, which @GenerateTraversals traverses whole.
    assertThat(errorFor(compilation, "Holder.nested"))
        .contains("has a wildcard type argument in Ord<? extends Leaf, Leaf>.")
        .contains("@GenerateLenses and @GenerateTraversals compose no optic instance");
    // A raw container has no argument to be ordered by, and no element to traverse either.
    assertThat(errorFor(compilation, "Holder.raw"))
        .contains("has a raw Ord.")
        .doesNotContain("@GenerateTraversals");
  }

  /** The one error reported against the named component. */
  private static String errorFor(Compilation compilation, String component) {
    return compilation.errors().stream()
        .map(diagnostic -> diagnostic.getMessage(null))
        .filter(message -> message.contains("'" + component + "'"))
        .reduce(
            (first, second) -> {
              throw new AssertionError("more than one error names " + component);
            })
        .orElseThrow(() -> new AssertionError("no error names " + component));
  }
}
