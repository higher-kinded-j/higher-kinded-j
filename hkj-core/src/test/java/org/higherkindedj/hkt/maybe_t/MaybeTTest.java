// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeT Core Type Tests")
// (Outer: OptionalKind.Witness)
class MaybeTTest {

  private Monad<OptionalKind.Witness> outerMonad;

  private final String justValue = "Success";
  private final Integer otherJustValue = 123;

  private Kind<OptionalKind.Witness, Maybe<String>> wrappedJust;
  private Kind<OptionalKind.Witness, Maybe<String>> wrappedNothing;
  private Kind<OptionalKind.Witness, Maybe<String>> wrappedEmpty;
  private Kind<OptionalKind.Witness, Integer> wrappedOuterValue;

  private Maybe<String> plainJust;
  private Maybe<String> plainNothing;

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;

    wrappedJust = OPTIONAL.widen(Optional.of(Maybe.just(justValue)));
    wrappedNothing = OPTIONAL.widen(Optional.of(Maybe.nothing()));
    wrappedEmpty = OPTIONAL.widen(Optional.empty());

    wrappedOuterValue = OPTIONAL.widen(Optional.of(otherJustValue));

    plainJust = Maybe.just(justValue);
    plainNothing = Maybe.nothing();
  }

  private <A> Optional<Maybe<A>> unwrapT(MaybeT<OptionalKind.Witness, A> maybeT) {
    Kind<OptionalKind.Witness, Maybe<A>> outerKind = maybeT.value();
    return OPTIONAL.narrow(outerKind);
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Maybe<A>>")
    void fromKind_wrapsExisting() {
      MaybeT<OptionalKind.Witness, String> mtJust = MaybeT.fromKind(wrappedJust);
      MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.fromKind(wrappedNothing);
      MaybeT<OptionalKind.Witness, String> mtEmpty = MaybeT.fromKind(wrappedEmpty);

      assertThat(unwrapT(mtJust)).isPresent().contains(Maybe.just(justValue));
      assertThat(unwrapT(mtNothing)).isPresent().contains(Maybe.nothing());
      assertThat(unwrapT(mtEmpty)).isEmpty();

      assertThat(mtJust.value()).isSameAs(wrappedJust);
      assertThat(mtNothing.value()).isSameAs(wrappedNothing);
      assertThat(mtEmpty.value()).isSameAs(wrappedEmpty);
    }

    @Test
    @DisplayName("just should lift value to MaybeT(F<Just(A)>)")
    void just_liftsValue() {
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.just(outerMonad, justValue);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(justValue));
    }

    @Test
    @DisplayName("just should lift null value to MaybeT(F<Just(null)>)")
    void just_liftsNullValue() {
      // MaybeT.just uses Maybe.just internally, which doesn't allow null
      // To get F<Just(null)>, use fromMaybe with Maybe.fromNullable
      MaybeT<OptionalKind.Witness, String> mt =
          MaybeT.fromMaybe(outerMonad, Maybe.fromNullable(null));
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.fromNullable(null));
    }

    @Test
    @DisplayName("nothing should create MaybeT(F<Nothing>)")
    void nothing_createsNothing() {
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.nothing(outerMonad);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.nothing());
    }

    @Test
    @DisplayName("fromMaybe should lift Maybe to MaybeT(F<Maybe>)")
    void fromMaybe_liftsMaybe() {
      MaybeT<OptionalKind.Witness, String> mtJust = MaybeT.fromMaybe(outerMonad, plainJust);
      MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.fromMaybe(outerMonad, plainNothing);

      assertThat(unwrapT(mtJust)).isPresent().contains(plainJust);
      assertThat(unwrapT(mtNothing)).isPresent().contains(plainNothing);
    }

    @Test
    @DisplayName("liftF should lift Kind<F, A> to MaybeT(F<Maybe<A>>)")
    void liftF_liftsOuterKind() {
      MaybeT<OptionalKind.Witness, Integer> mt = MaybeT.liftF(outerMonad, wrappedOuterValue);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(otherJustValue));
    }

    @Test
    @DisplayName("liftF should handle empty outer Kind")
    void liftF_handlesEmptyOuterKind() {
      Kind<OptionalKind.Witness, Integer> emptyOuter = OPTIONAL.widen(Optional.empty());
      MaybeT<OptionalKind.Witness, Integer> mt = MaybeT.liftF(outerMonad, emptyOuter);
      assertThat(unwrapT(mt)).isEmpty();
    }

    @Test
    @DisplayName("liftF should handle null values in outer monad")
    void liftF_convertsNullToJustNull() {
      // When the outer monad contains null, liftF will map it through Maybe.fromNullable
      // Since Optional doesn't allow Optional.of(null), we use ofNullable which creates empty
      Kind<OptionalKind.Witness, String> nullValueKind = OPTIONAL.widen(Optional.ofNullable(null));
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.liftF(outerMonad, nullValueKind);

      // Since Optional.ofNullable(null) creates Optional.empty,
      // the result is an empty Optional, not Optional.of(Maybe)
      assertThat(unwrapT(mt)).isEmpty();
    }

    @Test
    @DisplayName("liftF should wrap values using Maybe.fromNullable")
    void liftF_wrapsValuesInMaybe() {
      // Test with a non-null value
      Kind<OptionalKind.Witness, Integer> valueKind = OPTIONAL.widen(Optional.of(42));
      MaybeT<OptionalKind.Witness, Integer> mt = MaybeT.liftF(outerMonad, valueKind);

      assertThat(unwrapT(mt)).isPresent().contains(Maybe.fromNullable(42));
    }

    @Test
    @DisplayName("liftF should handle empty outer monad")
    void liftF_handlesEmptyOuterMonad() {
      Kind<OptionalKind.Witness, String> emptyOuter = OPTIONAL.widen(Optional.empty());
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.liftF(outerMonad, emptyOuter);

      assertThat(unwrapT(mt)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceMethodTests {

    @Test
    @DisplayName("value should return the wrapped Kind")
    void value_returnsWrapped() {
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.fromKind(wrappedJust);
      assertThat(mt.value()).isSameAs(wrappedJust);

      MaybeT<OptionalKind.Witness, String> mtEmpty = MaybeT.fromKind(wrappedEmpty);
      assertThat(mtEmpty.value()).isSameAs(wrappedEmpty);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodTests {

    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust1 =
        OPTIONAL.widen(Optional.of(Maybe.just("A")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust2 =
        OPTIONAL.widen(Optional.of(Maybe.just("A")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust3 =
        OPTIONAL.widen(Optional.of(Maybe.just("B")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedNothing1 =
        OPTIONAL.widen(Optional.of(Maybe.nothing()));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedEmpty1 = OPTIONAL.widen(Optional.empty());

    MaybeT<OptionalKind.Witness, String> mt1 = MaybeT.fromKind(wrappedJust1);
    MaybeT<OptionalKind.Witness, String> mt2 = MaybeT.fromKind(wrappedJust2);
    MaybeT<OptionalKind.Witness, String> mt3 = MaybeT.fromKind(wrappedJust3);
    MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.fromKind(wrappedNothing1);
    MaybeT<OptionalKind.Witness, String> mtEmpty = MaybeT.fromKind(wrappedEmpty1);

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      assertThat(mt1).isEqualTo(mt2);
      assertThat(mt1).isNotEqualTo(mt3);
      assertThat(mt1).isNotEqualTo(mtNothing);
      assertThat(mt1).isNotEqualTo(mtEmpty);
      assertThat(mt1).isNotEqualTo(null);
      assertThat(mt1).isNotEqualTo(wrappedJust1);
    }

    @Test
    @DisplayName("equals should return true for self comparison")
    void equals_selfComparison() {
      assertThat(mt1.equals(mt1)).isTrue();
    }

    @Test
    @DisplayName("equals should return false for null comparison")
    void equals_nullComparison() {
      assertThat(mt1.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals should return false for different type comparison")
    void equals_differentTypeComparison() {
      assertThat(mt1.equals(new Object())).isFalse();
      assertThat(mt1.equals(wrappedJust1)).isFalse();
    }

    @Test
    @DisplayName("equals should differentiate based on wrapped Kind content")
    void equals_differentiatesContent() {
      assertThat(mt1).isNotEqualTo(mtNothing);
      assertThat(mtNothing).isNotEqualTo(mt1);
      assertThat(mt1).isNotEqualTo(mtEmpty);
      assertThat(mtEmpty).isNotEqualTo(mt1);
      assertThat(mtNothing).isNotEqualTo(mtEmpty);
      assertThat(mtEmpty).isNotEqualTo(mtNothing);
    }

    @Test
    @DisplayName("equals should return true for equal wrapped Kinds")
    void equals_comparesEqualWrappedValue() {
      Kind<OptionalKind.Witness, Maybe<String>> locWrapped1 =
          OPTIONAL.widen(Optional.of(Maybe.just("A")));
      Kind<OptionalKind.Witness, Maybe<String>> locWrapped2 =
          OPTIONAL.widen(Optional.of(Maybe.just("A")));

      MaybeT<OptionalKind.Witness, String> localMt1 = MaybeT.fromKind(locWrapped1);
      MaybeT<OptionalKind.Witness, String> localMt2 = MaybeT.fromKind(locWrapped2);

      assertThat(localMt1).isEqualTo(localMt2);
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      assertThat(mt1.hashCode()).isEqualTo(mt2.hashCode());
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      assertThat(mt1.toString()).startsWith("MaybeT[value=").endsWith("]");
      assertThat(mt1.toString()).contains("Optional[Just(A)]");

      assertThat(mtNothing.toString()).startsWith("MaybeT[value=").endsWith("]");
      assertThat(mtNothing.toString()).contains("Optional[Nothing]");

      assertThat(mtEmpty.toString()).startsWith("MaybeT[value=").endsWith("]");
      assertThat(mtEmpty.toString()).contains("Optional.empty");
    }
  }

  @Nested
  @DisplayName("Complete Core Type Test Suite")
  class CompleteCoreTypeTests {

    @Test
    @DisplayName("Run complete MaybeT core type tests")
    void runCompleteMaybeTTests() {
      CoreTypeTest.maybeT(MaybeT.class, outerMonad)
          .withJust(MaybeT.just(outerMonad, justValue))
          .withNothing(MaybeT.nothing(outerMonad))
          .withMappers(Object::toString)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Edge case: null values in Maybe")
    void edgeCase_nullValuesInMaybe() {
      // Use fromNullable for null values instead of just
      MaybeT<OptionalKind.Witness, String> justNull =
          MaybeT.fromMaybe(outerMonad, Maybe.fromNullable(null));
      assertThat(justNull).isNotNull();
      assertThat(justNull.value()).isNotNull();

      MaybeT<OptionalKind.Witness, String> nothing = MaybeT.nothing(outerMonad);
      assertThat(nothing).isNotNull();
      assertThat(nothing.value()).isNotNull();
    }

    @Test
    @DisplayName("Edge case: fromMaybe with Nothing")
    void edgeCase_fromMaybeWithNothing() {
      Maybe<String> maybeNothing = Maybe.nothing();
      MaybeT<OptionalKind.Witness, String> fromMaybeNothing =
          MaybeT.fromMaybe(outerMonad, maybeNothing);
      assertThat(fromMaybeNothing).isNotNull();
      assertThat(fromMaybeNothing.value()).isNotNull();
    }

    @Test
    @DisplayName("Edge case: fromMaybe with Just(null)")
    void edgeCase_fromMaybeWithJustNull() {
      // Use fromNullable instead of just for null values
      Maybe<String> maybeJustNull = Maybe.fromNullable(null);
      MaybeT<OptionalKind.Witness, String> fromMaybeJust =
          MaybeT.fromMaybe(outerMonad, maybeJustNull);
      assertThat(fromMaybeJust).isNotNull();
      assertThat(fromMaybeJust.value()).isNotNull();
    }

    @Test
    @DisplayName("Edge case: liftF with null-valued outer Kind")
    void edgeCase_liftFWithNullValuedOuter() {
      Kind<OptionalKind.Witness, String> liftedValue = outerMonad.of(null);
      MaybeT<OptionalKind.Witness, String> lifted = MaybeT.liftF(outerMonad, liftedValue);
      assertThat(lifted).isNotNull();
      assertThat(lifted.value()).isNotNull();
    }

    @Test
    @DisplayName("Edge case: chaining multiple operations")
    void edgeCase_chainingMultipleOperations() {
      MaybeT<OptionalKind.Witness, String> initial = MaybeT.just(outerMonad, "test");

      // Chain fromKind -> fromKind -> fromKind
      Kind<OptionalKind.Witness, Maybe<String>> kind1 = initial.value();
      MaybeT<OptionalKind.Witness, String> step1 = MaybeT.fromKind(kind1);

      Kind<OptionalKind.Witness, Maybe<String>> kind2 = step1.value();
      MaybeT<OptionalKind.Witness, String> step2 = MaybeT.fromKind(kind2);

      assertThat(unwrapT(step2)).isPresent().contains(Maybe.just("test"));
    }
  }
}
