// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import static org.higherkindedj.optics.free.OpticOpKindHelper.OP;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code OpticOpFunctor} tests: one operation of each of the ten kinds.
 *
 * <p>Referenced from the {@code *LawTests} block via a fully-qualified {@code @MethodSource}. An
 * operation holds a continuation it has not run, so equality runs both sides through {@link
 * DirectOpticInterpreter} and compares what they produce. It also asks for the same record, so a
 * {@code mapK} that rebuilds the wrong operation fails.
 */
@SuppressWarnings({"unused", "migration-nudge"}) // @MethodSource; each op is lifted by hand to run
final class OpticOpLawFixtures {

  private OpticOpLawFixtures() {}

  record Person(String name, int age) {}

  record Team(String name, List<Person> members) {}

  static final Lens<Person, String> NAME =
      Lens.of(Person::name, (person, name) -> new Person(name, person.age()));

  static final Lens<Team, List<Person>> MEMBERS =
      Lens.of(Team::members, (team, members) -> new Team(team.name(), members));

  static final Traversal<Team, String> MEMBER_NAMES =
      MEMBERS.asTraversal().andThen(Traversals.forList()).andThen(NAME.asTraversal());

  static final Fold<Team, String> MEMBER_NAMES_FOLD =
      Fold.of(team -> team.members().stream().map(Person::name).toList());

  static final Person ALICE = new Person("Alice", 25);

  static final Team TEAM = new Team("Core", List.of(ALICE, new Person("Bob", 31)));

  /** Same record, and the same result once both are run. */
  static final BiPredicate<Kind<OpticOpKind.Witness, ?>, Kind<OpticOpKind.Witness, ?>> EQ =
      (k1, k2) -> k1.getClass() == k2.getClass() && Objects.equals(run(k1), run(k2));

  static Object run(Kind<OpticOpKind.Witness, ?> op) {
    return OpticInterpreters.direct().run(Free.liftF(op, OpticOpFunctor.INSTANCE));
  }

  /** A get of {@code ALICE}'s name, for the tests that need one operation. */
  static Kind<OpticOpKind.Witness, String> getName() {
    return OP.widen(new OpticOp.Get<>(ALICE, NAME::get, Function.identity()));
  }

  /** One operation of each kind, each with the identity continuation. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Get", getName()),
        Arguments.of(
            "Preview",
            OP.widen(new OpticOp.Preview<>(TEAM, MEMBER_NAMES_FOLD, Function.identity()))),
        Arguments.of(
            "GetAll", OP.widen(new OpticOp.GetAll<>(TEAM, MEMBER_NAMES_FOLD, Function.identity()))),
        Arguments.of("Set", OP.widen(new OpticOp.Set<>(ALICE, NAME, "Bob", Function.identity()))),
        Arguments.of(
            "SetAll",
            OP.widen(new OpticOp.SetAll<>(TEAM, MEMBER_NAMES, "Anon", Function.identity()))),
        Arguments.of(
            "Modify",
            OP.widen(new OpticOp.Modify<>(ALICE, NAME, String::toUpperCase, Function.identity()))),
        Arguments.of(
            "ModifyAll",
            OP.widen(
                new OpticOp.ModifyAll<>(
                    TEAM, MEMBER_NAMES, String::toUpperCase, Function.identity()))),
        Arguments.of(
            "Exists",
            OP.widen(
                new OpticOp.Exists<>(TEAM, MEMBER_NAMES_FOLD, "Bob"::equals, Function.identity()))),
        Arguments.of(
            "All",
            OP.widen(
                new OpticOp.All<>(
                    TEAM, MEMBER_NAMES_FOLD, name -> !name.isEmpty(), Function.identity()))),
        Arguments.of(
            "Count", OP.widen(new OpticOp.Count<>(TEAM, MEMBER_NAMES_FOLD, Function.identity()))));
  }

  /** Each kind built with a null continuation, which its constructor rejects. */
  static Stream<Arguments> withoutContinuation() {
    return Stream.of(
        Arguments.of("Get", (ThrowingCallable) () -> new OpticOp.Get<>(ALICE, NAME::get, null)),
        Arguments.of(
            "Preview",
            (ThrowingCallable) () -> new OpticOp.Preview<>(TEAM, MEMBER_NAMES_FOLD, null)),
        Arguments.of(
            "GetAll", (ThrowingCallable) () -> new OpticOp.GetAll<>(TEAM, MEMBER_NAMES_FOLD, null)),
        Arguments.of("Set", (ThrowingCallable) () -> new OpticOp.Set<>(ALICE, NAME, "Bob", null)),
        Arguments.of(
            "SetAll",
            (ThrowingCallable) () -> new OpticOp.SetAll<>(TEAM, MEMBER_NAMES, "Anon", null)),
        Arguments.of(
            "Modify",
            (ThrowingCallable) () -> new OpticOp.Modify<>(ALICE, NAME, String::toUpperCase, null)),
        Arguments.of(
            "ModifyAll",
            (ThrowingCallable)
                () -> new OpticOp.ModifyAll<>(TEAM, MEMBER_NAMES, String::toUpperCase, null)),
        Arguments.of(
            "Exists",
            (ThrowingCallable)
                () -> new OpticOp.Exists<>(TEAM, MEMBER_NAMES_FOLD, "Bob"::equals, null)),
        Arguments.of(
            "All",
            (ThrowingCallable)
                () -> new OpticOp.All<>(TEAM, MEMBER_NAMES_FOLD, name -> !name.isEmpty(), null)),
        Arguments.of(
            "Count", (ThrowingCallable) () -> new OpticOp.Count<>(TEAM, MEMBER_NAMES_FOLD, null)));
  }
}
