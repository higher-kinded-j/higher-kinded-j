// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.laws.LensLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Lenses page's "Why a lens, when you have {@code @With}?" section makes. */
@DisplayName("the Lenses page: a wither beside a lens")
class LensesBookTest {

  private static final Employee EMPLOYEE =
      new Employee("Alice", new Company("Initech Inc.", new Address("123 Fake St", "Anytown")));

  private static final Range RANGE = new Range(1, 3);

  @Test
  @DisplayName("the wither cascade and the composed lens make the same change")
  void cascadeMatchesTheComposedLens() {
    Employee moved = LensesBook.moveByWithers(EMPLOYEE);

    assertThat(moved).isEqualTo(LensesBook.EMPLOYEE_TO_STREET.set("456 Main St", EMPLOYEE));
    assertThat(moved.company().address().street()).isEqualTo("456 Main St");
  }

  @Test
  @DisplayName("chained withers construct at each call, so the constructor refuses Range(5, 3)")
  void chainedWithersRefuseTheIntermediateRange() {
    assertThatThrownBy(() -> RANGE.withLo(5).withHi(10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lo (5) must be <= hi (3)");
  }

  @Test
  @DisplayName("which order of withers works depends on the direction of the move")
  void theWorkingOrderDependsOnTheDirection() {
    assertThat(RANGE.withHi(10).withLo(5)).isEqualTo(new Range(5, 10));
    assertThatThrownBy(() -> new Range(5, 10).withHi(3).withLo(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lo (5) must be <= hi (3)");
  }

  @Test
  @DisplayName("two lens writes refuse the same intermediate range")
  void twoLensWritesRefuseTheIntermediateRange() {
    assertThatThrownBy(() -> RangeLenses.hi().set(10, RangeLenses.lo().set(5, RANGE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lo (5) must be <= hi (3)");
  }

  @Test
  @DisplayName("Lens.paired constructs once, so it moves a range either way")
  void pairedLensConstructsOnce() {
    Lens<Range, Pair<Integer, Integer>> bounds =
        Lens.paired(RangeLenses.lo(), RangeLenses.hi(), Range::new);

    assertThat(bounds.set(Pair.of(5, 10), RANGE)).isEqualTo(new Range(5, 10));
    assertThat(bounds.set(Pair.of(1, 3), new Range(5, 10))).isEqualTo(RANGE);
  }

  @Test
  @DisplayName("a lowercasing constructor breaks set-get, and the law check catches it")
  void normalisingConstructorBreaksSetGet() {
    NormalisedEmail stored = new NormalisedEmail("ada@example.com");

    assertThat(NormalisedEmailLenses.value().set("Ada@Example.com", stored).value())
        .isEqualTo("ada@example.com");
    assertThatThrownBy(
            () ->
                LensLaws.assertLensLaws(
                    NormalisedEmailLenses.value(), stored, "Ada@Example.com", "GRACE@example.com"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Lens set-get");
  }

  @Test
  @DisplayName("normalising at the boundary keeps the record's lens lawful")
  void boundaryFixKeepsTheLensLawful() {
    // ANCHOR: law_check
    ContactEmail stored = LensesBook.fromRequest("  Ada@Example.com ");
    // The values set have capitals, which a lowercasing constructor would change
    LensLaws.assertLensLaws(
        ContactEmailLenses.value(), stored, "Ada@Example.com", "GRACE@example.com");
    // ANCHOR_END: law_check

    assertThat(stored.value()).isEqualTo("ada@example.com");
  }
}
