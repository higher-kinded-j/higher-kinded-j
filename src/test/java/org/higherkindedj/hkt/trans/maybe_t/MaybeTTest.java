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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeT Class Tests (Outer: Optional)")
class MaybeTTest {

  // Outer Monad (F = OptionalKind<?>)
  private Monad<OptionalKind<?>> outerMonad;

  // Test data
  private final String justValue = "Success";
  private final Integer otherJustValue = 123;

  // Pre-wrapped Kinds for testing
  private Kind<OptionalKind<?>, Maybe<String>> wrappedJust;
  private Kind<OptionalKind<?>, Maybe<String>> wrappedNothing;
  private Kind<OptionalKind<?>, Maybe<String>> wrappedOuterEmpty; // Outer Optional is empty
  private Kind<OptionalKind<?>, Integer> wrappedOuterValue;
  private Kind<OptionalKind<?>, Integer> wrappedOuterEmptyValue;

  // Plain Maybe for testing
  private Maybe<String> plainJust;
  private Maybe<String> plainNothing;

  @BeforeEach
  void setUp() {
    // Use OptionalMonad as the concrete Monad<F> needed for factory methods
    outerMonad = new OptionalMonad();

    // Prepare wrapped kinds for testing fromKind
    // These now wrap Kind<OptionalKind, Maybe<String>>
    wrappedJust = OptionalKindHelper.wrap(Optional.of(Maybe.just(justValue)));
    wrappedNothing = OptionalKindHelper.wrap(Optional.of(Maybe.nothing()));
    wrappedOuterEmpty = OptionalKindHelper.wrap(Optional.empty()); // Outer Optional is empty

    // Prepare outer value for liftF (Kind<OptionalKind, Integer>)
    wrappedOuterValue = OptionalKindHelper.wrap(Optional.of(otherJustValue));
    wrappedOuterEmptyValue =
        OptionalKindHelper.wrap(Optional.empty()); // Kind<OptionalKind, Integer>

    // Prepare plain Maybe for fromMaybe
    plainJust = Maybe.just(justValue);
    plainNothing = Maybe.nothing();
  }

