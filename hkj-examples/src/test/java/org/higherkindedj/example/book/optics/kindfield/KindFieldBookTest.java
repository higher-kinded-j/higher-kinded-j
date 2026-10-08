// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.kindfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Kind Field Support page shows for {@code headOption()}. */
@DisplayName("Kind Field Support: headOption reads the first and writes every one")
class KindFieldBookTest {

  private static final Member ALICE =
      new Member("Alice", LIST.widen(List.of(new Skill("Java", 95), new Skill("SQL", 40))));

  @Test
  @DisplayName("the read is the first skill only")
  void readsTheFirst() {
    assertThat(KindFieldBook.narrow(ALICE).first()).contains(new Skill("Java", 95));
  }

  @Test
  @DisplayName("the write replaces every skill, because it is the traversal's setAll")
  void writesEveryOne() {
    Member flattened = KindFieldBook.narrow(ALICE).flattened();

    assertThat(LIST.narrow(flattened.skills()))
        .containsExactly(new Skill("Go", 50), new Skill("Go", 50));
  }
}
