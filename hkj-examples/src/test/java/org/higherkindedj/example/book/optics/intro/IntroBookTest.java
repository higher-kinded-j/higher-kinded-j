// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.intro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Optics chapter introduction makes about its before and after. */
@DisplayName("the Optics introduction: a wither cascade beside a Focus path")
class IntroBookTest {

  private static final Employee EMPLOYEE =
      new Employee("Alice", new Company("Initech Inc.", new Address("123 Fake St", "Anytown")));

  @Test
  @DisplayName("the wither cascade and the Focus path make the same change")
  void cascadeMatchesTheFocusPath() {
    Employee byWithers = IntroBook.moveByWithers(EMPLOYEE);
    Employee byFocus = IntroBook.moveByFocus(EMPLOYEE);

    assertThat(byFocus).isEqualTo(byWithers);
    assertThat(byFocus.company().address().street()).isEqualTo("456 Main St");
  }
}
