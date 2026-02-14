// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link Tuple2KindHelper}. */
@DisplayName("Tuple2KindHelper Test Suite")
class Tuple2KindHelperTest {

  @Nested
  @DisplayName("widen2() - Convert Tuple2 to Kind2")
  class Widen2Tests {

    @Test
    @DisplayName("widen2() wraps a Tuple2 into Kind2")
    void widen2WrapsTuple() {
      Tuple2<String, Integer> tuple = new Tuple2<>("hello", 42);

      Kind2<Tuple2Kind2.Witness, String, Integer> kind = TUPLE2.widen2(tuple);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(Tuple2KindHelper.Tuple2Kind2Holder.class);
    }

    @Test
    @DisplayName("widen2() preserves tuple data")
    void widen2PreservesData() {
      Tuple2<String, Integer> original = new Tuple2<>("hello", 42);

      Kind2<Tuple2Kind2.Witness, String, Integer> kind = TUPLE2.widen2(original);
      Tuple2<String, Integer> unwrapped = TUPLE2.narrow2(kind);

      assertThat(unwrapped).isEqualTo(original);
    }

    @Test
    @DisplayName("widen2() throws NullPointerException when tuple is null")
    void widen2ThrowsWhenTupleNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> TUPLE2.widen2(null))
          .withMessageContaining("Tuple2")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("widen2() works with null elements inside tuple")
    void widen2WorksWithNullElements() {
      Tuple2<String, Integer> tuple = new Tuple2<>(null, null);

      Kind2<Tuple2Kind2.Witness, String, Integer> kind = TUPLE2.widen2(tuple);

      assertThat(kind).isNotNull();
      Tuple2<String, Integer> unwrapped = TUPLE2.narrow2(kind);
      assertThat(unwrapped._1()).isNull();
      assertThat(unwrapped._2()).isNull();
    }
  }

  @Nested
  @DisplayName("narrow2() - Convert Kind2 back to Tuple2")
  class Narrow2Tests {

    @Test
    @DisplayName("narrow2() unwraps Kind2 to Tuple2")
    void narrow2UnwrapsKind() {
      Tuple2<String, Integer> original = new Tuple2<>("hello", 42);
      Kind2<Tuple2Kind2.Witness, String, Integer> kind = TUPLE2.widen2(original);

      Tuple2<String, Integer> result = TUPLE2.narrow2(kind);

      assertThat(result).isEqualTo(original);
      assertThat(result._1()).isEqualTo("hello");
      assertThat(result._2()).isEqualTo(42);
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException when Kind2 is null")
    void narrow2ThrowsWhenKindNull() {
      assertThatThrownBy(() -> TUPLE2.narrow2(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind2 for Tuple2");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException for wrong Kind2 type")
    void narrow2ThrowsWhenWrongKindType() {
      // Create a Kind2 that is NOT a Tuple2Kind2Holder
      Kind2<Tuple2Kind2.Witness, String, Integer> wrongKind =
          new Kind2<Tuple2Kind2.Witness, String, Integer>() {};

      assertThatThrownBy(() -> TUPLE2.narrow2(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind2 instance cannot be narrowed to Tuple2")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow2() round-trips correctly")
    void narrow2RoundTrips() {
      Tuple2<String, Integer> original = new Tuple2<>("test", 123);

      Tuple2<String, Integer> result = TUPLE2.narrow2(TUPLE2.widen2(original));

      assertThat(result).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("Tuple2Kind2Holder - Internal Implementation")
  class Tuple2Kind2HolderTests {

    @Test
    @DisplayName("Tuple2Kind2Holder stores tuple correctly")
    void holderStoresTuple() {
      Tuple2<String, Integer> tuple = new Tuple2<>("hello", 42);

      Tuple2KindHelper.Tuple2Kind2Holder<String, Integer> holder =
          new Tuple2KindHelper.Tuple2Kind2Holder<>(tuple);

      assertThat(holder.tuple()).isEqualTo(tuple);
    }

    @Test
    @DisplayName("Tuple2Kind2Holder validates non-null tuple")
    void holderValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Tuple2KindHelper.Tuple2Kind2Holder<String, Integer>(null))
          .withMessageContaining("Tuple2")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("Tuple2Kind2Holder implements Tuple2Kind2 interface")
    void holderImplementsInterface() {
      Tuple2<String, Integer> tuple = new Tuple2<>("hello", 42);
      Tuple2KindHelper.Tuple2Kind2Holder<String, Integer> holder =
          new Tuple2KindHelper.Tuple2Kind2Holder<>(tuple);

      assertThat(holder).isInstanceOf(Tuple2Kind2.class);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Integration")
  class EdgeCasesAndIntegration {

    @Test
    @DisplayName("Multiple widen2 calls create independent instances")
    void multipleWiden2CallsCreateIndependentInstances() {
      Tuple2<String, Integer> tuple1 = new Tuple2<>("first", 1);
      Tuple2<String, Integer> tuple2 = new Tuple2<>("second", 2);

      Kind2<Tuple2Kind2.Witness, String, Integer> kind1 = TUPLE2.widen2(tuple1);
      Kind2<Tuple2Kind2.Witness, String, Integer> kind2 = TUPLE2.widen2(tuple2);

      assertThat(kind1).isNotSameAs(kind2);
      assertThat(TUPLE2.narrow2(kind1)).isEqualTo(tuple1);
      assertThat(TUPLE2.narrow2(kind2)).isEqualTo(tuple2);
    }

    @Test
    @DisplayName("Works with different type parameters")
    void worksWithDifferentTypes() {
      Tuple2<Boolean, Double> tuple = new Tuple2<>(true, 3.14);

      Kind2<Tuple2Kind2.Witness, Boolean, Double> kind = TUPLE2.widen2(tuple);
      Tuple2<Boolean, Double> result = TUPLE2.narrow2(kind);

      assertThat(result._1()).isTrue();
      assertThat(result._2()).isEqualTo(3.14);
    }

    @Test
    @DisplayName("Singleton enum instance is accessible")
    void singletonEnumInstanceAccessible() {
      assertThat(TUPLE2).isNotNull();
      assertThat(TUPLE2).isSameAs(Tuple2KindHelper.TUPLE2);
    }

    @Test
    @DisplayName("Enum has only one instance")
    void enumHasOnlyOneInstance() {
      Tuple2KindHelper[] values = Tuple2KindHelper.values();
      assertThat(values).hasSize(1);
      assertThat(values[0]).isSameAs(TUPLE2);
    }
  }
}
