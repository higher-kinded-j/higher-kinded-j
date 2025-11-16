// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListMonad Alternative Operations Test Suite")
class ListAlternativeTest {

  private Alternative<ListKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = ListMonad.INSTANCE;
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns empty list")
    void emptyReturnsEmptyList() {
      Kind<ListKind.Witness, Integer> empty = alternative.empty();

      List<Integer> list = LIST.narrow(empty);
      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<ListKind.Witness, String> emptyString = alternative.empty();
      Kind<ListKind.Witness, Integer> emptyInt = alternative.empty();

      assertThat(LIST.narrow(emptyString)).isEmpty();
      assertThat(LIST.narrow(emptyInt)).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElse() Tests")
  class OrElseTests {

    @Test
    @DisplayName("orElse() concatenates two non-empty lists")
    void orElseConcatenatesLists() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(3, 4));

      Kind<ListKind.Witness, Integer> result = alternative.orElse(list1, () -> list2);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("orElse() with empty first list")
    void orElseWithEmptyFirst() {
      Kind<ListKind.Witness, Integer> empty = alternative.empty();
      Kind<ListKind.Witness, Integer> list = LIST.widen(Arrays.asList(1, 2));

      Kind<ListKind.Witness, Integer> result = alternative.orElse(empty, () -> list);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2);
    }

    @Test
    @DisplayName("orElse() with empty second list")
    void orElseWithEmptySecond() {
      Kind<ListKind.Witness, Integer> list = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> empty = alternative.empty();

      Kind<ListKind.Witness, Integer> result = alternative.orElse(list, () -> empty);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2);
    }

    @Test
    @DisplayName("orElse() with both empty lists")
    void orElseWithBothEmpty() {
      Kind<ListKind.Witness, Integer> empty1 = alternative.empty();
      Kind<ListKind.Witness, Integer> empty2 = alternative.empty();

      Kind<ListKind.Witness, Integer> result = alternative.orElse(empty1, () -> empty2);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).isEmpty();
    }

    @Test
    @DisplayName("orElse() always evaluates both lists")
    void orElseAlwaysEvaluatesBoth() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));
      boolean[] evaluated = {false};

      Kind<ListKind.Witness, Integer> result =
          alternative.orElse(
              list1,
              () -> {
                evaluated[0] = true;
                return LIST.widen(Arrays.asList(3, 4));
              });

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2, 3, 4);
      assertThat(evaluated[0]).isTrue(); // For List, second is always evaluated
    }
  }

  @Nested
  @DisplayName("guard() Tests")
  class GuardTests {

    @Test
    @DisplayName("guard(true) returns singleton list with Unit")
    void guardTrueReturnsSingletonList() {
      Kind<ListKind.Witness, Unit> result = alternative.guard(true);

      List<Unit> list = LIST.narrow(result);
      assertThat(list).hasSize(1);
      assertThat(list.get(0)).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns empty list")
    void guardFalseReturnsEmptyList() {
      Kind<ListKind.Witness, Unit> result = alternative.guard(false);

      List<Unit> list = LIST.narrow(result);
      assertThat(list).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() concatenates all lists")
    void orElseAllConcatenatesAll() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(3, 4));
      Kind<ListKind.Witness, Integer> list3 = LIST.widen(Arrays.asList(5, 6));

      Kind<ListKind.Witness, Integer> result =
          alternative.orElseAll(list1, () -> list2, () -> list3);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("orElseAll() with some empty lists")
    void orElseAllWithSomeEmpty() {
      Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> empty = alternative.empty();
      Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(3, 4));

      Kind<ListKind.Witness, Integer> result =
          alternative.orElseAll(list1, () -> empty, () -> list2);

      List<Integer> resultList = LIST.narrow(result);
      assertThat(resultList).containsExactly(1, 2, 3, 4);
    }
  }

  @Nested
  @DisplayName("Alternative Laws")
  class AlternativeLaws {

    @Test
    @DisplayName("Left Identity: orElse(empty(), () -> fa) == fa")
    void leftIdentityLaw() {
      Kind<ListKind.Witness, Integer> fa = LIST.widen(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = alternative.orElse(alternative.empty(), () -> fa);

      assertThat(LIST.narrow(result)).isEqualTo(LIST.narrow(fa));
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<ListKind.Witness, Integer> fa = LIST.widen(Arrays.asList(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      assertThat(LIST.narrow(result)).isEqualTo(LIST.narrow(fa));
    }

    @Test
    @DisplayName(
        "Associativity: orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), ()"
            + " -> fc)")
    void associativityLaw() {
      Kind<ListKind.Witness, Integer> fa = LIST.widen(Arrays.asList(1, 2));
      Kind<ListKind.Witness, Integer> fb = LIST.widen(Arrays.asList(3, 4));
      Kind<ListKind.Witness, Integer> fc = LIST.widen(Arrays.asList(5, 6));

      Kind<ListKind.Witness, Integer> left =
          alternative.orElse(fa, () -> alternative.orElse(fb, () -> fc));
      Kind<ListKind.Witness, Integer> right =
          alternative.orElse(alternative.orElse(fa, () -> fb), () -> fc);

      assertThat(LIST.narrow(left)).isEqualTo(LIST.narrow(right));
    }
  }
}
