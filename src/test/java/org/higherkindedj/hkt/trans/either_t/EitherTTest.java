// Create this file: src/test/java/org/higherkindedj/hkt/trans/EitherTTest.java
package org.higherkindedj.hkt.trans.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherT Class Tests (Outer: Optional, Left: String)")
class EitherTTest {

  // Outer Monad (F = OptionalKind<?>)
  private Monad<OptionalKind<?>> outerMonad;

  // Test data
  private final String rightValue = "Success";
  private final String leftValue = "Error";
  private final Integer otherRightValue = 123;

  private Kind<OptionalKind<?>, Either<String, String>> wrappedRight;
  private Kind<OptionalKind<?>, Either<String, String>> wrappedLeft;
  private Kind<OptionalKind<?>, Either<String, String>> wrappedEmpty;
  private Kind<OptionalKind<?>, Integer> wrappedOuterValue;

  private Either<String, String> plainRight;
  private Either<String, String> plainLeft;

  @BeforeEach
  void setUp() {
    // Use OptionalMonad as the concrete Monad<F> needed for factory methods
    outerMonad = new OptionalMonad();

    // Prepare wrapped kinds for testing fromKind
    wrappedRight = OptionalKindHelper.wrap(Optional.of(Either.right(rightValue)));
    wrappedLeft = OptionalKindHelper.wrap(Optional.of(Either.left(leftValue)));
    wrappedEmpty = OptionalKindHelper.wrap(Optional.empty());

    // Prepare outer value for liftF
    wrappedOuterValue = OptionalKindHelper.wrap(Optional.of(otherRightValue));

    // Prepare plain Either for fromEither
    plainRight = Either.right(rightValue);
    plainLeft = Either.left(leftValue);
  }

