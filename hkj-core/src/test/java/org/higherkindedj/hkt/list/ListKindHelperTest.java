// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListKindHelper Tests")
class ListKindHelperTest {

  @Nested
  @DisplayName("widen()")
  class WidenTests {

    @Test
    void widen_shouldReturnListViewForValidList() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = LIST.widen(list);
      assertThat(kind)
          .isInstanceOf(ListKind.ListView.class); // ListKind.ListView is the concrete type
      // To verify content, unwrap and check
      assertThat(LIST.narrow(kind)).isEqualTo(list);
    }

    @Test
    void widen_shouldReturnListViewForEmptyList() {
      List<Integer> emptyList = Collections.emptyList();
      Kind<ListKind.Witness, Integer> kind = LIST.widen(emptyList);
      assertThat(kind).isInstanceOf(ListKind.ListView.class);
      assertThat(LIST.narrow(kind)).isEqualTo(emptyList);
    }

    @Test
    void widen_shouldThrowForNullInput() {
      assertThatThrownBy(() -> LIST.widen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input List cannot be null");
    }
  }

  @Nested
  @DisplayName("narrow()")
  class NarrowTests {

    @Test
    void narrow_shouldReturnOriginalList() {
      List<Double> originalList = Arrays.asList(1.0, 2.5);
      Kind<ListKind.Witness, Double> kind = LIST.widen(originalList);
      assertThat(LIST.narrow(kind)).isSameAs(originalList);
      assertThat(LIST.narrow(kind)).isEqualTo(originalList);
    }

    // Dummy Kind for testing invalid type unwrap
    record DummyListKind<A>() implements Kind<ListKind.Witness, A> {}

    @Test
    void narrow_shouldThrowForUnknownKindType() {
      Kind<ListKind.Witness, String> unknownKind = new DummyListKind<>();
      assertThatThrownBy(() -> LIST.narrow(unknownKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a List:");
    }
  }

  @Nested
  @DisplayName("unwrapOr()")
  class UnwrapOrTests {
    @Test
    void unwrapOr_shouldReturnValueWhenPresent() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = LIST.widen(list);
      List<String> defaultList = Collections.singletonList("default");
      assertThat(LIST.unwrapOr(kind, defaultList)).isEqualTo(list);
    }

    @Test
    void unwrapOr_shouldReturnDefaultWhenKindIsNull() {
      List<String> defaultList = Collections.singletonList("default");
      assertThat(LIST.unwrapOr(null, defaultList)).isSameAs(defaultList);
    }

    @Test
    void unwrapOr_shouldThrowNPEWhenDefaultIsNull() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = LIST.widen(list);
      assertThatThrownBy(() -> LIST.unwrapOr(kind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Input defaultValue cannot be null");
    }
  }
}