  // Helper to unwrap MaybeT<OptionalKind<?>, A> -> Optional<Maybe<A>>
  private <A> Optional<Maybe<A>> unwrapT(MaybeT<OptionalKind<?>, A> maybeT) {
    Kind<OptionalKind<?>, Maybe<A>> outerKind = maybeT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Maybe<A>>")
    void fromKind_wrapsExisting() {
      MaybeT<OptionalKind<?>, String> mtJust = MaybeT.fromKind(wrappedJust);
      MaybeT<OptionalKind<?>, String> mtNothing = MaybeT.fromKind(wrappedNothing);
      MaybeT<OptionalKind<?>, String> mtOuterEmpty = MaybeT.fromKind(wrappedOuterEmpty);

      assertThat(unwrapT(mtJust)).isPresent().contains(Maybe.just(justValue));
      assertThat(unwrapT(mtNothing)).isPresent().contains(Maybe.nothing());
      assertThat(unwrapT(mtOuterEmpty)).isEmpty(); // Outer Optional was empty

      // Check value() returns the original wrapped kind
      assertThat(mtJust.value()).isSameAs(wrappedJust);
      assertThat(mtNothing.value()).isSameAs(wrappedNothing);
      assertThat(mtOuterEmpty.value()).isSameAs(wrappedOuterEmpty);
    }

    @Test
    @DisplayName("fromKind should throw NullPointerException for null input")
    void fromKind_throwsOnNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> MaybeT.fromKind(null))
          .withMessageContaining("Wrapped value cannot be null");
    }

    @Test
    @DisplayName("just should lift non-null value to MaybeT(F<Just(A)>)")
    void just_liftsNonNullValue() {
      MaybeT<OptionalKind<?>, String> mt = MaybeT.just(outerMonad, justValue);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(justValue));
    }

    @Test
    @DisplayName("just should throw NullPointerException for null input value")
    void just_throwsOnNullValue() {
      // Maybe.just itself throws on null
      assertThatNullPointerException()
          .isThrownBy(() -> MaybeT.just(outerMonad, (String) null))
          .withMessageContaining("Value for Just cannot be null");
    }

    @Test
    @DisplayName("nothing should lift Nothing state to MaybeT(F<Nothing>)")
    void nothing_liftsNothing() {
      // Specify type parameter A explicitly
      MaybeT<OptionalKind<?>, Integer> mt = MaybeT.nothing(outerMonad);
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.nothing());
    }

    @Test
    @DisplayName("fromMaybe should lift Maybe to MaybeT(F<Maybe>)")
    void fromMaybe_liftsMaybe() {
      MaybeT<OptionalKind<?>, String> mtJust = MaybeT.fromMaybe(outerMonad, plainJust);
      MaybeT<OptionalKind<?>, String> mtNothing = MaybeT.fromMaybe(outerMonad, plainNothing);

      assertThat(unwrapT(mtJust)).isPresent().contains(plainJust);
      assertThat(unwrapT(mtNothing)).isPresent().contains(plainNothing);
    }

    @Test
    @DisplayName("fromMaybe should throw NullPointerException for null Maybe input")
    void fromMaybe_throwsOnNullMaybe() {
      assertThatNullPointerException()
          .isThrownBy(() -> MaybeT.fromMaybe(outerMonad, null))
          .withMessageContaining("Input Maybe cannot be null");
    }

    @Test
    @DisplayName("liftF should lift Kind<F, A> to MaybeT(F<Maybe.fromNullable(A)>)")
    void liftF_liftsOuterKind() {
      // Lift a present outer value (Kind<OptionalKind, Integer>)
      MaybeT<OptionalKind<?>, Integer> mt = MaybeT.liftF(outerMonad, wrappedOuterValue);
      // Result should be Optional<Maybe<Integer>> containing Optional.of(Maybe.just(123))
      assertThat(unwrapT(mt)).isPresent().contains(Maybe.just(otherJustValue));

      // Lift an empty outer value (Kind<OptionalKind, Integer>)
      MaybeT<OptionalKind<?>, Integer> mtOuterEmpty =
          MaybeT.liftF(outerMonad, wrappedOuterEmptyValue);
      // liftF uses outerMonad.map, which for Optional propagates empty
      // Result should be Optional.empty()
      assertThat(unwrapT(mtOuterEmpty)).isEmpty();

      // Lift an outer value containing null (Kind<OptionalKind, String>)
      Kind<OptionalKind<?>, String> wrappedOuterNull =
          OptionalKindHelper.wrap(Optional.ofNullable(null));
      MaybeT<OptionalKind<?>, String> mtOuterNull = MaybeT.liftF(outerMonad, wrappedOuterNull);
      // map(Maybe::fromNullable, Optional.of(null)) -> Optional.of(Maybe.nothing())
      assertThat(unwrapT(mtOuterEmpty).isPresent()).isFalse();
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("value() should return the wrapped Kind")
    void value_returnsWrapped() {
      MaybeT<OptionalKind<?>, String> mt = MaybeT.fromKind(wrappedJust);
      assertThat(mt.value()).isSameAs(wrappedJust);

      MaybeT<OptionalKind<?>, String> mtEmptyOuter = MaybeT.fromKind(wrappedOuterEmpty);
      assertThat(mtEmptyOuter.value()).isSameAs(wrappedOuterEmpty);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    // Create distinct wrapped Kinds with equal/different content for comparison
    // These need to be Kind<F, Maybe<A>>
    Kind<OptionalKind<?>, Maybe<String>> wrappedJustOpt1 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("A")));
    Kind<OptionalKind<?>, Maybe<String>> wrappedJustOpt2 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("A"))); // Equal content
    Kind<OptionalKind<?>, Maybe<String>> wrappedJustOpt3 =
        OptionalKindHelper.wrap(Optional.of(Maybe.just("B"))); // Different content
    Kind<OptionalKind<?>, Maybe<String>> wrappedNothingOpt1 =
        OptionalKindHelper.wrap(Optional.of(Maybe.nothing())); // Inner nothing
    Kind<OptionalKind<?>, Maybe<String>> wrappedNothingOpt2 =
        OptionalKindHelper.wrap(Optional.of(Maybe.nothing())); // Inner nothing
    Kind<OptionalKind<?>, Maybe<String>> wrappedOuterEmpty1 =
        OptionalKindHelper.wrap(Optional.empty()); // Outer empty

    MaybeT<OptionalKind<?>, String> mt1 = MaybeT.fromKind(wrappedJustOpt1);
    MaybeT<OptionalKind<?>, String> mt2 = MaybeT.fromKind(wrappedJustOpt2);
    MaybeT<OptionalKind<?>, String> mt3 = MaybeT.fromKind(wrappedJustOpt3);
    MaybeT<OptionalKind<?>, String> mtNothingInner1 = MaybeT.fromKind(wrappedNothingOpt1);
    MaybeT<OptionalKind<?>, String> mtNothingInner2 = MaybeT.fromKind(wrappedNothingOpt2);
    MaybeT<OptionalKind<?>, String> mtOuterEmpty = MaybeT.fromKind(wrappedOuterEmpty1);

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      assertThat(mt1).isEqualTo(mt2); // Equal content
      assertThat(mtNothingInner1).isEqualTo(mtNothingInner2); // Equal empty inner content

      assertThat(mt1).isNotEqualTo(mt3); // Different content
      assertThat(mt1).isNotEqualTo(mtNothingInner1); // Different inner presence
      assertThat(mt1).isNotEqualTo(mtOuterEmpty); // Different outer presence
      assertThat(mt1).isNotEqualTo(null);
      assertThat(mt1).isNotEqualTo(wrappedJustOpt1); // Different type
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
      assertThat(mt1.equals(wrappedJustOpt1)).isFalse();
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      assertThat(mt1.hashCode()).isEqualTo(mt2.hashCode());
      assertThat(mtNothingInner1.hashCode()).isEqualTo(mtNothingInner2.hashCode());

      // It's not guaranteed that non-equal objects have different hash codes,
      // but we check consistency with equals.
      assertThat(mt1.equals(mt3)).isFalse();
      assertThat(mt1.equals(mtNothingInner1)).isFalse();
      assertThat(mt1.equals(mtOuterEmpty)).isFalse();
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      // The exact string depends on the toString of the outer Kind and inner Maybe
      assertThat(mt1.toString()).startsWith("MaybeT[").endsWith("]");
      assertThat(mt1.toString()).contains("Optional[Just(A)]"); // Content of wrappedJustOpt1

      assertThat(mtNothingInner1.toString()).startsWith("MaybeT[").endsWith("]");
      assertThat(mtNothingInner1.toString()).contains("Optional[Nothing]");

      assertThat(mtOuterEmpty.toString()).startsWith("MaybeT[").endsWith("]");
      assertThat(mtOuterEmpty.toString()).contains("Optional.empty"); // Outer empty
    }
  }
}
