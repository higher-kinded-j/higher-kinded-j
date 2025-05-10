package org.higherkindedj.hkt.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ListKindHelper Tests")
class ListKindHelperTest {

  @Nested
  @DisplayName("wrap()")
  class WrapTests {

    @Test
    void wrap_shouldReturnListViewForValidList() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = ListKindHelper.wrap(list);
      assertThat(kind)
          .isInstanceOf(ListKind.ListView.class); // ListKind.ListView is the concrete type
      // To verify content, unwrap and check
      assertThat(ListKindHelper.unwrap(kind)).isEqualTo(list);
    }

    @Test
    void wrap_shouldReturnListViewForEmptyList() {
      List<Integer> emptyList = Collections.emptyList();
      Kind<ListKind.Witness, Integer> kind = ListKindHelper.wrap(emptyList);
      assertThat(kind).isInstanceOf(ListKind.ListView.class);
      assertThat(ListKindHelper.unwrap(kind)).isEqualTo(emptyList);
    }

    @Test
    void wrap_shouldThrowForNullInput() {
      assertThatThrownBy(() -> ListKindHelper.wrap(null))
          .isInstanceOf(
              NullPointerException.class) // Or specific exception from Objects.requireNonNull
          .hasMessageContaining("list cannot be null for wrap");
    }
  }

  @Nested
  @DisplayName("unwrap()")
  class UnwrapTests {

    @Test
    void unwrap_shouldReturnOriginalList() {
      List<Double> originalList = Arrays.asList(1.0, 2.5);
      Kind<ListKind.Witness, Double> kind = ListKindHelper.wrap(originalList);
      assertThat(ListKindHelper.unwrap(kind))
          .isSameAs(originalList); // It's List.copyOf in wrap, so not same
      // but content should be equal
      assertThat(ListKindHelper.unwrap(kind)).isEqualTo(originalList);
    }

    @Test
    void unwrap_shouldReturnEmptyListForNullKind() {
      // This is specific behavior of ListKindHelper.unwrap(null)
      assertThat(ListKindHelper.unwrap(null)).isEqualTo(Collections.emptyList());
    }

    // Dummy Kind for testing invalid type unwrap
    record DummyListKind<A>() implements Kind<ListKind.Witness, A> {}

    @Test
    void unwrap_shouldThrowClassCastExceptionForUnknownKindType() {
      // If the Kind passed is not a ListView (or whatever ListKind.narrow expects),
      // ClassCastException can occur in narrow.
      // If ListKind.narrow checks type, then KindUnwrapException might be thrown by unwrap itself.
      // The current test setup relies on ClassCastException from ListKind.narrow if not ListKind
      Kind<ListKind.Witness, String> unknownKind = new DummyListKind<>();
      assertThatThrownBy(() -> ListKindHelper.unwrap(unknownKind))
          .isInstanceOf(ClassCastException.class) // This comes from ListKind.narrow
          .hasMessageContaining("DummyListKind cannot be cast");
    }
  }

  @Nested
  @DisplayName("unwrapOr()")
  class UnwrapOrTests {
    @Test
    void unwrapOr_shouldReturnValueWhenPresent() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = ListKindHelper.wrap(list);
      List<String> defaultList = Collections.singletonList("default");
      assertThat(ListKindHelper.unwrapOr(kind, defaultList)).isEqualTo(list);
    }

    @Test
    void unwrapOr_shouldReturnDefaultWhenKindIsNull() {
      List<String> defaultList = Collections.singletonList("default");
      assertThat(ListKindHelper.unwrapOr(null, defaultList)).isSameAs(defaultList);
    }

    @Test
    void unwrapOr_shouldThrowNPEWhenDefaultIsNull() {
      List<String> list = Arrays.asList("a", "b");
      Kind<ListKind.Witness, String> kind = ListKindHelper.wrap(list);
      assertThatThrownBy(() -> ListKindHelper.unwrapOr(kind, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("defaultValue cannot be null");
    }
  }

  @Nested
  @DisplayName("Private Constructor")
  class PrivateConstructorTest {

    @Test
    @DisplayName("should throw UnsupportedOperationException when invoked via reflection")
    void constructor_shouldThrowException() throws NoSuchMethodException {
      Constructor<ListKindHelper> constructor = ListKindHelper.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      assertThatThrownBy(constructor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .cause()
          .hasMessageContaining("This is a utility class and cannot be instantiated");
    }
  }
}
