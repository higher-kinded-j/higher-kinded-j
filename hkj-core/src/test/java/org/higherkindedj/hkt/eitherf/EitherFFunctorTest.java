// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link EitherFFunctor}.
 *
 * <p>Verifies the Functor operations and laws; the laws are driven by the shipped {@link
 * FunctorLaws} over {@link EitherFLawFixtures}.
 */
@DisplayName("EitherFFunctor Test Suite")
class EitherFFunctorTest {

  private EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor;

  @BeforeEach
  void setUp() {
    functor = EitherFFunctor.of(IdentityMonad.INSTANCE, Instances.monadError(maybe()));
  }

  private Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> leftKind(
      int value) {
    Kind<IdentityKind.Witness, Integer> inner =
        IdentityKindHelper.IDENTITY.widen(new Identity<>(value));
    return EitherFKindHelper.EITHERF.widen(EitherF.left(inner));
  }

  /** Extracts the Identity value from a Left EitherF via fold. */
  private <A> A extractLeft(EitherF<IdentityKind.Witness, MaybeKind.Witness, A> ef) {
    return ef.fold(
        left -> IdentityKindHelper.IDENTITY.narrow(left).value(),
        _ -> {
          throw new AssertionError("Expected Left but got Right");
        });
  }

  /** Extracts the Maybe value from a Right EitherF via fold. */
  private <A> A extractRight(EitherF<IdentityKind.Witness, MaybeKind.Witness, A> ef) {
    return ef.fold(
        _ -> {
          throw new AssertionError("Expected Right but got Left");
        },
        right -> MaybeKindHelper.MAYBE.narrow(right).get());
  }

  /**
   * {@code EitherFFunctor.map} delegates eagerly to the underlying functor on the present side, so
   * a thrown mapper surfaces synchronously — {@link Category#EXCEPTIONS} is exercised. The Functor
   * laws are verified in the {@code Laws} block below.
   */
  @Test
  @DisplayName("Functor contract — operations, validations & exceptions")
  void functorContract() {
    TypeClassContract.<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>functor(
            EitherFFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(leftKind(42))
        .withMapper(Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Map Operations")
  class MapOperations {

    @Test
    @DisplayName("map() on Left delegates to left functor")
    void mapOnLeftDelegatesToLeftFunctor() {
      var kind = leftKind(7);
      Function<Integer, String> f = Object::toString;

      var result = functor.map(f, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Left.class);
      assertThat(extractLeft(eitherF)).isEqualTo("7");
    }

    @Test
    @DisplayName("map() on Right delegates to right functor")
    void mapOnRightDelegatesToRightFunctor() {
      Kind<MaybeKind.Witness, Integer> inner = MaybeKindHelper.MAYBE.widen(Maybe.just(99));
      Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> kind =
          EitherFKindHelper.EITHERF.widen(EitherF.right(inner));
      Function<Integer, String> f = Object::toString;

      var result = functor.map(f, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Right.class);
      assertThat(extractRight(eitherF)).isEqualTo("99");
    }
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherf.EitherFLawFixtures#kinds")
    void identity(
        String label,
        Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, EitherFLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.eitherf.EitherFLawFixtures#kinds")
    void composition(
        String label,
        Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> fa) {
      Function<Integer, String> f = Object::toString;
      Function<String, Integer> g = String::length;
      FunctorLaws.assertComposition(functor, fa, f, g, EitherFLawFixtures.EQ);
    }
  }

  @Nested
  @DisplayName("Null Parameter Validation")
  class NullValidation {

    @Test
    @DisplayName("Constructor rejects null functorF")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void constructorRejectsNullFunctorF() {
      assertThatThrownBy(() -> EitherFFunctor.of(null, Instances.monadError(maybe())))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Constructor rejects null functorG")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void constructorRejectsNullFunctorG() {
      assertThatThrownBy(
              () ->
                  EitherFFunctor.<IdentityKind.Witness, MaybeKind.Witness>of(
                      IdentityMonad.INSTANCE, null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
