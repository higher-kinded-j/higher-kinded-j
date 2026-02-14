// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 10: Free Applicative
 *
 * <p>Free Applicative captures independent computations that can potentially run in parallel. This
 * contrasts with Free Monad, which captures sequential, dependent computations.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>FreeAp.pure: Lift a value into Free Applicative
 *   <li>FreeAp.lift: Suspend an instruction
 *   <li>map2: Combine two independent computations
 *   <li>foldMap: Interpret using a natural transformation
 * </ul>
 *
 * <p>Links to documentation: <a
 * href="https://higher-kinded-j.github.io/latest/monads/free_applicative.html">Free Applicative
 * Guide</a>
 */
public class Tutorial10_FreeApplicative {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // Helper for interpreting with Id
  private static final IdMonad ID_APP = IdMonad.instance();
  private static final Natural<IdKind.Witness, IdKind.Witness> IDENTITY = Natural.identity();

  /**
   * Exercise 1: Creating pure values
   *
   * <p>FreeAp.pure lifts a value into the Free Applicative context.
   *
   * <p>Task: Create a pure FreeAp value
   */
  @Test
  void exercise1_pure() {
    // TODO: Create a FreeAp containing the value 42
    // Hint: Use FreeAp.pure(42)
    FreeAp<IdKind.Witness, Integer> pureValue = answerRequired();

    // Interpret to get the value
    Kind<IdKind.Witness, Integer> result = pureValue.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(42);
  }

  /**
   * Exercise 2: Mapping over FreeAp
   *
   * <p>FreeAp supports map, allowing transformation of the result value.
   *
   * <p>Task: Map over a FreeAp to transform its value
   */
  @Test
  void exercise2_map() {
    FreeAp<IdKind.Witness, Integer> pureValue = FreeAp.pure(10);

    // TODO: Map the value to multiply by 5
    // Hint: pureValue.map(x -> x * 5)
    FreeAp<IdKind.Witness, Integer> mapped = answerRequired();

    Kind<IdKind.Witness, Integer> result = mapped.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(50);
  }

  /**
   * Exercise 3: Combining independent computations
   *
   * <p>map2 combines two FreeAp values. The key insight: neither computation depends on the other's
   * result.
   *
   * <p>Task: Combine two independent values
   */
  @Test
  void exercise3_map2() {
    FreeAp<IdKind.Witness, String> name = FreeAp.pure("Alice");
    FreeAp<IdKind.Witness, Integer> age = FreeAp.pure(30);

    // TODO: Combine name and age into a greeting
    // Hint: name.map2(age, (n, a) -> n + " is " + a + " years old")
    FreeAp<IdKind.Witness, String> combined = answerRequired();

    Kind<IdKind.Witness, String> result = combined.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("Alice is 30 years old");
  }

  /**
   * Exercise 4: Chaining map2 for multiple values
   *
   * <p>You can chain map2 to combine multiple independent computations.
   *
   * <p>Task: Combine three independent values
   */
  @Test
  void exercise4_multipleMap2() {
    FreeAp<IdKind.Witness, Integer> a = FreeAp.pure(10);
    FreeAp<IdKind.Witness, Integer> b = FreeAp.pure(20);
    FreeAp<IdKind.Witness, Integer> c = FreeAp.pure(30);

    // TODO: Sum all three values using map2 chains
    // Hint: a.map2(b, Integer::sum).map2(c, Integer::sum)
    // Or: a.map2(b.map2(c, Integer::sum), Integer::sum)
    FreeAp<IdKind.Witness, Integer> sum = answerRequired();

    Kind<IdKind.Witness, Integer> result = sum.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo(60);
  }

  /**
   * Exercise 5: Understanding independence
   *
   * <p>The key difference from Free Monad: with map2, neither computation uses the other's result.
   *
   * <p>Task: Identify why this enables parallelism
   */
  @Test
  void exercise5_independenceUnderstanding() {
    // These represent "fetching" operations
    FreeAp<IdKind.Witness, String> fetchUser = FreeAp.pure("User{id=1}");
    FreeAp<IdKind.Witness, String> fetchPosts = FreeAp.pure("Posts{count=5}");

    // TODO: Combine them. Note: fetchPosts does NOT depend on fetchUser's result!
    // Hint: fetchUser.map2(fetchPosts, (user, posts) -> user + " has " + posts)
    FreeAp<IdKind.Witness, String> dashboard = answerRequired();

    Kind<IdKind.Witness, String> result = dashboard.foldMap(IDENTITY, ID_APP);

    assertThat(ID.narrow(result).value()).isEqualTo("User{id=1} has Posts{count=5}");

    // Key insight: A smart interpreter could execute fetchUser and fetchPosts
    // in parallel because neither needs the other's result.
    // With Free Monad's flatMap, this would be sequential:
    //   fetchUser.flatMap(user -> fetchPosts.map(posts -> user + " has " + posts))
    // Even though fetchPosts doesn't use 'user', flatMap forces sequencing.
  }

  /**
   * Exercise 6: Building a record with map2
   *
   * <p>A common pattern: combine multiple independent fetches into a record.
   *
   * <p>Task: Build a Person record from independent values
   */
  @Test
  void exercise6_buildRecord() {
    record Person(String name, int age, String city) {}

    FreeAp<IdKind.Witness, String> fetchName = FreeAp.pure("Bob");
    FreeAp<IdKind.Witness, Integer> fetchAge = FreeAp.pure(25);
    FreeAp<IdKind.Witness, String> fetchCity = FreeAp.pure("London");

    // TODO: Combine all three into a Person
    // Hint: Chain map2 calls, building up the record step by step
    // fetchName.map2(fetchAge, (name, age) -> ...).map2(fetchCity, ...)
    FreeAp<IdKind.Witness, Person> person = answerRequired();

    Kind<IdKind.Witness, Person> result = person.foldMap(IDENTITY, ID_APP);
    Person p = ID.narrow(result).value();

    assertThat(p.name()).isEqualTo("Bob");
    assertThat(p.age()).isEqualTo(25);
    assertThat(p.city()).isEqualTo("London");
  }

  /**
   * Congratulations! You've completed Tutorial 10: Free Applicative
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>FreeAp.pure lifts values into the Free Applicative
   *   <li>map transforms the result value
   *   <li>map2 combines independent computations
   *   <li>Neither computation in map2 depends on the other
   *   <li>This independence enables parallel execution
   *   <li>foldMap interprets using a natural transformation
   * </ul>
   *
   * <p>Key difference from Free Monad:
   *
   * <ul>
   *   <li>Free Monad (flatMap): Sequential, dependent computations
   *   <li>Free Applicative (map2): Independent, potentially parallel
   * </ul>
   *
   * <p>You've completed the Core Types tutorial track!
   */
}
