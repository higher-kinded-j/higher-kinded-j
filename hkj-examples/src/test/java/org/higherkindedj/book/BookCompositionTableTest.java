// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the book's composition table to the {@code andThen} overloads the optics declare.
 *
 * <p>The table on the Composition Rules page answers one question for every pair of optic types:
 * what does {@code first.andThen(second)} return? It used to be written by hand, and it promised
 * results the API did not have: {@code Iso} had no {@code andThen(Traversal)}, so that composition
 * bound {@code Optic.andThen} and returned a bare {@code Optic}, while the table said {@code
 * Traversal}. Nothing compiled the table, so nothing noticed.
 *
 * <p>So the table is now read from the interfaces themselves. For each pair this test finds the
 * {@code andThen} overload whose parameter is the second optic type and records its return type; a
 * pair with no such overload falls back to {@code Optic.andThen} and is rendered as {@code Optic}.
 * The rendering must match the golden file the page includes, byte for byte, so a new or removed
 * overload fails here until the book says the same.
 */
@DisplayName("the book's composition table is read from the andThen overloads")
class BookCompositionTableTest {

  /** The optic types the table covers, in the order the page lists them. */
  private static final List<Class<?>> OPTICS =
      List.of(Iso.class, Lens.class, Prism.class, Affine.class, Traversal.class);

  /** The golden file composition_rules.md includes. */
  private static final String GOLDEN = "/golden/optics-composition-table.md.golden";

  @Test
  @DisplayName("the golden table matches what each andThen overload returns")
  void goldenTableMatchesTheOverloads() {
    String rendered = render();
    assertThat(golden())
        .as(
            """
            The composition table on hkj-book/src/optics/composition_rules.md no longer matches \
            the andThen overloads. If the API change is intended, replace \
            hkj-examples/src/test/resources%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("every pair of optics composes to an optic type, not a bare Optic")
  void everyPairHasAnOverload() {
    assertThat(render()).doesNotContain("`Optic`");
  }

  /** The table as markdown: one row per first optic, one column per second. */
  static String render() {
    String header =
        "| `first.andThen(second)` | "
            + OPTICS.stream().map(Class::getSimpleName).collect(Collectors.joining(" | "))
            + " |";
    String rule = "|---" + "|---".repeat(OPTICS.size()) + "|";
    String rows =
        OPTICS.stream()
            .map(
                first ->
                    "| **"
                        + first.getSimpleName()
                        + "** | "
                        + OPTICS.stream()
                            .map(second -> resultOf(first, second))
                            .collect(Collectors.joining(" | "))
                        + " |")
            .collect(Collectors.joining("\n"));
    return header + "\n" + rule + "\n" + rows + "\n";
  }

  /** What {@code first.andThen(second)} returns, or {@code Optic} when no overload takes it. */
  private static String resultOf(Class<?> first, Class<?> second) {
    return Arrays.stream(first.getMethods())
        .filter(m -> m.getName().equals("andThen") && !m.isBridge() && !m.isSynthetic())
        .filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == second)
        .map(Method::getReturnType)
        .map(Class::getSimpleName)
        .findFirst()
        .orElse("`Optic`");
  }

  private static String golden() {
    try (InputStream in = BookCompositionTableTest.class.getResourceAsStream(GOLDEN)) {
      assertThat(in).as("the golden table %s is on the test classpath", GOLDEN).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
