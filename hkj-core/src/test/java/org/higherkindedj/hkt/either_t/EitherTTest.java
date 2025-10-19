// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherT Core Type Tests (Outer: OptionalKind.Witness, Left: String)")
class EitherTTest {

    private Monad<OptionalKind.Witness> outerMonad;

    private final String rightValue = "Success";
    private final String leftValue = "Error";
    private final Integer otherRightValue = 123;

    private Kind<OptionalKind.Witness, Either<String, String>> wrappedRight;
    private Kind<OptionalKind.Witness, Either<String, String>> wrappedLeft;
    private Kind<OptionalKind.Witness, Either<String, String>> wrappedEmpty;
    private Kind<OptionalKind.Witness, Integer> wrappedOuterValue;

    private Either<String, String> plainRight;
    private Either<String, String> plainLeft;

    @BeforeEach
    void setUp() {
        outerMonad = OptionalMonad.INSTANCE;

        wrappedRight = OPTIONAL.widen(Optional.of(Either.right(rightValue)));
        wrappedLeft = OPTIONAL.widen(Optional.of(Either.left(leftValue)));
        wrappedEmpty = OPTIONAL.widen(Optional.empty());

        wrappedOuterValue = OPTIONAL.widen(Optional.of(otherRightValue));

        plainRight = Either.right(rightValue);
        plainLeft = Either.left(leftValue);
    }

