// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.optics.free.OpticOpKindHelper.OP;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link OpticOpFunctor} and the continuation each {@link OpticOp} carries. */
@DisplayName("OpticOpFunctor Tests")
class OpticOpFunctorTest {

  record Person(String name, int age) {}

  static final Lens<Person, String> NAME_LENS =
      Lens.of(Person::name, (person, name) -> new Person(name, person.age()));

  static final Person ALICE = new Person("Alice", 25);

  private static final OpticOpFunctor FUNCTOR = OpticOpFunctor.INSTANCE;

  private static Kind<OpticOpKind.Witness, String> getName() {
    return OP.widen(new OpticOp.Get<>(ALICE, NAME_LENS::get, Function.identity()));
  }

  /** Runs a single operation and returns what its continuation gives. */
  @SuppressWarnings("migration-nudge") // lifts one instruction by hand to test the functor
  private static <A> A runOne(Kind<OpticOpKind.Witness, A> op) {
    return OpticInterpreters.direct().run(Free.liftF(op, FUNCTOR));
  }

  @Nested
  @DisplayName("map")
  class MapTests {

    @Test
    @DisplayName("map applies the function to the operation's result")
    void mapAppliesTheFunction() {
      Kind<OpticOpKind.Witness, Integer> mapped = FUNCTOR.map(String::length, getName());

      assertThat(runOne(mapped)).isEqualTo(5);
    }

    @Test
    @DisplayName("map keeps the operation and composes onto its continuation")
    @SuppressWarnings("unchecked") // the get's continuation takes the name, a String
    void mapKeepsTheOperation() {
      Kind<OpticOpKind.Witness, Integer> mapped = FUNCTOR.map(String::length, getName());

      OpticOp<?, Integer> op = OP.narrow(mapped);
      assertThat(op).isInstanceOf(OpticOp.Get.class);
      assertThat(((OpticOp.Get<?, ?, ?>) op).source()).isEqualTo(ALICE);
      assertThat(((Function<Object, ?>) op.k()).apply("Bob")).isEqualTo(3);
    }

    @Test
    @DisplayName("map with identity gives the same result")
    void mapIdentity() {
      assertThat(runOne(FUNCTOR.map(Function.identity(), getName()))).isEqualTo(runOne(getName()));
    }

    @Test
    @DisplayName("map composes")
    void mapComposes() {
      Function<String, Integer> length = String::length;
      Function<Integer, String> describe = n -> n + " letters";

      String twice = runOne(FUNCTOR.map(describe, FUNCTOR.map(length, getName())));
      String once = runOne(FUNCTOR.map(length.andThen(describe), getName()));

      assertThat(twice).isEqualTo(once).isEqualTo("5 letters");
    }

    @Test
    @DisplayName("map rejects a null function")
    void mapRejectsNullFunction() {
      assertThatNullPointerException().isThrownBy(() -> FUNCTOR.map(null, getName()));
    }

    @Test
    @DisplayName("map rejects a null instruction")
    void mapRejectsNullKind() {
      assertThatNullPointerException().isThrownBy(() -> FUNCTOR.map(String::length, null));
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
          OpticPrograms.get(ALICE, NAME_LENS)
              .flatMap(name -> OpticPrograms.set(ALICE, NAME_LENS, name.toUpperCase()));

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
          OpticPrograms.get(ALICE, NAME_LENS)
              .flatMap(name -> OpticPrograms.set(ALICE, NAME_LENS, name + "!"));

      Free<OpticOpKind.Witness, Person> translated =
          Free.translate(program, Natural.identity(), FUNCTOR);

      assertThat(run(translated)).isEqualTo(new Person("Alice!", 25));
    }
  }
}