  // Helper to unwrap EitherT<OptionalKind<?>, String, A> -> Optional<Either<String, A>>
  private <A> Optional<Either<String, A>> unwrapT(EitherT<OptionalKind<?>, String, A> eitherT) {
    Kind<OptionalKind<?>, Either<String, A>> outerKind = eitherT.value();
    return OptionalKindHelper.unwrap(outerKind);
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("fromKind should wrap existing Kind<F, Either<L, R>>")
    void fromKind_wrapsExisting() {
      EitherT<OptionalKind<?>, String, String> etRight = EitherT.fromKind(wrappedRight);
      EitherT<OptionalKind<?>, String, String> etLeft = EitherT.fromKind(wrappedLeft);
      EitherT<OptionalKind<?>, String, String> etEmpty = EitherT.fromKind(wrappedEmpty);

      assertThat(unwrapT(etRight)).isPresent().contains(Either.right(rightValue));
      assertThat(unwrapT(etLeft)).isPresent().contains(Either.left(leftValue));
      assertThat(unwrapT(etEmpty)).isEmpty();

      // Check getValue returns the original wrapped kind
      assertThat(etRight.value()).isSameAs(wrappedRight);
      assertThat(etLeft.value()).isSameAs(wrappedLeft);
      assertThat(etEmpty.value()).isSameAs(wrappedEmpty);
    }

    @Test
    @DisplayName("fromKind should throw NullPointerException for null input")
    void fromKind_throwsOnNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> EitherT.fromKind(null))
          .withMessageContaining("Wrapped value cannot be null");
    }

    @Test
    @DisplayName("right should lift value to EitherT(F<Right(R)>)")
    void right_liftsValue() {
      EitherT<OptionalKind<?>, String, String> et = EitherT.right(outerMonad, rightValue);
      assertThat(unwrapT(et)).isPresent().contains(Either.right(rightValue));
    }

    @Test
    @DisplayName("right should lift null value to EitherT(F<Right(null)>)")
    void right_liftsNullValue() {
      EitherT<OptionalKind<?>, String, String> et = EitherT.right(outerMonad, null);
      assertThat(unwrapT(et)).isPresent().contains(Either.right(null));
    }

    @Test
    @DisplayName("left should lift value to EitherT(F<Left(L)>)")
    void left_liftsValue() {
      // Specify type parameter R explicitly for clarity if needed
      EitherT<OptionalKind<?>, String, Integer> et = EitherT.left(outerMonad, leftValue);
      assertThat(unwrapT(et)).isPresent().contains(Either.left(leftValue));
    }

    @Test
    @DisplayName("fromEither should lift Either to EitherT(F<Either>)")
    void fromEither_liftsEither() {
      EitherT<OptionalKind<?>, String, String> etRight = EitherT.fromEither(outerMonad, plainRight);
      EitherT<OptionalKind<?>, String, String> etLeft = EitherT.fromEither(outerMonad, plainLeft);

      assertThat(unwrapT(etRight)).isPresent().contains(plainRight);
      assertThat(unwrapT(etLeft)).isPresent().contains(plainLeft);
    }

    @Test
    @DisplayName("liftF should lift Kind<F, R> to EitherT(F<Right(R)>)")
    void liftF_liftsOuterKind() {
      // Specify type parameter L explicitly
      EitherT<OptionalKind<?>, String, Integer> et = EitherT.liftF(outerMonad, wrappedOuterValue);
      assertThat(unwrapT(et)).isPresent().contains(Either.right(otherRightValue));
    }

    @Test
    @DisplayName("liftF should handle empty outer Kind")
    void liftF_handlesEmptyOuterKind() {
      Kind<OptionalKind<?>, Integer> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
      EitherT<OptionalKind<?>, String, Integer> et = EitherT.liftF(outerMonad, emptyOuter);
      // liftF uses outerMonad.map, which for Optional propagates empty
      assertThat(unwrapT(et)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("getValue should return the wrapped Kind")
    void getValue_returnsWrapped() {
      EitherT<OptionalKind<?>, String, String> et = EitherT.fromKind(wrappedRight);
      assertThat(et.value()).isSameAs(wrappedRight);

      EitherT<OptionalKind<?>, String, String> etEmpty = EitherT.fromKind(wrappedEmpty);
      assertThat(etEmpty.value()).isSameAs(wrappedEmpty);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    @Test
    @DisplayName("equals should compare based on wrapped value")
    void equals_comparesWrappedValue() {
      // Create distinct wrapped Kinds with equal content
      Kind<OptionalKind<?>, Either<String, String>> wrappedRight1 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedRight2 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedRight3 =
          OptionalKindHelper.wrap(Optional.of(Either.right("B")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedLeft1 =
          OptionalKindHelper.wrap(Optional.of(Either.left("E")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedEmpty1 =
          OptionalKindHelper.wrap(Optional.empty());

      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrappedRight1);
      EitherT<OptionalKind<?>, String, String> et2 =
          EitherT.fromKind(wrappedRight2); // Equal content
      EitherT<OptionalKind<?>, String, String> et3 =
          EitherT.fromKind(wrappedRight3); // Different content
      EitherT<OptionalKind<?>, String, String> etLeft = EitherT.fromKind(wrappedLeft1);
      EitherT<OptionalKind<?>, String, String> etEmpty = EitherT.fromKind(wrappedEmpty1);

      assertThat(et1).isEqualTo(et2);
      assertThat(et1).isNotEqualTo(et3);
      assertThat(et1).isNotEqualTo(etLeft);
      assertThat(et1).isNotEqualTo(etEmpty);
      assertThat(et1).isNotEqualTo(null);
      assertThat(et1).isNotEqualTo(wrappedRight1); // Different type
    }

    @Test
    @DisplayName("equals should return true for self comparison")
    void equals_selfComparison() {
      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrappedRight);
      assertThat(et1.equals(et1)).isTrue(); // Explicitly test obj == this
    }

    @Test
    @DisplayName("equals should return false for null comparison")
    void equals_nullComparison() {
      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrappedRight);
      // Use AssertJ's standard equals method, which handles the null check internally
      // but this test ensures the internal o == null branch is hit if called directly.
      assertThat(et1.equals(null)).isFalse(); // Test obj == null branch
    }

    @Test
    @DisplayName("equals should return false for different type comparison")
    void equals_differentTypeComparison() {
      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrappedRight);
      // Test getClass() != o.getClass() branch
      assertThat(et1.equals(new Object())).isFalse();
      // Also test against the wrapped value itself
      assertThat(et1.equals(wrappedRight)).isFalse();
    }

    @Test
    @DisplayName("equals should differentiate based on wrapped Kind content")
    void equals_differentiatesContent() {
      EitherT<OptionalKind<?>, String, String> etRight =
          EitherT.fromKind(wrappedRight); // Optional[Right("Success")]
      EitherT<OptionalKind<?>, String, String> etLeft =
          EitherT.fromKind(wrappedLeft); // Optional[Left("Error")]
      EitherT<OptionalKind<?>, String, String> etEmpty =
          EitherT.fromKind(wrappedEmpty); // Optional.empty

      assertThat(etRight).isNotEqualTo(etLeft);
      assertThat(etLeft).isNotEqualTo(etRight);

      assertThat(etRight).isNotEqualTo(etEmpty);
      assertThat(etEmpty).isNotEqualTo(etRight);

      assertThat(etLeft).isNotEqualTo(etEmpty);
      assertThat(etEmpty).isNotEqualTo(etLeft);
    }

    // Keep existing tests for equals/hashCode/toString...
    // Ensure you have tests comparing two *equal* EitherT instances as well.
    @Test
    @DisplayName("equals should return true for equal wrapped Kinds")
    void equals_comparesEqualWrappedValue() {
      // Re-create wrapped Kinds to ensure they are equal but not the same instance
      Kind<OptionalKind<?>, Either<String, String>> wrapped1 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));
      Kind<OptionalKind<?>, Either<String, String>> wrapped2 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));

      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrapped1);
      EitherT<OptionalKind<?>, String, String> et2 = EitherT.fromKind(wrapped2); // Equal content

      assertThat(et1).isEqualTo(et2);
    }

    @Test
    @DisplayName("hashCode should be consistent with equals")
    void hashCode_consistentWithEquals() {
      // Create distinct wrapped Kinds with equal content
      Kind<OptionalKind<?>, Either<String, String>> wrappedRight1 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedRight2 =
          OptionalKindHelper.wrap(Optional.of(Either.right("A")));
      Kind<OptionalKind<?>, Either<String, String>> wrappedLeft1 =
          OptionalKindHelper.wrap(Optional.of(Either.left("E")));

      EitherT<OptionalKind<?>, String, String> et1 = EitherT.fromKind(wrappedRight1);
      EitherT<OptionalKind<?>, String, String> et2 =
          EitherT.fromKind(wrappedRight2); // Equal content
      EitherT<OptionalKind<?>, String, String> etLeft =
          EitherT.fromKind(wrappedLeft1); // Different content

      assertThat(et1.hashCode()).isEqualTo(et2.hashCode());
      // It's not guaranteed that non-equal objects have different hash codes,
      // but we can check consistency
      assertThat(et1.equals(etLeft)).isFalse();
      // We cannot assert et1.hashCode() != etLeft.hashCode() reliably
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      EitherT<OptionalKind<?>, String, String> etRight = EitherT.fromKind(wrappedRight);
      EitherT<OptionalKind<?>, String, String> etLeft = EitherT.fromKind(wrappedLeft);
      EitherT<OptionalKind<?>, String, String> etEmpty = EitherT.fromKind(wrappedEmpty);

      // The exact string depends on the toString of the outer Kind and inner Either
      assertThat(etRight.toString()).startsWith("EitherT[").endsWith("]");
      assertThat(etRight.toString()).contains("Optional[Right(Success)]");

      assertThat(etLeft.toString()).startsWith("EitherT[").endsWith("]");
      assertThat(etLeft.toString()).contains("Optional[Left(Error)]");

      assertThat(etEmpty.toString()).startsWith("EitherT[").endsWith("]");
      assertThat(etEmpty.toString()).contains("Optional.empty");
    }
  }
}
