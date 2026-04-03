// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Interpreters Test Suite")
class InterpretersTest {

  // Simple interpreters that extract values and wrap them in Identity

  /** Interpreter: Identity<A> -> Identity<A> (pass-through, prepends "id:") */
  private final Natural<IdentityKind.Witness, IdentityKind.Witness> identityInterp =
      new Natural<>() {
        @Override
        public <A> Kind<IdentityKind.Witness, A> apply(Kind<IdentityKind.Witness, A> fa) {
          return fa; // pass-through for Identity
        }
      };

  /** Interpreter: Maybe<A> -> Identity<A> (extracts from Just, wraps in Identity) */
  private final Natural<MaybeKind.Witness, IdentityKind.Witness> maybeInterp =
      new Natural<>() {
        @Override
        public <A> Kind<IdentityKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
          Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
          return IdentityKindHelper.IDENTITY.widen(new Identity<>(maybe.get()));
        }
      };

  private Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, String> leftIdentity(
      String value) {
    Kind<IdentityKind.Witness, String> inner =
        IdentityKindHelper.IDENTITY.widen(new Identity<>(value));
    return EitherFKindHelper.EITHERF.widen(EitherF.left(inner));
  }

  private Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, String> rightMaybe(
      String value) {
    Kind<MaybeKind.Witness, String> inner = MaybeKindHelper.MAYBE.widen(Maybe.just(value));
    return EitherFKindHelper.EITHERF.widen(EitherF.right(inner));
  }

  @Nested
  @DisplayName("combine(2)")
  class CombineTwo {

    @Test
    @DisplayName("Dispatches Left to first interpreter")
    void dispatchesLeftToFirst() {
      Natural<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, IdentityKind.Witness>
          combined = Interpreters.combine(identityInterp, maybeInterp);

      Kind<IdentityKind.Witness, String> result = combined.apply(leftIdentity("hello"));

      Identity<String> id = IdentityKindHelper.IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Dispatches Right to second interpreter")
    void dispatchesRightToSecond() {
      Natural<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, IdentityKind.Witness>
          combined = Interpreters.combine(identityInterp, maybeInterp);

      Kind<IdentityKind.Witness, String> result = combined.apply(rightMaybe("world"));

      Identity<String> id = IdentityKindHelper.IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("combine(3)")
  class CombineThree {

    @Test
    @DisplayName("Dispatches to correct interpreter in three-way composition")
    void dispatchesToCorrectInterpreterInThreeWay() {
      // Third effect: also Identity (reusing for simplicity)
      Natural<IdentityKind.Witness, IdentityKind.Witness> thirdInterp = identityInterp;

      Natural<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              IdentityKind.Witness>
          combined = Interpreters.combine(identityInterp, maybeInterp, thirdInterp);

      // Test: inject into the leftmost position
      Kind<IdentityKind.Witness, String> leftOp =
          IdentityKindHelper.IDENTITY.widen(new Identity<>("first"));
      Kind<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              String>
          leftKind = EitherFKindHelper.EITHERF.widen(EitherF.left(leftOp));

      Kind<IdentityKind.Witness, String> result = combined.apply(leftKind);
      Identity<String> id = IdentityKindHelper.IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo("first");

      // Test: inject into the middle position (right, then left)
      Kind<MaybeKind.Witness, String> middleOp = MaybeKindHelper.MAYBE.widen(Maybe.just("second"));
      Kind<EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>, String> innerLeft =
          EitherFKindHelper.EITHERF.widen(EitherF.left(middleOp));
      Kind<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              String>
          middleKind = EitherFKindHelper.EITHERF.widen(EitherF.right(innerLeft));

      Kind<IdentityKind.Witness, String> middleResult = combined.apply(middleKind);
      Identity<String> middleId = IdentityKindHelper.IDENTITY.narrow(middleResult);
      assertThat(middleId.value()).isEqualTo("second");
    }

    @Test
    @DisplayName("Dispatches to third (rightmost) interpreter in three-way composition")
    void dispatchesToThirdInterpreter() {
      Natural<IdentityKind.Witness, IdentityKind.Witness> thirdInterp = identityInterp;

      Natural<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              IdentityKind.Witness>
          combined = Interpreters.combine(identityInterp, maybeInterp, thirdInterp);

      // Inject into the rightmost position (right, then right)
      Kind<IdentityKind.Witness, String> rightOp =
          IdentityKindHelper.IDENTITY.widen(new Identity<>("third"));
      Kind<EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>, String> innerRight =
          EitherFKindHelper.EITHERF.widen(EitherF.right(rightOp));
      Kind<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<MaybeKind.Witness, IdentityKind.Witness>>,
              String>
          rightKind = EitherFKindHelper.EITHERF.widen(EitherF.right(innerRight));

      Kind<IdentityKind.Witness, String> result = combined.apply(rightKind);
      Identity<String> id = IdentityKindHelper.IDENTITY.narrow(result);
      assertThat(id.value()).isEqualTo("third");
    }
  }

  @Nested
  @DisplayName("combine(4)")
  class CombineFour {

    @Test
    @DisplayName("Dispatches to all four positions in four-way composition")
    void dispatchesToAllFourPositions() {
      // Use Identity for all four to keep it simple
      Natural<IdentityKind.Witness, IdentityKind.Witness> interp = identityInterp;

      // Type: EitherF<Id, EitherF<Id, EitherF<Id, Id>>>
      // For simplicity, use Maybe as second effect to distinguish
      Natural<
              EitherFKind.Witness<
                  IdentityKind.Witness,
                  EitherFKind.Witness<
                      MaybeKind.Witness,
                      EitherFKind.Witness<IdentityKind.Witness, IdentityKind.Witness>>>,
              IdentityKind.Witness>
          combined = Interpreters.combine(identityInterp, maybeInterp, interp, interp);

      // Test first position (Left)
      Kind<IdentityKind.Witness, String> firstOp =
          IdentityKindHelper.IDENTITY.widen(new Identity<>("first"));
      var firstKind =
          EitherFKindHelper.EITHERF.widen(
              EitherF
                  .<IdentityKind.Witness,
                      EitherFKind.Witness<
                          MaybeKind.Witness,
                          EitherFKind.Witness<IdentityKind.Witness, IdentityKind.Witness>>,
                      String>
                      left(firstOp));

      Kind<IdentityKind.Witness, String> firstResult = combined.apply(firstKind);
      assertThat(IdentityKindHelper.IDENTITY.<String>narrow(firstResult).value())
          .isEqualTo("first");

      // Test fourth position (Right, Right, Right)
      Kind<IdentityKind.Witness, String> fourthOp =
          IdentityKindHelper.IDENTITY.widen(new Identity<>("fourth"));
      Kind<EitherFKind.Witness<IdentityKind.Witness, IdentityKind.Witness>, String> inner3 =
          EitherFKindHelper.EITHERF.widen(EitherF.right(fourthOp));
      Kind<
              EitherFKind.Witness<
                  MaybeKind.Witness,
                  EitherFKind.Witness<IdentityKind.Witness, IdentityKind.Witness>>,
              String>
          inner2 = EitherFKindHelper.EITHERF.widen(EitherF.right(inner3));
      var fourthKind =
          EitherFKindHelper.EITHERF.widen(
              EitherF
                  .<IdentityKind.Witness,
                      EitherFKind.Witness<
                          MaybeKind.Witness,
                          EitherFKind.Witness<IdentityKind.Witness, IdentityKind.Witness>>,
                      String>
                      right(inner2));

      Kind<IdentityKind.Witness, String> fourthResult = combined.apply(fourthKind);
      assertThat(IdentityKindHelper.IDENTITY.<String>narrow(fourthResult).value())
          .isEqualTo("fourth");
    }
  }

  @Nested
  @DisplayName("Null Validation")
  class NullValidation {

    @Test
    @DisplayName("combine(2) rejects null first interpreter")
    void combineRejectsNullFirst() {
      assertThatThrownBy(() -> Interpreters.combine(null, maybeInterp))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("combine(2) rejects null second interpreter")
    void combineRejectsNullSecond() {
      assertThatThrownBy(() -> Interpreters.combine(identityInterp, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("combine(3) rejects null parameters")
    void combine3RejectsNull() {
      assertThatThrownBy(() -> Interpreters.combine(null, maybeInterp, identityInterp))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Interpreters.combine(identityInterp, null, identityInterp))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Interpreters.combine(identityInterp, maybeInterp, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("combine(4) rejects null parameters")
    void combine4RejectsNull() {
      assertThatThrownBy(
              () -> Interpreters.combine(null, maybeInterp, identityInterp, identityInterp))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(
              () -> Interpreters.combine(identityInterp, maybeInterp, identityInterp, null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
