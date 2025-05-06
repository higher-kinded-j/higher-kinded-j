package org.higherkindedj.hkt.trans.optional_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalT Class Tests (Outer: Optional)")
class OptionalTTest {

  // Outer Monad (F = OptionalKind<?>)
  private Monad<OptionalKind<?>> outerMonad;

  // Test data
  private final String presentValue = "Success";
  private final Integer otherPresentValue = 123;

  private Kind<OptionalKind<?>, String> wrappedEmpty;
  private Kind<OptionalKind<?>, Integer> wrappedOuterValue;
  private Kind<OptionalKind<?>, Integer> wrappedOuterEmpty;

  private Optional<String> plainPresent;
  private Optional<String> plainEmpty;

  @BeforeEach
  void setUp() {
    // Use OptionalMonad as the concrete Monad<F> needed for factory methods
    outerMonad = new OptionalMonad();

    // Prepare wrapped kinds for testing fromKind
    Kind<OptionalKind<?>, String> wrappedPresent =
        OptionalKindHelper.wrap(Optional.of(presentValue));
    wrappedEmpty = OptionalKindHelper.wrap(Optional.empty()); // This Kind holds String type arg

    // Prepare outer value for liftF
    wrappedOuterValue = OptionalKindHelper.wrap(Optional.of(otherPresentValue));
    wrappedOuterEmpty =
        OptionalKindHelper.wrap(Optional.empty()); // This Kind holds Integer type arg

    // Prepare plain Optional for fromOptional
    plainPresent = Optional.of(presentValue);
    plainEmpty = Optional.empty();
  }

