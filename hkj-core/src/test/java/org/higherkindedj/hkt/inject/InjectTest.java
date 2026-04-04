// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherf.EitherFKind;
import org.higherkindedj.hkt.eitherf.EitherFKindHelper;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Inject Test Suite")
class InjectTest {

  private Kind<IdentityKind.Witness, String> identityOp(String value) {
    return IdentityKindHelper.IDENTITY.widen(new Identity<>(value));
  }

  private Kind<MaybeKind.Witness, String> maybeOp(String value) {
    return MaybeKindHelper.MAYBE.widen(Maybe.just(value));
  }

  @Nested
  @DisplayName("injectLeft")
  class InjectLeftTests {

    @Test
    @DisplayName("injectLeft wraps value in EitherF.Left")
    void injectLeftWrapsInLeft() {
      Inject<IdentityKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          inject = InjectInstances.injectLeft();

      Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, String> result =
          inject.inject(identityOp("hello"));

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Left.class);

      // Use fold to extract value with correct types
      String value =
          eitherF.fold(
              left -> IdentityKindHelper.IDENTITY.<String>narrow(left).value(),
              right -> {
                throw new AssertionError("Expected Left");
              });
      assertThat(value).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("injectRight")
  class InjectRightTests {

    @Test
    @DisplayName("injectRight wraps value in EitherF.Right")
    void injectRightWrapsInRight() {
      Inject<MaybeKind.Witness, EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>>
          inject = InjectInstances.injectRight();

      Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, String> result =
          inject.inject(maybeOp("world"));

      EitherF<IdentityKind.Witness, MaybeKind.Witness, String> eitherF =
          EitherFKindHelper.EITHERF.narrow(result);
      assertThat(eitherF).isInstanceOf(EitherF.Right.class);

      // Use fold to extract value with correct types
      String value =
          eitherF.fold(
              left -> {
                throw new AssertionError("Expected Right");
              },
              right -> MaybeKindHelper.MAYBE.<String>narrow(right).get());
      assertThat(value).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("injectRightThen (transitive)")
  class InjectRightThenTests {

    @Test
    @DisplayName("injectRightThen navigates through right side")
    void injectRightThenNavigatesThrough() {
      // Compose three effects: Identity, Maybe, Identity
      // Inject Identity (the third) into EitherF<Identity, EitherF<Maybe, Identity>>
      Inject<IdentityKind.Witness, EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>
          innerInject = InjectInstances.injectRight();

      Inject<
              IdentityKind.Witness,
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>>
          outerInject = InjectInstances.injectRightThen(innerInject);

      Kind<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              String>
          result = outerInject.inject(identityOp("deep"));

      // Should be Right(Right(identityOp("deep")))
      EitherF<
              IdentityKind.Witness,
              EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>,
              String>
          outer = EitherFKindHelper.EITHERF.narrow(result);
      assertThat(outer).isInstanceOf(EitherF.Right.class);

      // Use fold to navigate with correct types
      String value =
          outer.fold(
              left -> {
                throw new AssertionError("Expected outer Right");
              },
              rightKind -> {
                EitherF<MaybeKind.Witness, IdentityKind.Witness, String> inner =
                    EitherFKindHelper.EITHERF.narrow(rightKind);
                assertThat(inner).isInstanceOf(EitherF.Right.class);
                return inner.fold(
                    innerLeft -> {
                      throw new AssertionError("Expected inner Right");
                    },
                    innerRight -> IdentityKindHelper.IDENTITY.<String>narrow(innerRight).value());
              });
      assertThat(value).isEqualTo("deep");
    }

    @Test
    @DisplayName("injectRightThen rejects null inner Inject")
    void injectRightThenRejectsNull() {
      Assertions.assertThatThrownBy(() -> InjectInstances.injectRightThen(null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
