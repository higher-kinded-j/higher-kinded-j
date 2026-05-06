// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.free_ap.FreeApKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial10 FreeApplicative — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial10_FreeApplicative_Solution {

  // Helper for interpreting with Id
  private static final IdMonad ID_APP = IdMonad.instance();
  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY = Natural.identity();

  /**
   * Why this is idiomatic: {@code FreeAp.pure(42)} is the empty program — a value lifted into the
   * free applicative without referencing any underlying functor. {@code foldMap} with identity
   * gives the value back unchanged.
   *
   * <p>Alternative: skip {@code FreeAp} for pure values and use the value directly. The {@code
   * FreeAp.pure} form earns its keep when the value will be combined with effectful operations
   * later via {@code map2}.
   *
   * <p>Common wrong attempt: trying to {@code foldMap} without supplying both a natural
   * transformation and a target applicative. Both are mandatory — the natural transformation names
   * the interpreter, the applicative says how to combine its results.
   */
  @Test
  void exercise1_pure() {
    // SOLUTION: Create a FreeAp containing the value 42
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(42);

    // Interpret to get the value
    Kind<IdKind.Witness, Integer> result = pureValue.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(42);
  }

  /**
   * Why this is idiomatic: {@code FreeAp.map} composes a pure function into the program without
   * ever interpreting it. The transformation is recorded in the AST and applied during {@code
   * foldMap}.
   *
   * <p>Alternative: pre-compute the value and {@code FreeAp.pure(50)} directly. Equivalent for a
   * pure program; pointless work, but it would still typecheck. {@code map} keeps the computation
   * symbolic.
   *
   * <p>Common wrong attempt: side-effecting inside the {@code map} function (printing, logging).
   * The free applicative is meant to be inspectable; effectful arrows make the "describe now,
   * interpret later" guarantee leaky.
   */
  @Test
  void exercise2_map() {
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(10);

    // SOLUTION: Map the value to multiply by 5
    FreeAp<IdKind.Witness, Integer> mapped = pureValue.map(x -> x * 5);

    Kind<IdKind.Witness, Integer> result = mapped.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(50);
  }

  /**
   * Why this is idiomatic: {@code map2} combines two independent {@code FreeAp} programs with a
   * binary function — exactly the applicative shape. Neither sub-program depends on the other's
   * result, so the interpreter is free to run them in parallel.
   *
   * <p>Alternative: {@code applicative.ap(name.map(n -> a -> ...), age)}. Same outcome, less
   * readable; {@code map2} is the named pattern for "combine two with a binary function".
   *
   * <p>Common wrong attempt: chain via {@code flatMap} when independence holds. Free monads
   * sequence — they assume each step depends on the previous. Choose the free <em>applicative</em>
   * for parallelisable steps; the monad for sequential ones.
   */
  @Test
  void exercise3_map2() {
    FreeAp<IdKind.Witness, String> name = FreeAp.pure("Alice");
    FreeAp<IdKind.Witness, Integer> age = FreeAp.pure(30);

    // SOLUTION: Combine name and age into a greeting
    FreeAp<IdKind.Witness, String> combined =
        name.map2(age, (n, a) -> n + " is " + a + " years old");

    Kind<IdKind.Witness, String> result = combined.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("Alice is 30 years old");
  }

  /**
   * Why this is idiomatic: a left-leaning chain of {@code map2}s sums three independent programs.
   * The interpreter sees a balanced AST it can flatten, parallelise, or analyse before executing.
   *
   * <p>Alternative: {@code applicative.map3(a, b, c, (x, y, z) -> x + y + z)}. Tighter in the
   * source; the {@code map2} chain shown here generalises trivially to {@code map4}, {@code map5},
   * and beyond without extra overloads.
   *
   * <p>Common wrong attempt: nest the map2s incorrectly ({@code a.map2(b.map2(c, ...), ...)}) and
   * miscount the arguments. The associativity is fine either way, but the lambda arities have to
   * line up; trace the types through each {@code map2} call.
   */
  @Test
  void exercise4_multipleMap2() {
    FreeAp<IdKind.Witness, Integer> a = FreeAp.pure(10);
    FreeAp<IdKind.Witness, Integer> b = FreeAp.pure(20);
    FreeAp<IdKind.Witness, Integer> c = FreeAp.pure(30);

    // SOLUTION: Sum all three values using map2 chains
    FreeAp<IdKind.Witness, Integer> sum = a.map2(b, Integer::sum).map2(c, Integer::sum);

    Kind<IdKind.Witness, Integer> result = sum.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(60);
  }

  /**
   * Why this is idiomatic: {@code fetchUser} and {@code fetchPosts} are described as independent
   * programs; combining them with {@code map2} states "build the dashboard from both" without
   * imposing an order. This is the free applicative's defining advantage — independence preserved
   * in the AST.
   *
   * <p>Alternative: a free monad chain {@code fetchUser.flatMap(u -> fetchPosts.map(p -> ...))}.
   * Equivalent runtime answer; the AST now records a sequential dependency that an interpreter
   * cannot parallelise.
   *
   * <p>Common wrong attempt: phrase the combination as "fetch user, then fetch posts with the
   * user's id". The moment the second program depends on the first, the free applicative is the
   * wrong tool — switch to a free monad.
   */
  @Test
  void exercise5_independenceUnderstanding() {
    // These represent "fetching" operations
    FreeAp<IdKind.Witness, String> fetchUser = FreeAp.pure("User{id=1}");
    FreeAp<IdKind.Witness, String> fetchPosts = FreeAp.pure("Posts{count=5}");

    // SOLUTION: Combine them - fetchPosts does NOT depend on fetchUser's result
    FreeAp<IdKind.Witness, String> dashboard =
        fetchUser.map2(fetchPosts, (user, posts) -> user + " has " + posts);

    Kind<IdKind.Witness, String> result = dashboard.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("User{id=1} has Posts{count=5}");
  }

  /**
   * Why this is idiomatic: {@code applicative.map3(a, b, c, Person::new)} is the canonical "build a
   * record from three independent fetches" idiom. The constructor reference makes the wiring
   * explicit; each component is computed once.
   *
   * <p>Alternative: chain two {@code map2}s and one {@code map}. Same shape; {@code map3} is the
   * named, single-call version.
   *
   * <p>Common wrong attempt: assemble the {@code Person} inside a {@code flatMap} chain, implying
   * sequential fetch. The applicative form preserves the parallelisable structure all the way to
   * the interpreter.
   */
  @Test
  void exercise6_buildRecord() {
    record Person(String name, int age, String city) {}

    FreeAp<IdKind.Witness, String> fetchName = FreeAp.pure("Bob");
    FreeAp<IdKind.Witness, Integer> fetchAge = FreeAp.pure(25);
    FreeAp<IdKind.Witness, String> fetchCity = FreeAp.pure("London");

    // SOLUTION: Combine all three into a Person using map3
    FreeApApplicative<IdKind.Witness> applicative = FreeApApplicative.instance();
    Kind<FreeApKind.Witness<IdKind.Witness>, Person> personKind =
        applicative.map3(
            FREE_AP.widen(fetchName),
            FREE_AP.widen(fetchAge),
            FREE_AP.widen(fetchCity),
            Person::new);
    FreeAp<IdKind.Witness, Person> person = FREE_AP.narrow(personKind);

    Kind<IdKind.Witness, Person> result = person.foldMap(IDENTITY, ID_APP);
    Person p = ID.narrow(result).value();

    assertThat(p.name()).isEqualTo("Bob");
    assertThat(p.age()).isEqualTo(25);
    assertThat(p.city()).isEqualTo("London");
  }
}
