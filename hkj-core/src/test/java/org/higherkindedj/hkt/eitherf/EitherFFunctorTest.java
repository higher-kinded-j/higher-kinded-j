// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherFFunctor Test Suite")
class EitherFFunctorTest {

  private EitherFFunctor<IdentityKind.Witness, MaybeKind.Witness> functor;

  @BeforeEach
  void setUp() {
    functor = EitherFFunctor.of(IdentityMonad.INSTANCE, MaybeMonad.INSTANCE);
  }

  private Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> leftKind(
      int value) {
    Kind<IdentityKind.Witness, Integer> inner =
        IdentityKindHelper.IDENTITY.widen(new Identity<>(value));
    return EitherFKindHelper.EITHERF.widen(EitherF.left(inner));
  }

  private Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer> rightKind(
      int value) {
    Kind<MaybeKind.Witness, Integer> inner = MaybeKindHelper.MAYBE.widen(Maybe.just(value));
    return EitherFKindHelper.EITHERF.widen(EitherF.right(inner));
  }

  /** Extracts the Identity value from a Left EitherF via fold. */
  private <A> A extractLeft(EitherF<IdentityKind.Witness, MaybeKind.Witness, A> ef) {
    return ef.fold(
        left -> IdentityKindHelper.IDENTITY.<A>narrow(left).value(),
        right -> {
          throw new AssertionError("Expected Left but got Right");
        });
  }

  /** Extracts the Maybe value from a Right EitherF via fold. */
  private <A> A extractRight(EitherF<IdentityKind.Witness, MaybeKind.Witness, A> ef) {
    return ef.fold(
        left -> {
          throw new AssertionError("Expected Right but got Left");
        },
        right -> MaybeKindHelper.MAYBE.<A>narrow(right).get());
  }

  @Nested
  @DisplayName("Map Operations")
  class MapOperations {

    @Test
    @DisplayName("map() on Left delegates to left functor")
    void mapOnLeftDelegatesToLeftFunctor() {
      var kind = leftKind(42);
      Function<Integer, String> f = Object::toString;

      var result = functor.map(f, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Left.class);
      assertThat(extractLeft(eitherF)).isEqualTo("42");
    }

    @Test
    @DisplayName("map() on Right delegates to right functor")
    void mapOnRightDelegatesToRightFunctor() {
      var kind = rightKind(42);
      Function<Integer, String> f = Object::toString;

      var result = functor.map(f, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Right.class);
      assertThat(extractRight(eitherF)).isEqualTo("42");
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @Test
    @DisplayName("Identity law: map(id, fa) == fa for Left")
    void identityLawForLeft() {
      var kind = leftKind(42);
      Function<Integer, Integer> id = Function.identity();

      var result = functor.map(id, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> mapped =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(mapped).isInstanceOf(EitherF.Left.class);
      assertThat(extractLeft(mapped)).isEqualTo(42);
    }

    @Test
    @DisplayName("Identity law: map(id, fa) == fa for Right")
    void identityLawForRight() {
      var kind = rightKind(42);
      Function<Integer, Integer> id = Function.identity();

      var result = functor.map(id, kind);

      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> mapped =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(mapped).isInstanceOf(EitherF.Right.class);
      assertThat(extractRight(mapped)).isEqualTo(42);
    }

    @Test
    @DisplayName("Composition law: map(g.compose(f)) == map(g, map(f)) for Left")
    void compositionLawForLeft() {
      var kind = leftKind(42);
      Function<Integer, String> f = Object::toString;
      Function<String, Integer> g = String::length;

      var composed = functor.map(g.compose(f), kind);
      var chained = functor.map(g, functor.map(f, kind));

      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> composedEf =
          EitherFKindHelper.EITHERF.narrow(composed);
      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> chainedEf =
          EitherFKindHelper.EITHERF.narrow(chained);

      assertThat(extractLeft(composedEf)).isEqualTo(extractLeft(chainedEf));
    }

    @Test
    @DisplayName("Composition law: map(g.compose(f)) == map(g, map(f)) for Right")
    void compositionLawForRight() {
      var kind = rightKind(42);
      Function<Integer, String> f = Object::toString;
      Function<String, Integer> g = String::length;

      var composed = functor.map(g.compose(f), kind);
      var chained = functor.map(g, functor.map(f, kind));

      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> composedEf =
          EitherFKindHelper.EITHERF.narrow(composed);
      EitherF<IdentityKind.Witness, MaybeKind.Witness, Integer> chainedEf =
          EitherFKindHelper.EITHERF.narrow(chained);

      assertThat(extractRight(composedEf)).isEqualTo(extractRight(chainedEf));
    }
  }

  @Nested
  @DisplayName("Null Parameter Validation")
  class NullValidation {

    @Test
    @DisplayName("Constructor rejects null functorF")
    void constructorRejectsNullFunctorF() {
      assertThatThrownBy(() -> EitherFFunctor.of(null, MaybeMonad.INSTANCE))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Constructor rejects null functorG")
    void constructorRejectsNullFunctorG() {
      assertThatThrownBy(
              () ->
                  EitherFFunctor.<IdentityKind.Witness, MaybeKind.Witness>of(
                      IdentityMonad.INSTANCE, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() rejects null function")
    void mapRejectsNullFunction() {
      var kind = leftKind(42);
      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() rejects null Kind argument")
    void mapRejectsNullKind() {
      assertThatThrownBy(() -> functor.map(Object::toString, null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