  // Helper to unwrap OptionalT<OptionalKind<?>, A> -> Optional<Optional<A>>
  // This remains correct as OptionalT wraps Kind<F, Optional<A>>
  private <A> Optional<Optional<A>> unwrapT(OptionalT<OptionalKind<?>, A> optionalT) {
    Kind<OptionalKind<?>, Optional<A>> outerKind = optionalT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Optional<A>>")
    void fromKind_wrapsExisting() {
      // Create the Kind<F, Optional<A>> structure needed by fromKind
      Kind<OptionalKind<?>, Optional<String>> kindWithPresentOptional =
          OptionalKindHelper.wrap(Optional.of(Optional.of(presentValue)));
      Kind<OptionalKind<?>, Optional<String>> kindWithEmptyOptional =
          OptionalKindHelper.wrap(Optional.of(Optional.empty()));
      Kind<OptionalKind<?>, Optional<String>> kindOuterEmpty =
          OptionalKindHelper.wrap(Optional.empty()); // Outer is empty

      OptionalT<OptionalKind<?>, String> otPresent = OptionalT.fromKind(kindWithPresentOptional);
      OptionalT<OptionalKind<?>, String> otEmptyInner = OptionalT.fromKind(kindWithEmptyOptional);
      OptionalT<OptionalKind<?>, String> otEmptyOuter = OptionalT.fromKind(kindOuterEmpty);

      assertThat(unwrapT(otPresent)).isPresent().contains(Optional.of(presentValue));
      assertThat(unwrapT(otEmptyInner)).isPresent().contains(Optional.empty());
      assertThat(unwrapT(otEmptyOuter)).isEmpty();

      // Check getValue returns the original wrapped kind
      assertThat(otPresent.value()).isSameAs(kindWithPresentOptional);
      assertThat(otEmptyInner.value()).isSameAs(kindWithEmptyOptional);
      assertThat(otEmptyOuter.value()).isSameAs(kindOuterEmpty);
    }

    @Test
    @DisplayName("fromKind should throw NullPointerException for null input")
    void fromKind_throwsOnNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> OptionalT.fromKind(null))
          .withMessageContaining("Wrapped value cannot be null");
    }

    @Test
    @DisplayName("some should lift non-null value to OptionalT(F<Optional.of(A)>)")
    void some_liftsNonNullValue() {
      OptionalT<OptionalKind<?>, String> ot = OptionalT.some(outerMonad, presentValue);
      assertThat(unwrapT(ot)).isPresent().contains(Optional.of(presentValue));
    }

    @Test
    @DisplayName("some should throw NullPointerException for null input value")
    void some_throwsOnNullValue() {
      assertThatNullPointerException().isThrownBy(() -> OptionalT.some(outerMonad, (String) null));
    }

    @Test
    @DisplayName("none should lift empty state to OptionalT(F<Optional.empty()>)")
    void none_liftsEmpty() {
      // Specify type parameter A explicitly
      OptionalT<OptionalKind<?>, Integer> ot = OptionalT.none(outerMonad);
      assertThat(unwrapT(ot)).isPresent().contains(Optional.empty());
    }

    @Test
    @DisplayName("fromOptional should lift Optional to OptionalT(F<Optional>)")
    void fromOptional_liftsOptional() {
      OptionalT<OptionalKind<?>, String> otPresent =
          OptionalT.fromOptional(outerMonad, plainPresent);
      OptionalT<OptionalKind<?>, String> otEmpty = OptionalT.fromOptional(outerMonad, plainEmpty);

      assertThat(unwrapT(otPresent)).isPresent().contains(plainPresent);
      assertThat(unwrapT(otEmpty)).isPresent().contains(plainEmpty);
    }

    @Test
    @DisplayName("fromOptional should throw NullPointerException for null Optional input")
    void fromOptional_throwsOnNullOptional() {
      assertThatNullPointerException()
          .isThrownBy(() -> OptionalT.fromOptional(outerMonad, null))
          .withMessageContaining("Input Optional cannot be null");
    }

    @Test
    @DisplayName("liftF should lift Kind<F, A> to OptionalT(F<Optional.ofNullable(A)>)")
    void liftF_liftsOuterKind() {
      // Lift a present outer value (Kind<OptionalKind, Integer>)
      OptionalT<OptionalKind<?>, Integer> ot = OptionalT.liftF(outerMonad, wrappedOuterValue);
      // Result should be Optional<Optional<Integer>> containing Optional.of(Optional.of(123))
      assertThat(unwrapT(ot)).isPresent().contains(Optional.of(otherPresentValue));

      // Lift an empty outer value (Kind<OptionalKind, Integer>)
      OptionalT<OptionalKind<?>, Integer> otEmptyOuter =
          OptionalT.liftF(outerMonad, wrappedOuterEmpty);
      // liftF uses outerMonad.map, which for Optional propagates empty
      // Result should be Optional.empty()
      assertThat(unwrapT(otEmptyOuter)).isEmpty();

      // Lift an outer value containing null (Kind<OptionalKind, String>)
      Kind<OptionalKind<?>, String> wrappedOuterNull =
          OptionalKindHelper.wrap(Optional.ofNullable(null));
      OptionalT<OptionalKind<?>, String> otOuterNull =
          OptionalT.liftF(outerMonad, wrappedOuterNull);
      // Map propagates empty, so result is Optional.empty()
      assertThat(unwrapT(otOuterNull)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("value() should return the wrapped Kind")
    void value_returnsWrapped() {
      // Recreate the Kind<F, Optional<A>> structure for this test
      Kind<OptionalKind<?>, Optional<String>> kindWithPresentOptional =
          OptionalKindHelper.wrap(Optional.of(Optional.of(presentValue)));
      Kind<OptionalKind<?>, Optional<String>> kindWithEmptyOptional =
          OptionalKindHelper.wrap(Optional.of(Optional.empty()));

      OptionalT<OptionalKind<?>, String> ot = OptionalT.fromKind(kindWithPresentOptional);
      assertThat(ot.value()).isSameAs(kindWithPresentOptional);

      OptionalT<OptionalKind<?>, String> otEmpty = OptionalT.fromKind(kindWithEmptyOptional);
      assertThat(otEmpty.value()).isSameAs(kindWithEmptyOptional);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    // Create distinct wrapped Kinds with equal/different content for comparison
    // These need to be Kind<F, Optional<A>>
    Kind<OptionalKind<?>, Optional<String>> wrappedPresentOpt1 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("A")));
    Kind<OptionalKind<?>, Optional<String>> wrappedPresentOpt2 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("A"))); // Equal content
    Kind<OptionalKind<?>, Optional<String>> wrappedPresentOpt3 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("B"))); // Different content
    Kind<OptionalKind<?>, Optional<String>> wrappedEmptyOpt1 =
        OptionalKindHelper.wrap(Optional.of(Optional.empty())); // Inner empty
    Kind<OptionalKind<?>, Optional<String>> wrappedEmptyOpt2 =
        OptionalKindHelper.wrap(Optional.of(Optional.empty())); // Inner empty
    Kind<OptionalKind<?>, Optional<String>> wrappedOuterEmpty1 =
        OptionalKindHelper.wrap(Optional.empty()); // Outer empty

    OptionalT<OptionalKind<?>, String> ot1 = OptionalT.fromKind(wrappedPresentOpt1);
    OptionalT<OptionalKind<?>, String> ot2 = OptionalT.fromKind(wrappedPresentOpt2);
    OptionalT<OptionalKind<?>, String> ot3 = OptionalT.fromKind(wrappedPresentOpt3);
    OptionalT<OptionalKind<?>, String> otEmptyInner1 = OptionalT.fromKind(wrappedEmptyOpt1);
    OptionalT<OptionalKind<?>, String> otEmptyInner2 = OptionalT.fromKind(wrappedEmptyOpt2);
    OptionalT<OptionalKind<?>, String> otOuterEmpty = OptionalT.fromKind(wrappedOuterEmpty1);

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      assertThat(ot1).isEqualTo(ot2); // Equal content
      assertThat(otEmptyInner1).isEqualTo(otEmptyInner2); // Equal empty inner content

      assertThat(ot1).isNotEqualTo(ot3); // Different content
      assertThat(ot1).isNotEqualTo(otEmptyInner1); // Different inner presence
      assertThat(ot1).isNotEqualTo(otOuterEmpty); // Different outer presence
      assertThat(ot1).isNotEqualTo(null);
      assertThat(ot1).isNotEqualTo(wrappedPresentOpt1); // Different type
    }

    @Test
    @DisplayName("equals should return true for self comparison")
    void equals_selfComparison() {
      assertThat(ot1.equals(ot1)).isTrue();
    }

    @Test
    @DisplayName("equals should return false for null comparison")
    void equals_nullComparison() {
      assertThat(ot1.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals should return false for different type comparison")
    void equals_differentTypeComparison() {
      assertThat(ot1.equals(new Object())).isFalse();
      assertThat(ot1.equals(wrappedPresentOpt1)).isFalse();
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      assertThat(ot1.hashCode()).isEqualTo(ot2.hashCode());
      assertThat(otEmptyInner1.hashCode()).isEqualTo(otEmptyInner2.hashCode());

      // It's not guaranteed that non-equal objects have different hash codes,
      // but we check consistency with equals.
      assertThat(ot1.equals(ot3)).isFalse();
      assertThat(ot1.equals(otEmptyInner1)).isFalse();
      assertThat(ot1.equals(otOuterEmpty)).isFalse();
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      // The exact string depends on the toString of the outer Kind and inner Optional
      assertThat(ot1.toString()).startsWith("OptionalT[").endsWith("]");
      assertThat(ot1.toString()).contains("Optional[Optional[A]]"); // Content of wrappedPresentOpt1

      assertThat(otEmptyInner1.toString()).startsWith("OptionalT[").endsWith("]");
      assertThat(otEmptyInner1.toString()).contains("Optional[Optional.empty]");

      assertThat(otOuterEmpty.toString()).startsWith("OptionalT[").endsWith("]");
      assertThat(otOuterEmpty.toString()).contains("Optional.empty"); // Outer empty
    }
  }
}
