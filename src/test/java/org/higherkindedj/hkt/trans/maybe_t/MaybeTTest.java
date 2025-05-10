package org.higherkindedj.hkt.trans.maybe_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.jspecify.annotations.NonNull; // Ensure this is imported
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeT Class Tests (Outer: OptionalKind.Witness)")
class MaybeTTest {

  private Monad<OptionalKind.Witness> outerMonad;

  private final String presentValue = "Success";
  private final Integer otherPresentValue = 123;

  private Kind<OptionalKind.Witness, Maybe<String>> wrappedJust;
  private Kind<OptionalKind.Witness, Maybe<String>> wrappedNothing;
  private Kind<OptionalKind.Witness, Maybe<String>> wrappedOuterEmpty; // Represents F<empty>
  private Kind<OptionalKind.Witness, Integer> wrappedOuterValue; // Represents F<A> for liftF

  private Maybe<String> plainJust;
  private Maybe<String> plainNothing;

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad();

    wrappedJust = OptionalKindHelper.wrap(Optional.of(Maybe.just(presentValue)));
    wrappedNothing = OptionalKindHelper.wrap(Optional.of(Maybe.nothing()));
    wrappedOuterEmpty = OptionalKindHelper.wrap(Optional.empty());
    wrappedOuterValue = OptionalKindHelper.wrap(Optional.of(otherPresentValue));

    plainJust = Maybe.just(presentValue);
    plainNothing = Maybe.nothing();
  }

  private <A> Optional<Maybe<A>> unwrapT(MaybeT<OptionalKind.Witness, A> maybeT) {
    Kind<OptionalKind.Witness, Maybe<A>> outerKind = maybeT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Maybe<A>>")
    void fromKind_wrapsExisting() {
      MaybeT<OptionalKind.Witness, String> mtJust = MaybeT.fromKind(wrappedJust);
      MaybeT<OptionalKind.Witness, String> mtNothing = MaybeT.fromKind(wrappedNothing);
      MaybeT<OptionalKind.Witness, String> mtOuterEmpty = MaybeT.fromKind(wrappedOuterEmpty);

      assertThat(unwrapT(mtJust)).isPresent().contains(Maybe.just(presentValue));
      assertThat(unwrapT(mtNothing)).isPresent().contains(Maybe.nothing());
      assertThat(unwrapT(mtOuterEmpty)).isEmpty();

      assertThat(mtJust.value()).isSameAs(wrappedJust);
      assertThat(mtNothing.value()).isSameAs(wrappedNothing);
      assertThat(mtOuterEmpty.value()).isSameAs(wrappedOuterEmpty);
    }

    @Test
    @DisplayName("fromKind should throw NullPointerException for null input")
    void fromKind_throwsOnNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> MaybeT.fromKind(null))
          .withMessageContaining("Wrapped value cannot be null for MaybeT");
    }

    @Test
    @DisplayName("just should lift non-null value to MaybeT(F<Maybe.Just(A)>)")
    void just_liftsNonNullValue() {
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.just(outerMonad, presentValue);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(presentValue));
    }

    @Test
    @DisplayName("just should throw NullPointerException for null input value")
    void just_throwsOnNullValue() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  MaybeT.just(
                      outerMonad, (@NonNull String) null)) // Explicit cast to satisfy @NonNull
          .withMessageContaining("Value for Just cannot be null");
    }

    @Test
    @DisplayName("nothing should lift empty state to MaybeT(F<Maybe.Nothing()>)")
    void nothing_liftsEmpty() {
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
    @DisplayName("liftF should lift Kind<F, A> to MaybeT(F<Maybe.Just(A)>)")
    void liftF_liftsOuterKind() {
      MaybeT<OptionalKind.Witness, Integer> mt = MaybeT.liftF(outerMonad, wrappedOuterValue);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(otherPresentValue));
    }

    @Test
    @DisplayName(
        "liftF should handle empty outer Kind as F<Maybe.Nothing()> if F map(empty) -> F<empty>")
    void liftF_handlesEmptyOuterKind() {
      Kind<OptionalKind.Witness, Integer> outerEmpty = OptionalKindHelper.wrap(Optional.empty());
      MaybeT<OptionalKind.Witness, Integer> mt = MaybeT.liftF(outerMonad, outerEmpty);
      // liftF does outerMonad.map(Maybe::just, fa)
      // if fa is Optional.empty(), map does nothing, so result is Optional.empty()
      assertThat(unwrapT(mt)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("value() should return the wrapped Kind")
    void value_returnsWrapped() {
      MaybeT<OptionalKind.Witness, String> mt = MaybeT.fromKind(wrappedJust);
      assertThat(mt.value()).isSameAs(wrappedJust);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust1 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("A")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust2 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("A")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedJust3 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("B")));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedNothing1 =
        OptionalKindHelper.wrap(Optional.of(Maybe.nothing()));
    Kind<OptionalKind.Witness, Maybe<String>> wrappedOuterEmpty1 =
        OptionalKindHelper.wrap(Optional.empty());

    MaybeT<OptionalKind.Witness, String> mt1 = MaybeT.fromKind(wrappedJust1);
    MaybeT<OptionalKind.Witness, String> mt2 = MaybeT.fromKind(wrappedJust2);
    MaybeT<OptionalKind.Witness, String> mt3 = MaybeT.fromKind(wrappedJust3);
    MaybeT<OptionalKind.Witness, String> mtNothingLocal = MaybeT.fromKind(wrappedNothing1);
    MaybeT<OptionalKind.Witness, String> mtOuterEmptyLocal = MaybeT.fromKind(wrappedOuterEmpty1);

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      assertThat(mt1).isEqualTo(mt2);
      assertThat(mt1).isNotEqualTo(mt3);
      assertThat(mt1).isNotEqualTo(mtNothingLocal);
      assertThat(mt1).isNotEqualTo(mtOuterEmptyLocal);
      assertThat(mt1).isNotEqualTo(null);
      assertThat(mt1).isNotEqualTo(wrappedJust1);
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

      assertThat(mtNothingLocal.toString()).startsWith("MaybeT[value=").endsWith("]");
      assertThat(mtNothingLocal.toString()).contains("Optional[Nothing]");

      assertThat(mtOuterEmptyLocal.toString()).startsWith("MaybeT[value=").endsWith("]");
      assertThat(mtOuterEmptyLocal.toString()).contains("Optional.empty");
    }
  }
}