    private <A> Optional<Either<String, A>> unwrapT(
            EitherT<OptionalKind.Witness, String, A> eitherT) {
        Kind<OptionalKind.Witness, Either<String, A>> outerKind = eitherT.value();
        return OPTIONAL.narrow(outerKind);
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("fromKind should wrap existing Kind<F, Either<L, R>>")
        void fromKind_wrapsExisting() {
            EitherT<OptionalKind.Witness, String, String> etRight = EitherT.fromKind(wrappedRight);
            EitherT<OptionalKind.Witness, String, String> etLeft = EitherT.fromKind(wrappedLeft);
            EitherT<OptionalKind.Witness, String, String> etEmpty = EitherT.fromKind(wrappedEmpty);

            assertThat(unwrapT(etRight)).isPresent().contains(Either.right(rightValue));
            assertThat(unwrapT(etLeft)).isPresent().contains(Either.left(leftValue));
            assertThat(unwrapT(etEmpty)).isEmpty();

            assertThat(etRight.value()).isSameAs(wrappedRight);
            assertThat(etLeft.value()).isSameAs(wrappedLeft);
            assertThat(etEmpty.value()).isSameAs(wrappedEmpty);
        }

        @Test
        @DisplayName("right should lift value to EitherT(F<Right(R)>)")
        void right_liftsValue() {
            EitherT<OptionalKind.Witness, String, String> et = EitherT.right(outerMonad, rightValue);
            assertThat(unwrapT(et)).isPresent().contains(Either.right(rightValue));
        }

        @Test
        @DisplayName("right should lift null value to EitherT(F<Right(null)>)")
        void right_liftsNullValue() {
            EitherT<OptionalKind.Witness, String, String> et = EitherT.right(outerMonad, (String) null);
            assertThat(unwrapT(et)).isPresent().contains(Either.right(null));
        }

        @Test
        @DisplayName("left should lift value to EitherT(F<Left(L)>)")
        void left_liftsValue() {
            EitherT<OptionalKind.Witness, String, Integer> et = EitherT.left(outerMonad, leftValue);
            assertThat(unwrapT(et)).isPresent().contains(Either.left(leftValue));
        }

        @Test
        @DisplayName("fromEither should lift Either to EitherT(F<Either>)")
        void fromEither_liftsEither() {
            EitherT<OptionalKind.Witness, String, String> etRight =
                    EitherT.fromEither(outerMonad, plainRight);
            EitherT<OptionalKind.Witness, String, String> etLeft =
                    EitherT.fromEither(outerMonad, plainLeft);

            assertThat(unwrapT(etRight)).isPresent().contains(plainRight);
            assertThat(unwrapT(etLeft)).isPresent().contains(plainLeft);
        }

        @Test
        @DisplayName("liftF should lift Kind<F, R> to EitherT(F<Right(R)>)")
        void liftF_liftsOuterKind() {
            EitherT<OptionalKind.Witness, String, Integer> et =
                    EitherT.liftF(outerMonad, wrappedOuterValue);
            assertThat(unwrapT(et)).isPresent().contains(Either.right(otherRightValue));
        }

        @Test
        @DisplayName("liftF should handle empty outer Kind")
        void liftF_handlesEmptyOuterKind() {
            Kind<OptionalKind.Witness, Integer> emptyOuter = OPTIONAL.widen(Optional.empty());
            EitherT<OptionalKind.Witness, String, Integer> et = EitherT.liftF(outerMonad, emptyOuter);
            assertThat(unwrapT(et)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Instance Methods")
    class InstanceMethodTests {

        @Test
        @DisplayName("value should return the wrapped Kind")
        void value_returnsWrapped() {
            EitherT<OptionalKind.Witness, String, String> et = EitherT.fromKind(wrappedRight);
            assertThat(et.value()).isSameAs(wrappedRight);

            EitherT<OptionalKind.Witness, String, String> etEmpty = EitherT.fromKind(wrappedEmpty);
            assertThat(etEmpty.value()).isSameAs(wrappedEmpty);
        }
    }

    @Nested
    @DisplayName("Object Methods")
    class ObjectMethodTests {

        Kind<OptionalKind.Witness, Either<String, String>> wrappedRight1 =
                OPTIONAL.widen(Optional.of(Either.right("A")));
        Kind<OptionalKind.Witness, Either<String, String>> wrappedRight2 =
                OPTIONAL.widen(Optional.of(Either.right("A")));
        Kind<OptionalKind.Witness, Either<String, String>> wrappedRight3 =
                OPTIONAL.widen(Optional.of(Either.right("B")));
        Kind<OptionalKind.Witness, Either<String, String>> wrappedLeft1 =
                OPTIONAL.widen(Optional.of(Either.left("E")));
        Kind<OptionalKind.Witness, Either<String, String>> wrappedEmpty1 =
                OPTIONAL.widen(Optional.empty());

        EitherT<OptionalKind.Witness, String, String> et1 = EitherT.fromKind(wrappedRight1);
        EitherT<OptionalKind.Witness, String, String> et2 = EitherT.fromKind(wrappedRight2);
        EitherT<OptionalKind.Witness, String, String> et3 = EitherT.fromKind(wrappedRight3);
        EitherT<OptionalKind.Witness, String, String> etLeft = EitherT.fromKind(wrappedLeft1);
        EitherT<OptionalKind.Witness, String, String> etEmpty = EitherT.fromKind(wrappedEmpty1);

        @Test
        @DisplayName("equals should compare based on wrapped value")
        void equals_comparesWrappedValue() {
            assertThat(et1).isEqualTo(et2);
            assertThat(et1).isNotEqualTo(et3);
            assertThat(et1).isNotEqualTo(etLeft);
            assertThat(et1).isNotEqualTo(etEmpty);
            assertThat(et1).isNotEqualTo(null);
            assertThat(et1).isNotEqualTo(wrappedRight1);
        }

        @Test
        @DisplayName("equals should return true for self comparison")
        void equals_selfComparison() {
            assertThat(et1.equals(et1)).isTrue();
        }

        @Test
        @DisplayName("equals should return false for null comparison")
        void equals_nullComparison() {
            assertThat(et1.equals(null)).isFalse();
        }

        @Test
        @DisplayName("equals should return false for different type comparison")
        void equals_differentTypeComparison() {
            assertThat(et1.equals(new Object())).isFalse();
            assertThat(et1.equals(wrappedRight1)).isFalse();
        }

        @Test
        @DisplayName("equals should differentiate based on wrapped Kind content")
        void equals_differentiatesContent() {
            assertThat(et1).isNotEqualTo(etLeft);
            assertThat(etLeft).isNotEqualTo(et1);
            assertThat(et1).isNotEqualTo(etEmpty);
            assertThat(etEmpty).isNotEqualTo(et1);
            assertThat(etLeft).isNotEqualTo(etEmpty);
            assertThat(etEmpty).isNotEqualTo(etLeft);
        }

        @Test
        @DisplayName("equals should return true for equal wrapped Kinds")
        void equals_comparesEqualWrappedValue() {
            Kind<OptionalKind.Witness, Either<String, String>> locWrapped1 =
                    OPTIONAL.widen(Optional.of(Either.right("A")));
            Kind<OptionalKind.Witness, Either<String, String>> locWrapped2 =
                    OPTIONAL.widen(Optional.of(Either.right("A")));

            EitherT<OptionalKind.Witness, String, String> localEt1 = EitherT.fromKind(locWrapped1);
            EitherT<OptionalKind.Witness, String, String> localEt2 = EitherT.fromKind(locWrapped2);

            assertThat(localEt1).isEqualTo(localEt2);
        }

        @Test
        @DisplayName("hashCode should be consistent with equals")
        void hashCode_consistentWithEquals() {
            assertThat(et1.hashCode()).isEqualTo(et2.hashCode());
        }

        @Test
        @DisplayName("toString should represent the structure")
        void toString_representsStructure() {
            assertThat(et1.toString()).startsWith("EitherT[value=").endsWith("]");
            assertThat(et1.toString()).contains("Optional[Right(A)]");

            assertThat(etLeft.toString()).startsWith("EitherT[value=").endsWith("]");
            assertThat(etLeft.toString()).contains("Optional[Left(E)]");

            assertThat(etEmpty.toString()).startsWith("EitherT[value=").endsWith("]");
            assertThat(etEmpty.toString()).contains("Optional.empty");
        }
    }

    @Nested
    @DisplayName("Complete Core Type Test Suite")
    class CompleteCoreTypeTests {

        @Test
        @DisplayName("Run complete EitherT core type tests")
        void runCompleteEitherTTests() {
            CoreTypeTest.eitherT(EitherT.class, outerMonad)
                    .withLeft(EitherT.left(outerMonad, leftValue))
                    .withRight(EitherT.right(outerMonad, rightValue))
                    .withMappers(Object::toString)
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Edge case: null values in Either")
        void edgeCase_nullValuesInEither() {
            EitherT<OptionalKind.Witness, String, String> leftNull =
                    EitherT.left(outerMonad, null);
            assertThat(leftNull).isNotNull();
            assertThat(leftNull.value()).isNotNull();

            EitherT<OptionalKind.Witness, String, String> rightNull =
                    EitherT.right(outerMonad, null);
            assertThat(rightNull).isNotNull();
            assertThat(rightNull.value()).isNotNull();
        }

        @Test
        @DisplayName("Edge case: fromEither with Left")
        void edgeCase_fromEitherWithLeft() {
            Either<String, String> eitherLeft = Either.left(null);
            EitherT<OptionalKind.Witness, String, String> fromEitherLeft =
                    EitherT.fromEither(outerMonad, eitherLeft);
            assertThat(fromEitherLeft).isNotNull();
            assertThat(fromEitherLeft.value()).isNotNull();
        }

        @Test
        @DisplayName("Edge case: fromEither with Right")
        void edgeCase_fromEitherWithRight() {
            Either<String, String> eitherRight = Either.right(null);
            EitherT<OptionalKind.Witness, String, String> fromEitherRight =
                    EitherT.fromEither(outerMonad, eitherRight);
            assertThat(fromEitherRight).isNotNull();
            assertThat(fromEitherRight.value()).isNotNull();
        }

        @Test
        @DisplayName("Edge case: liftF with null-valued outer Kind")
        void edgeCase_liftFWithNullValuedOuter() {
            Kind<OptionalKind.Witness, String> liftedValue = outerMonad.of(null);
            EitherT<OptionalKind.Witness, String, String> lifted =
                    EitherT.liftF(outerMonad, liftedValue);
            assertThat(lifted).isNotNull();
            assertThat(lifted.value()).isNotNull();
        }
    }
}