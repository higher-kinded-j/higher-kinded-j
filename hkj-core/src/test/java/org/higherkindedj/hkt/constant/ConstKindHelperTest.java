// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link ConstKindHelper}. */
@DisplayName("ConstKindHelper Test Suite")
class ConstKindHelperTest {

  @Nested
  @DisplayName("widen2() - Convert Const to Kind2")
  class Widen2Tests {

    @Test
    @DisplayName("widen2() wraps a Const into Kind2")
    void widen2WrapsConst() {
      Const<String, Integer> const_ = new Const<>("hello");

      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(const_);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ConstKindHelper.ConstKind2Holder.class);
    }

    @Test
    @DisplayName("widen2() preserves const data")
    void widen2PreservesData() {
      Const<String, Integer> original = new Const<>("hello");

      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(original);
      Const<String, Integer> unwrapped = CONST.narrow2(kind);

      assertThat(unwrapped).isEqualTo(original);
    }

    @Test
    @DisplayName("widen2() throws NullPointerException when const is null")
    void widen2ThrowsWhenConstNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> CONST.widen2(null))
          .withMessageContaining("Const")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("widen2() works with null constant value")
    void widen2WorksWithNullConstantValue() {
      Const<String, Integer> const_ = new Const<>(null);

      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(const_);

      assertThat(kind).isNotNull();
      Const<String, Integer> unwrapped = CONST.narrow2(kind);
      assertThat(unwrapped.value()).isNull();
    }
  }

  @Nested
  @DisplayName("narrow2() - Convert Kind2 back to Const")
  class Narrow2Tests {

    @Test
    @DisplayName("narrow2() unwraps Kind2 to Const")
    void narrow2UnwrapsKind() {
      Const<String, Integer> original = new Const<>("hello");
      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(original);

      Const<String, Integer> result = CONST.narrow2(kind);

      assertThat(result).isEqualTo(original);
      assertThat(result.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException when Kind2 is null")
    void narrow2ThrowsWhenKindNull() {
      assertThatThrownBy(() -> CONST.narrow2(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind2 for Const");
    }

    @Test
    @DisplayName("narrow2() throws KindUnwrapException for wrong Kind2 type")
    void narrow2ThrowsWhenWrongKindType() {
      // Create a Kind2 that is NOT a ConstKind2Holder
      Kind2<ConstKind2.Witness, String, Integer> wrongKind =
          new Kind2<ConstKind2.Witness, String, Integer>() {};

      assertThatThrownBy(() -> CONST.narrow2(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind2 instance cannot be narrowed to Const")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow2() round-trips correctly")
    void narrow2RoundTrips() {
      Const<String, Integer> original = new Const<>("test");

      Const<String, Integer> result = CONST.narrow2(CONST.widen2(original));

      assertThat(result).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("ConstKind2Holder - Internal Implementation")
  class ConstKind2HolderTests {

    @Test
    @DisplayName("ConstKind2Holder stores const correctly")
    void holderStoresConst() {
      Const<String, Integer> const_ = new Const<>("hello");

      ConstKindHelper.ConstKind2Holder<String, Integer> holder =
          new ConstKindHelper.ConstKind2Holder<>(const_);

      assertThat(holder.const_()).isEqualTo(const_);
    }

    @Test
    @DisplayName("ConstKind2Holder validates non-null const")
    void holderValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ConstKindHelper.ConstKind2Holder<String, Integer>(null))
          .withMessageContaining("Const")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("ConstKind2Holder implements ConstKind2 interface")
    void holderImplementsInterface() {
      Const<String, Integer> const_ = new Const<>("hello");
      ConstKindHelper.ConstKind2Holder<String, Integer> holder =
          new ConstKindHelper.ConstKind2Holder<>(const_);

      assertThat(holder).isInstanceOf(ConstKind2.class);
    }
  }

  @Nested
  @DisplayName("widen() - Convert Const to Kind (Partially-Applied)")
  class WidenTests {

    @Test
    @DisplayName("widen() wraps a Const into Kind")
    void widenWrapsConst() {
      Const<Integer, String> const_ = new Const<>(42);

      Kind<ConstKind.Witness<Integer>, String> kind = CONST.widen(const_);

      assertThat(kind).isNotNull();
      assertThat(kind).isInstanceOf(ConstKindHelper.ConstKindHolder.class);
    }

    @Test
    @DisplayName("widen() preserves const data")
    void widenPreservesData() {
      Const<Integer, String> original = new Const<>(42);

      Kind<ConstKind.Witness<Integer>, String> kind = CONST.widen(original);
      Const<Integer, String> unwrapped = CONST.narrow(kind);

      assertThat(unwrapped).isEqualTo(original);
    }

    @Test
    @DisplayName("widen() throws NullPointerException when const is null")
    void widenThrowsWhenConstNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> CONST.widen(null))
          .withMessageContaining("Const")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("widen() works with null constant value")
    void widenWorksWithNullConstantValue() {
      Const<String, Integer> const_ = new Const<>(null);

      Kind<ConstKind.Witness<String>, Integer> kind = CONST.widen(const_);

      assertThat(kind).isNotNull();
      Const<String, Integer> unwrapped = CONST.narrow(kind);
      assertThat(unwrapped.value()).isNull();
    }
  }

  @Nested
  @DisplayName("narrow() - Convert Kind back to Const (Partially-Applied)")
  class NarrowTests {

    @Test
    @DisplayName("narrow() unwraps Kind to Const")
    void narrowUnwrapsKind() {
      Const<Integer, String> original = new Const<>(42);
      Kind<ConstKind.Witness<Integer>, String> kind = CONST.widen(original);

      Const<Integer, String> result = CONST.narrow(kind);

      assertThat(result).isEqualTo(original);
      assertThat(result.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("narrow() throws KindUnwrapException when Kind is null")
    void narrowThrowsWhenKindNull() {
      assertThatThrownBy(() -> CONST.narrow(null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Cannot narrow null Kind for Const");
    }

    @Test
    @DisplayName("narrow() throws KindUnwrapException for wrong Kind type")
    void narrowThrowsWhenWrongKindType() {
      // Create a Kind that is NOT a ConstKindHolder
      Kind<ConstKind.Witness<Integer>, String> wrongKind =
          new Kind<ConstKind.Witness<Integer>, String>() {};

      assertThatThrownBy(() -> CONST.narrow(wrongKind))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance cannot be narrowed to Const")
          .hasMessageContaining("received:");
    }

    @Test
    @DisplayName("narrow() round-trips correctly")
    void narrowRoundTrips() {
      Const<Integer, String> original = new Const<>(100);

      Const<Integer, String> result = CONST.narrow(CONST.widen(original));

      assertThat(result).isEqualTo(original);
    }
  }

  @Nested
  @DisplayName("ConstKindHolder - Internal Implementation")
  class ConstKindHolderTests {

    @Test
    @DisplayName("ConstKindHolder stores const correctly")
    void holderStoresConst() {
      Const<Integer, String> const_ = new Const<>(42);

      ConstKindHelper.ConstKindHolder<Integer, String> holder =
          new ConstKindHelper.ConstKindHolder<>(const_);

      assertThat(holder.const_()).isEqualTo(const_);
    }

    @Test
    @DisplayName("ConstKindHolder validates non-null const")
    void holderValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ConstKindHelper.ConstKindHolder<Integer, String>(null))
          .withMessageContaining("Const")
          .withMessageContaining("widen");
    }

    @Test
    @DisplayName("ConstKindHolder implements ConstKind interface")
    void holderImplementsInterface() {
      Const<Integer, String> const_ = new Const<>(42);
      ConstKindHelper.ConstKindHolder<Integer, String> holder =
          new ConstKindHelper.ConstKindHolder<>(const_);

      assertThat(holder).isInstanceOf(ConstKind.class);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Integration")
  class EdgeCasesAndIntegration {

    @Test
    @DisplayName("Multiple widen2 calls create independent instances")
    void multipleWiden2CallsCreateIndependentInstances() {
      Const<String, Integer> const1 = new Const<>("first");
      Const<String, Integer> const2 = new Const<>("second");

      Kind2<ConstKind2.Witness, String, Integer> kind1 = CONST.widen2(const1);
      Kind2<ConstKind2.Witness, String, Integer> kind2 = CONST.widen2(const2);

      assertThat(kind1).isNotSameAs(kind2);
      assertThat(CONST.narrow2(kind1)).isEqualTo(const1);
      assertThat(CONST.narrow2(kind2)).isEqualTo(const2);
    }

    @Test
    @DisplayName("Works with different type parameters")
    void worksWithDifferentTypes() {
      Const<Boolean, Double> const_ = new Const<>(true);

      Kind2<ConstKind2.Witness, Boolean, Double> kind = CONST.widen2(const_);
      Const<Boolean, Double> result = CONST.narrow2(kind);

      assertThat(result.value()).isTrue();
    }

    @Test
    @DisplayName("Singleton enum instance is accessible")
    void singletonEnumInstanceAccessible() {
      assertThat(CONST).isNotNull();
      assertThat(CONST).isSameAs(ConstKindHelper.CONST);
    }

    @Test
    @DisplayName("Enum has only one instance")
    void enumHasOnlyOneInstance() {
      ConstKindHelper[] values = ConstKindHelper.values();
      assertThat(values).hasSize(1);
      assertThat(values[0]).isSameAs(CONST);
    }

    @Test
    @DisplayName("Preserves phantom type parameter during round-trip")
    void preservesPhantomTypeParameter() {
      // Even though the phantom type (Integer) is not stored, the type signature is preserved
      Const<String, Integer> original = new Const<>("value");

      Kind2<ConstKind2.Witness, String, Integer> kind = CONST.widen2(original);
      // The Kind2 maintains the phantom Integer type in its signature
      Const<String, Integer> result = CONST.narrow2(kind);

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("Works with same types for constant and phantom")
    void worksWithSameTypes() {
      Const<String, String> const_ = new Const<>("constant");

      Kind2<ConstKind2.Witness, String, String> kind = CONST.widen2(const_);
      Const<String, String> result = CONST.narrow2(kind);

      assertThat(result.value()).isEqualTo("constant");
    }

    @Test
    @DisplayName("Multiple widen calls create independent instances")
    void multipleWidenCallsCreateIndependentInstances() {
      Const<Integer, String> const1 = new Const<>(1);
      Const<Integer, String> const2 = new Const<>(2);

      Kind<ConstKind.Witness<Integer>, String> kind1 = CONST.widen(const1);
      Kind<ConstKind.Witness<Integer>, String> kind2 = CONST.widen(const2);

      assertThat(kind1).isNotSameAs(kind2);
      assertThat(CONST.narrow(kind1)).isEqualTo(const1);
      assertThat(CONST.narrow(kind2)).isEqualTo(const2);
    }

    @Test
    @DisplayName("widen() and widen2() create different wrapper types")
    void widenAndWiden2CreateDifferentWrappers() {
      Const<Integer, String> const_ = new Const<>(42);

      Kind<ConstKind.Witness<Integer>, String> kind = CONST.widen(const_);
      Kind2<ConstKind2.Witness, Integer, String> kind2 = CONST.widen2(const_);

      assertThat(kind).isInstanceOf(ConstKindHelper.ConstKindHolder.class);
      assertThat(kind2).isInstanceOf(ConstKindHelper.ConstKind2Holder.class);
      assertThat(kind).isNotInstanceOf(ConstKindHelper.ConstKind2Holder.class);
      assertThat(kind2).isNotInstanceOf(ConstKindHelper.ConstKindHolder.class);
    }

    @Test
    @DisplayName("Preserves phantom type parameter during widen/narrow round-trip")
    void preservesPhantomTypeDuringPartiallyAppliedRoundTrip() {
      Const<Integer, String> original = new Const<>(100);

      Kind<ConstKind.Witness<Integer>, String> kind = CONST.widen(original);
      Const<Integer, String> result = CONST.narrow(kind);

      assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("Works with same types for constant and phantom in partially-applied form")
    void partiallyAppliedWorksWithSameTypes() {
      Const<String, String> const_ = new Const<>("value");

      Kind<ConstKind.Witness<String>, String> kind = CONST.widen(const_);
      Const<String, String> result = CONST.narrow(kind);

      assertThat(result.value()).isEqualTo("value");
    }
  }
}
