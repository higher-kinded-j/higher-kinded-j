// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.optics.free.OpticOpKindHelper.OP;
import static org.higherkindedj.optics.free.OpticOpLawFixtures.ALICE;
import static org.higherkindedj.optics.free.OpticOpLawFixtures.NAME;
import static org.higherkindedj.optics.free.OpticOpLawFixtures.getName;

import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.higherkindedj.optics.free.OpticOpLawFixtures.Person;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link OpticOpFunctor} and the continuation each {@link OpticOp} carries.
 *
 * <p>The laws are driven by the shipped {@link FunctorLaws} over one operation of each kind in
 * {@link OpticOpLawFixtures}.
 */
@DisplayName("OpticOpFunctor Tests")
class OpticOpFunctorTest {

  private static final OpticOpFunctor FUNCTOR = OpticOpFunctor.INSTANCE;

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawTests {
    final Function<Object, String> f = Object::toString;
    final Function<String, String> g = s -> s + "!";

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.optics.free.OpticOpLawFixtures#kinds")
    void identity(String label, Kind<OpticOpKind.Witness, Object> fa) {
      FunctorLaws.assertIdentity(FUNCTOR, fa, OpticOpLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.optics.free.OpticOpLawFixtures#kinds")
    void composition(String label, Kind<OpticOpKind.Witness, Object> fa) {
      FunctorLaws.assertComposition(FUNCTOR, fa, f, g, OpticOpLawFixtures.EQ);
    }
  }

  /**
   * {@code map} only composes onto the continuation, so a throwing function does not surface until
   * the operation runs, and {@link Category#EXCEPTIONS} is left out. The Functor laws are verified
   * in the {@code FunctorLawTests} block above.
   */
  @Test
  @DisplayName("Functor contract: operations and validations")
  void functorContract() {
    TypeClassContract.<OpticOpKind.Witness>functor(OpticOpFunctor.class)
        .<String>instance(FUNCTOR)
        .<Integer>withKind(getName())
        .withMapper(String::length)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("Continuation")
  class ContinuationTests {

    @Test
    @DisplayName("map applies the function to what the operation produces")
    void mapAppliesTheFunction() {
      assertThat(OpticOpLawFixtures.run(FUNCTOR.map(String::length, getName()))).isEqualTo(5);
    }

    @Test
    @DisplayName("a null from the mapped function fails when the operation runs, as in Free.map")
    void nullResultFailsWhenRun() {
      Kind<OpticOpKind.Witness, String> mapped = FUNCTOR.map(name -> null, getName());

      assertThatNullPointerException()
          .isThrownBy(() -> OpticOpLawFixtures.run(mapped))
          .withMessage("f must not return null");
    }

    @ParameterizedTest(name = "{0} rejects a null continuation")
    @MethodSource("org.higherkindedj.optics.free.OpticOpLawFixtures#withoutContinuation")
    void recordRejectsNullContinuation(String label, ThrowingCallable construct) {
      assertThatNullPointerException().isThrownBy(construct).withMessageContaining("k");
    }
  }

  @Nested
  @DisplayName("Interpreting")
  class InterpretingTests {

    /** Handles get and set the way an @EffectAlgebra handler does: the result goes to op.k(). */
    private final Natural<OpticOpKind.Witness, IdKind.Witness> handler =
        new Natural<>() {
          @Override
          public <A> Kind<IdKind.Witness, A> apply(Kind<OpticOpKind.Witness, A> fa) {
            return switch (OP.narrow(fa)) {
              case OpticOp.Get<?, ?, A> get -> Id.of(get(get));
              case OpticOp.Set<?, ?, A> set -> Id.of(set(set));
              default -> throw new UnsupportedOperationException();
            };
          }
        };

    private static <S, A, R> R get(OpticOp.Get<S, A, R> op) {
      return op.k().apply(op.optic().get(op.source()));
    }

    private static <S, A, R> R set(OpticOp.Set<S, A, R> op) {
      return op.k().apply(op.optic().set(op.newValue(), op.source()));
    }

    private <A> A run(Free<OpticOpKind.Witness, A> program) {
      return IdKindHelper.ID.narrow(program.foldMap(handler, IdMonad.instance())).value();
    }

    @Test
    @DisplayName("an interpreter that hands each result to the continuation runs a program")
    void continuationInterpreterRunsAProgram() {
      Free<OpticOpKind.Witness, Person> program =
          OpticPrograms.get(ALICE, NAME)
              .flatMap(name -> OpticPrograms.set(ALICE, NAME, name.toUpperCase()));

      assertThat(run(program)).isEqualTo(new Person("ALICE", 25));
    }

    @Test
    @DisplayName("a lifted operation that was mapped runs through the same interpreter")
    @SuppressWarnings("migration-nudge") // lifts one instruction by hand to test the functor
    void mappedOperationRuns() {
      Free<OpticOpKind.Witness, Integer> program =
          Free.liftF(FUNCTOR.map(String::length, getName()), FUNCTOR);

      assertThat(run(program)).isEqualTo(5);
    }

    @Test
    @DisplayName("a translated program runs through the same interpreter")
    void translatedProgramRuns() {
      Free<OpticOpKind.Witness, Person> program =
          OpticPrograms.get(ALICE, NAME)
              .flatMap(name -> OpticPrograms.set(ALICE, NAME, name + "!"));

      Free<OpticOpKind.Witness, Person> translated =
          Free.translate(program, Natural.identity(), FUNCTOR);

      assertThat(run(translated)).isEqualTo(new Person("Alice!", 25));
    }
  }
}
