package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.unwrap;
import static org.higherkindedj.hkt.list.ListKindHelper.wrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListKindHelper Tests")
class ListKindHelperTest {

  // A dummy Kind for testing invalid type scenarios
  private static final class DummyKindMarker { // Non-generic outer class for the marker
    private DummyKindMarker() {}

    // Nested static class Witness as the HKT marker for DummyKindMarker
    public static final class Witness {
      private Witness() {}
    }
  }

  // DummyListKind now correctly uses DummyKindMarker.Witness
  private record DummyListKind<A>(String id) implements Kind<DummyKindMarker.Witness, A> {}

  @Nested
  @DisplayName("wrap()")
  class WrapTests {
    @Test
    void wrap_nonEmptyList_shouldReturnListViewKind() {
      List<Integer> list = Arrays.asList(1, 2, 3);
      // wrap is a static import from ListKindHelper and returns Kind<ListKind.Witness, A>
      Kind<ListKind.Witness, Integer> kind = wrap(list);

      assertThat(kind).isNotNull();
      // The actual instance returned by ListKindHelper.wrap (via ListKind.of) is ListView
      assertThat(kind).isInstanceOf(ListView.class);
    }

    @Test
    void wrap_emptyList_shouldReturnListViewKindForEmptyList() {
      List<String> list = Collections.emptyList();
      Kind<ListKind.Witness, String> kind = wrap(list);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ListView.class);
      // ListKind.narrow(kind) casts to ListKind<A> interface, then unwrap()
      assertThat(ListKind.narrow(kind).unwrap()).isEmpty();
    }

    @Test
    void wrap_listWithNullElement_shouldWrapCorrectly() {
      List<String> list = Arrays.asList("a", null, "c");
      Kind<ListKind.Witness, String> kind = wrap(list);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ListView.class);
      assertThat(ListKind.narrow(kind).unwrap()).isEqualTo(list);
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {
    @Test
    void unwrap_validKind_shouldReturnOriginalList() {
      List<Integer> list = Arrays.asList(1, 2, 3);
      // Use ListKind.of which is the static factory on the ListKind interface
      Kind<ListKind.Witness, Integer> kind = ListKind.of(list);
      // unwrap is a static import from ListKindHelper
      assertThat(unwrap(kind)).isSameAs(list); // Assuming unwrap returns the exact instance
    }

    @Test
    void unwrap_validKindForEmptyList_shouldReturnEmptyList() {
      List<String> list = Collections.emptyList();
      Kind<ListKind.Witness, String> kind = ListKind.of(list);
      assertThat(unwrap(kind)).isSameAs(list);
    }

    @Test
    void unwrap_nullKind_shouldReturnEmptyList() {
      // Based on the ListKindHelper.unwrap(null) returning Collections.emptyList()
      List<Object> result = unwrap(null);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void unwrap_invalidKindType_shouldThrowClassCastException() {
      // Create a Kind of a different HKT marker
      // DummyListKind now correctly uses DummyKindMarker.Witness
      Kind<DummyKindMarker.Witness, Integer> unknownKind = new DummyListKind<>("dummy");

      // We expect ClassCastException when ListKind.narrow is called internally by unwrap
      // The cast (Kind<ListKind.Witness, Integer>) (Kind<?,?>) is to make the compiler pass this to
      // unwrap,
      // which expects Kind<ListKind.Witness, A>. The runtime ClassCastException happens inside
      // narrow.
      assertThatThrownBy(() -> unwrap((Kind<ListKind.Witness, Integer>) (Kind<?, ?>) unknownKind))
          .isInstanceOf(ClassCastException.class);
    }

    @Test
    void unwrap_kindHoldingNullListInternally_shouldThrow() {
      // The 'record ListView<A>(@NonNull List<A> list)' should prevent
      // a ListView instance from holding a null list if constructed via its canonical constructor
      // or via ListKind.of if ListKind.of has a null check for the input list.

      // Test that ListKind.of (which ListKindHelper.wrap uses) rejects null lists.
      // ListKindHelper.wrap already has a null check for the list.
      assertThatThrownBy(() -> wrap(null)) // wrap itself will throw for null list
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("list cannot be null for wrap");

      // If one were to bypass 'of' and 'wrap' and somehow create an invalid ListView
      // (e.g. if ListView was a class and allowed a null list), then unwrap might throw.
      // But with the record definition and @NonNull, this path is hard to achieve.
      // The primary guard is at the creation point.
    }
  }

  @Nested
  @DisplayName("unwrapOr()")
  class UnwrapOrTests {
    @Test
    void unwrapOr_validKind_shouldReturnOriginalList() {
      List<String> original = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = ListKind.of(original);
      List<String> defaultValue = Collections.singletonList("default");

      assertThat(ListKindHelper.unwrapOr(kind, defaultValue)).isSameAs(original);
    }

    @Test
    void unwrapOr_nullKind_shouldReturnDefaultList() {
      // List<Integer> original = Arrays.asList(1, 2); // Not used directly
      List<Integer> defaultValue = Collections.singletonList(0);
      Kind<ListKind.Witness, Integer> kind = null;

      assertThat(ListKindHelper.unwrapOr(kind, defaultValue)).isSameAs(defaultValue);
    }

    @Test
    void unwrapOr_nullDefault_shouldThrowNullPointerException() {
      // List<String> original = Arrays.asList("a", "b"); // Not needed for this test
      Kind<ListKind.Witness, String> kind =
          ListKind.of(Collections.emptyList()); // kind can be anything non-null
      assertThatThrownBy(() -> ListKindHelper.unwrapOr(kind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("defaultValue cannot be null");
    }
  }
}
