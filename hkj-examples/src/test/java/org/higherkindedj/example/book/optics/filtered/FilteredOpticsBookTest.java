// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.filtered;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Filtered Optics page makes about what a filtered traversal changes. */
@DisplayName("the Filtered Optics page: modify keeps, getAll excludes")
class FilteredOpticsBookTest {

  @Test
  @DisplayName("a filtered traversal composed with a lens reads and writes only the active names")
  void composedWithALens() {
    FilteredOpticsBook.Composed composed = FilteredOpticsBook.composeWithALens();

    assertThat(composed.names()).containsExactly("alice", "charlie");
    assertThat(composed.result())
        .containsExactly(
            new User("ALICE", true, 100, SubscriptionTier.PREMIUM),
            new User("bob", false, 200, SubscriptionTier.FREE),
            new User("CHARLIE", true, 150, SubscriptionTier.BASIC));
    assertThat(composed.names()).hasToString("[alice, charlie]");
  }

  @Test
  @DisplayName("modify keeps Bob in place unchanged, and getAll leaves him out")
  void preservedAndExcluded() {
    FilteredOpticsBook.Semantics semantics = FilteredOpticsBook.preservedAndExcluded();

    assertThat(semantics.modified())
        .containsExactly(
            new User("Alice", true, 200, SubscriptionTier.PREMIUM),
            new User("Bob", false, 200, SubscriptionTier.FREE),
            new User("Charlie", true, 250, SubscriptionTier.BASIC));
    assertThat(semantics.gotten())
        .containsExactly(
            new User("Alice", true, 100, SubscriptionTier.PREMIUM),
            new User("Charlie", true, 150, SubscriptionTier.BASIC));
    assertThat(semantics.gotten().getFirst())
        .hasToString("User[name=Alice, active=true, score=100, tier=PREMIUM]");
  }
}
