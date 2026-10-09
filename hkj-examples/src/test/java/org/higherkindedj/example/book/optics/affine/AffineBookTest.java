// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.affine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the values the Affines page, and the Composition Rules page beside it, show for the
 * examples in {@link AffineBook}. A region whose model is local records hands its values back as
 * objects, so those are checked by what they print, which is what the page shows.
 */
@DisplayName("the Affines page: zero or one focus")
class AffineBookTest {

  @Test
  @DisplayName("a hand-built affine reads a present value, reads nothing from empty, and wraps")
  void manualAffine() {
    AffineBook.ManualResults results = AffineBook.manual();

    assertThat(results.result()).contains("hello");
    assertThat(results.noMatch()).isEmpty();
    assertThat(results.updated()).contains("world");
  }

  @Test
  @DisplayName("Lens.andThen(Prism) reads present settings, nothing from empty, and fills on set")
  void lensThenPrism() {
    List<String> printed = AffineBook.lensThenPrism().stream().map(String::valueOf).toList();

    assertThat(printed)
        .containsExactly(
            "Optional[DatabaseSettings[host=localhost, port=5432]]",
            "Optional.empty",
            "Config[database=Optional[DatabaseSettings[host=newhost, port=3306]]]");
  }

  @Test
  @DisplayName("matches counts the two present values")
  void countsPresentValues() {
    assertThat(AffineBook.presentCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("modifyWhen and setWhen write when their predicate holds")
  void conditionalWrites() {
    AffineBook.Guarded guarded = AffineBook.conditional();

    assertThat(guarded.result()).contains("HELLO WORLD");
    assertThat(guarded.guarded()).contains("goodbye");
  }

  @Test
  @DisplayName("someWithRemove clears a present value")
  void removalClears() {
    assertThat(AffineBook.removal()).isEqualTo(Optional.empty());
  }

  @Test
  @DisplayName("four composed optics read, miss and write a doubly optional nickname")
  void deepOptional() {
    List<String> printed = AffineBook.deepOptional().stream().map(String::valueOf).toList();

    assertThat(printed)
        .containsExactly(
            "Optional[Countess]",
            "Optional.empty",
            "Review[sku=LAMP, author=Optional[CustomerProfile[name=Ada,"
                + " nickname=Optional[Lady Lovelace], altEmail=Optional.empty]]]");
  }
}
