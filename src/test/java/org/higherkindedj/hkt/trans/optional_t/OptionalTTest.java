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

@DisplayName("OptionalT Class Tests (Outer: OptionalKind.Witness)")
class OptionalTTest {

  private Monad<OptionalKind.Witness> outerMonad;

  private final String presentValue = "Success";
  private final Integer otherPresentValue = 123;

  private Kind<OptionalKind.Witness, Optional<String>> wrappedPresent;
  private Kind<OptionalKind.Witness, Optional<String>> wrappedEmpty;
  private Kind<OptionalKind.Witness, Integer> wrappedOuterValue;
  private Kind<OptionalKind.Witness, Integer> wrappedOuterEmptyValue;

  private Optional<String> plainPresent;
  private Optional<String> plainEmpty;

  @BeforeEach
  void setUp() {
    outerMonad = new OptionalMonad(); // OptionalMonad now provides Monad<OptionalKind.Witness>

    wrappedPresent = OptionalKindHelper.wrap(Optional.of(Optional.of(presentValue)));
    wrappedEmpty = OptionalKindHelper.wrap(Optional.of(Optional.empty()));
    // For wrappedOuterEmpty, previously was Kind<OptionalKind<?>,Optional<String>>
    // Now Kind<OptionalKind.Witness, Optional<String>>
    Kind<OptionalKind.Witness, Optional<String>> tempWrappedOuterEmpty =
        OptionalKindHelper.wrap(Optional.empty());

    wrappedOuterValue = OptionalKindHelper.wrap(Optional.of(otherPresentValue));
    wrappedOuterEmptyValue = OptionalKindHelper.wrap(Optional.empty());

    plainPresent = Optional.of(presentValue);
    plainEmpty = Optional.empty();
  }

  private <A> Optional<Optional<A>> unwrapT(OptionalT<OptionalKind.Witness, A> optionalT) {
    Kind<OptionalKind.Witness, Optional<A>> outerKind = optionalT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Optional<A>>")
    void fromKind_wrapsExisting() {
      OptionalT<OptionalKind.Witness, String> otPresent = OptionalT.fromKind(wrappedPresent);
      OptionalT<OptionalKind.Witness, String> otEmptyInner = OptionalT.fromKind(wrappedEmpty);
      // Test with an outer empty Kind. This needs to be Kind<OptionalKind.Witness,
      // Optional<String>>
      Kind<OptionalKind.Witness, Optional<String>> outerEmptyKind =
          OptionalKindHelper.wrap(Optional.empty());
      OptionalT<OptionalKind.Witness, String> otEmptyOuter = OptionalT.fromKind(outerEmptyKind);

      assertThat(unwrapT(otPresent)).isPresent().contains(Optional.of(presentValue));
      assertThat(unwrapT(otEmptyInner)).isPresent().contains(Optional.empty());
      assertThat(unwrapT(otEmptyOuter)).isEmpty();

      assertThat(otPresent.value()).isSameAs(wrappedPresent);
      assertThat(otEmptyInner.value()).isSameAs(wrappedEmpty);
      assertThat(otEmptyOuter.value()).isSameAs(outerEmptyKind);
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
      // MODIFIED: OptionalKind.Witness
      OptionalT<OptionalKind.Witness, String> ot = OptionalT.some(outerMonad, presentValue);
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
      OptionalT<OptionalKind.Witness, Integer> ot = OptionalT.none(outerMonad);
      assertThat(unwrapT(ot)).isPresent().contains(Optional.empty());
    }

    @Test
    @DisplayName("fromOptional should lift Optional to OptionalT(F<Optional>)")
    void fromOptional_liftsOptional() {
      OptionalT<OptionalKind.Witness, String> otPresent =
          OptionalT.fromOptional(outerMonad, plainPresent);
      OptionalT<OptionalKind.Witness, String> otEmpty =
          OptionalT.fromOptional(outerMonad, plainEmpty);

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
      OptionalT<OptionalKind.Witness, Integer> ot = OptionalT.liftF(outerMonad, wrappedOuterValue);
      assertThat(unwrapT(ot)).isPresent().contains(Optional.of(otherPresentValue));
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("value() should return the wrapped Kind")
    void value_returnsWrapped() {
      OptionalT<OptionalKind.Witness, String> ot = OptionalT.fromKind(wrappedPresent);
      assertThat(ot.value()).isSameAs(wrappedPresent);

      Kind<OptionalKind.Witness, Optional<String>> outerEmptyKind =
          OptionalKindHelper.wrap(Optional.empty());
      OptionalT<OptionalKind.Witness, String> otEmptyOuter = OptionalT.fromKind(outerEmptyKind);
      assertThat(otEmptyOuter.value()).isSameAs(outerEmptyKind);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {
    Kind<OptionalKind.Witness, Optional<String>> wrappedPresentOpt1 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("A")));
    Kind<OptionalKind.Witness, Optional<String>> wrappedPresentOpt2 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("A")));
    Kind<OptionalKind.Witness, Optional<String>> wrappedPresentOpt3 =
        OptionalKindHelper.wrap(Optional.of(Optional.of("B")));
    Kind<OptionalKind.Witness, Optional<String>> wrappedEmptyOpt1 =
        OptionalKindHelper.wrap(Optional.of(Optional.empty()));
    Kind<OptionalKind.Witness, Optional<String>> wrappedEmptyOpt2 =
        OptionalKindHelper.wrap(Optional.of(Optional.empty()));
    Kind<OptionalKind.Witness, Optional<String>> wrappedOuterEmpty1 =
        OptionalKindHelper.wrap(Optional.empty());

    OptionalT<OptionalKind.Witness, String> ot1 = OptionalT.fromKind(wrappedPresentOpt1);
    OptionalT<OptionalKind.Witness, String> ot2 = OptionalT.fromKind(wrappedPresentOpt2);
    OptionalT<OptionalKind.Witness, String> ot3 = OptionalT.fromKind(wrappedPresentOpt3);
    OptionalT<OptionalKind.Witness, String> otEmptyInner1 = OptionalT.fromKind(wrappedEmptyOpt1);
    OptionalT<OptionalKind.Witness, String> otEmptyInner2 = OptionalT.fromKind(wrappedEmptyOpt2);
    OptionalT<OptionalKind.Witness, String> otOuterEmpty = OptionalT.fromKind(wrappedOuterEmpty1);

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      assertThat(ot1).isEqualTo(ot2);
      assertThat(otEmptyInner1).isEqualTo(otEmptyInner2);

      assertThat(ot1).isNotEqualTo(ot3);
      assertThat(ot1).isNotEqualTo(otEmptyInner1);
      assertThat(ot1).isNotEqualTo(otOuterEmpty);
      assertThat(ot1).isNotEqualTo(null);
      assertThat(ot1).isNotEqualTo(wrappedPresentOpt1);
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      assertThat(ot1.hashCode()).isEqualTo(ot2.hashCode());
      assertThat(otEmptyInner1.hashCode()).isEqualTo(otEmptyInner2.hashCode());
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      assertThat(ot1.toString()).startsWith("OptionalT[value=").endsWith("]");
      assertThat(ot1.toString()).contains("Optional[Optional[A]]");

      assertThat(otEmptyInner1.toString()).startsWith("OptionalT[value=").endsWith("]");
      assertThat(otEmptyInner1.toString()).contains("Optional[Optional.empty]");

      assertThat(otOuterEmpty.toString()).startsWith("OptionalT[value=").endsWith("]");
      assertThat(otOuterEmpty.toString()).contains("Optional.empty");
    }
  }
}
