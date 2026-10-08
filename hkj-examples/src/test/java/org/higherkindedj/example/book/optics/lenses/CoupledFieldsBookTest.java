// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Coupled Fields page makes about its range. */
@DisplayName("the Coupled Fields page: two bounds a constructor checks together")
class CoupledFieldsBookTest {

  @Test
  @DisplayName("setting lo first asks for Range(11, 2), which the constructor refuses")
  void loFirstThrows() {
    assertThatThrownBy(CoupledFieldsBook::loFirst)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lo (11) must be <= hi (2)");
  }

  @Test
  @DisplayName("setting hi first, then lo, shifts the range up")
  void hiFirstShiftsUp() {
    CoupledFieldsBook.Steps steps = CoupledFieldsBook.hiFirst(new Range(1, 2));

    assertThat(steps.step1()).isEqualTo(new Range(1, 12));
    assertThat(steps.step2()).isEqualTo(new Range(11, 12));
  }

  @Test
  @DisplayName("shifting down works lo first, and hi first throws on Range(10, 1)")
  void theWorkingOrderReversesShiftingDown() {
    assertThat(CoupledFieldsBook.shiftDown()).isEqualTo(new Range(0, 1));
    assertThatThrownBy(() -> CoupledFieldsBook.hiLens.set(1, new Range(10, 11)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lo (10) must be <= hi (1)");
  }

  @Test
  @DisplayName("the paired lens shifts both bounds in one construction")
  void pairedLensShiftsBothBounds() {
    assertThat(CoupledFieldsBook.shiftUpTogether()).isEqualTo(new Range(11, 12));
  }
}
