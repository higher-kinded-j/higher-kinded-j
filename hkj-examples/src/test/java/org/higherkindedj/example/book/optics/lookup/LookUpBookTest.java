// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.higherkindedj.example.book.optics.cast.CastFixtures;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Look It Up introduction makes about what a lens declares. */
@DisplayName("the Look It Up introduction: a lens declares get, its fold declares getAll")
class LookUpBookTest {

  @Test
  @DisplayName("get on the lens and getAll on its fold read the same customer of Ada's order")
  void bothReadTheCustomer() {
    LookUpBook.Reads reads = LookUpBook.read(CastFixtures.ORDER);

    assertThat(reads.name()).isEqualTo("Ada");
    assertThat(reads.all()).containsExactly("Ada");
  }

  @Test
  @DisplayName("getAll is declared on Fold and not on Lens")
  void getAllIsOnFoldNotLens() {
    assertThat(Arrays.stream(Lens.class.getMethods()).map(Method::getName))
        .doesNotContain("getAll");
    assertThat(Arrays.stream(Fold.class.getMethods()).map(Method::getName)).contains("getAll");
  }
}
