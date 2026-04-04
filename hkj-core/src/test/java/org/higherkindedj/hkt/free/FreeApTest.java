// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.free.FreeAssert.assertThatFree;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Free.Ap Test Suite")
class FreeApTest {

  private final IdentityMonad identityMonad = IdentityMonad.INSTANCE;

  @Nested
  @DisplayName("Ap constructor")
  class ConstructorTests {

    @Test
    @DisplayName("Ap rejects null applicative")
    void apRejectsNullApplicative() {
      assertThatThrownBy(() -> new Free.Ap<>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Ap creates correct Free variant")
    void apCreatesCorrectVariant() {
      FreeAp<IdentityKind.Witness, Integer> freeAp = FreeAp.pure(42);
      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(freeAp);

      assertThatFree(program).isAp();
    }
  }

  @Nested
  @DisplayName("Ap interpretation with Natural (foldMap)")
  class InterpretationNatural {

    @Test
    @DisplayName("Ap with Pure FreeAp returns pure value")
    void apWithPureReturnsValue() {
      FreeAp<IdentityKind.Witness, String> freeAp = FreeAp.pure("hello");
      Free<IdentityKind.Witness, String> program = new Free.Ap<>(freeAp);

      Kind<IdentityKind.Witness, String> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Ap with Lift FreeAp interprets instruction")
    void apWithLiftInterpretsInstruction() {
      Kind<IdentityKind.Witness, Integer> instruction = IDENTITY.widen(new Identity<>(42));
      FreeAp<IdentityKind.Witness, Integer> freeAp = FreeAp.lift(instruction);
      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(freeAp);

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("Ap with map2 combines independent computations")
    void apWithMap2CombinesComputations() {
      Kind<IdentityKind.Witness, Integer> instrA = IDENTITY.widen(new Identity<>(10));
      Kind<IdentityKind.Witness, Integer> instrB = IDENTITY.widen(new Identity<>(20));

      FreeAp<IdentityKind.Witness, Integer> left = FreeAp.lift(instrA);
      FreeAp<IdentityKind.Witness, Integer> right = FreeAp.lift(instrB);
      FreeAp<IdentityKind.Witness, Integer> combined = left.map2(right, Integer::sum);

      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(combined);

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(30);
    }
  }

  @Nested
  @DisplayName("Ap interpretation with Function (foldMap)")
  class InterpretationFunction {

    @Test
    @DisplayName("Ap with Lift interprets via function-based foldMap")
    void apWithLiftInterpretsFunctionBased() {
      Kind<IdentityKind.Witness, Integer> instruction = IDENTITY.widen(new Identity<>(42));
      FreeAp<IdentityKind.Witness, Integer> freeAp = FreeAp.lift(instruction);
      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(freeAp);

      Function<Kind<IdentityKind.Witness, ?>, Kind<IdentityKind.Witness, ?>> transform =
          kind -> kind;

      Kind<IdentityKind.Witness, Integer> result = program.foldMap(transform, identityMonad);

      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Ap in flatMap chains")
  class FlatMapChains {

    @Test
    @DisplayName("Ap followed by flatMap works correctly")
    void apFollowedByFlatMap() {
      FreeAp<IdentityKind.Witness, Integer> freeAp = FreeAp.pure(10);
      Free<IdentityKind.Witness, Integer> apProgram = new Free.Ap<>(freeAp);
      Free<IdentityKind.Witness, Integer> program = apProgram.flatMap(x -> Free.pure(x * 2));

      Kind<IdentityKind.Witness, Integer> result =
          program.foldMap(Natural.identity(), identityMonad);

      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(20);
    }
  }

  @Nested
  @DisplayName("Ap translation")
  class TranslationTests {

    @Test
    @DisplayName("translate preserves Ap with Pure FreeAp")
    void translatePreservesApPure() {
      FreeAp<IdentityKind.Witness, String> freeAp = FreeAp.pure("hello");
      Free<IdentityKind.Witness, String> program = new Free.Ap<>(freeAp);

      Free<IdentityKind.Witness, String> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      assertThatFree(translated).isAp();

      Kind<IdentityKind.Witness, String> result =
          translated.foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<String>narrow(result).value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("translate preserves Ap with Lift FreeAp")
    void translatePreservesApLift() {
      Kind<IdentityKind.Witness, Integer> instruction = IDENTITY.widen(new Identity<>(42));
      FreeAp<IdentityKind.Witness, Integer> freeAp = FreeAp.lift(instruction);
      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(freeAp);

      Free<IdentityKind.Witness, Integer> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      assertThatFree(translated).isAp();

      Kind<IdentityKind.Witness, Integer> result =
          translated.foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(42);
    }

    @Test
    @DisplayName("translate preserves Ap with map2 FreeAp")
    void translatePreservesApMap2() {
      FreeAp<IdentityKind.Witness, Integer> left = FreeAp.lift(IDENTITY.widen(new Identity<>(10)));
      FreeAp<IdentityKind.Witness, Integer> right = FreeAp.lift(IDENTITY.widen(new Identity<>(20)));
      FreeAp<IdentityKind.Witness, Integer> combined = left.map2(right, Integer::sum);

      Free<IdentityKind.Witness, Integer> program = new Free.Ap<>(combined);

      Free<IdentityKind.Witness, Integer> translated =
          Free.translate(program, Natural.identity(), identityMonad);

      Kind<IdentityKind.Witness, Integer> result =
          translated.foldMap(Natural.identity(), identityMonad);
      assertThat(IDENTITY.<Integer>narrow(result).value()).isEqualTo(30);
    }
  }
}
