// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.expression;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: ForPath Parallel Composition - Expressing Independent Computations
 *
 * <p>In this tutorial, you'll learn how {@code ForPath.par()} lets you compose independent
 * computations using applicative semantics. Where {@code .from()} chains sequentially via {@code
 * flatMap}, {@code par()} declares that computations are independent of each other.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Static {@code par()} entry points for combining two or three independent Paths
 *   <li>Short-circuit behaviour with MaybePath and EitherPath
 *   <li>True parallel execution with VTaskPath on virtual threads
 *   <li>Chaining sequential operations after {@code par()}
 *   <li>The difference between applicative ({@code par}) and monadic ({@code from}) composition
 * </ul>
 *
 * <p>Replace each {@code answerRequired()} call with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: ForPath Parallel Composition")
public class Tutorial02_ForPathParallel {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // =========================================================================
  // Exercise 1: Basic par() with MaybePath
  // =========================================================================

  /**
   * Exercise 1: Combine two independent MaybePath values using par().
   *
   * <p>The {@code ForPath.par()} method takes two independent Path computations and combines them
   * using applicative semantics. Neither computation depends on the other's result.
   *
   * <p>Task: Use {@code ForPath.par()} to combine a name and an age, then yield a greeting string.
   */
  @Test
  @DisplayName("Exercise 1: Basic par(2) with MaybePath")
  void exercise1_basicParWithMaybePath() {
    // TODO: Replace answerRequired() with ForPath.par(Path.just("Alice"), Path.just(30))
    //       .yield((name, age) -> name + " is " + age)
    // Hint: ForPath.par() takes two Path values directly
    MaybePath<String> result = answerRequired();

    assertThat(result.getOrElse("nothing")).isEqualTo("Alice is 30");
  }

  // =========================================================================
  // Exercise 2: Short-circuit behaviour
  // =========================================================================

  /**
   * Exercise 2: Observe short-circuit behaviour when one computation fails.
   *
   * <p>When any computation in a {@code par()} fails (returns Nothing for MaybePath), the entire
   * result fails. This matches applicative semantics: all inputs must succeed.
   *
   * <p>Task: Use {@code ForPath.par()} where the second computation is Nothing. Verify the result
   * is also Nothing.
   */
  @Test
  @DisplayName("Exercise 2: par() short-circuits on Nothing")
  void exercise2_parShortCircuits() {
    // TODO: Replace answerRequired() with ForPath.par(Path.just("Bob"), Path.<Integer>nothing())
    //       .yield((name, age) -> name + " is " + age)
    // Hint: Use Path.<Integer>nothing() for the second argument
    MaybePath<String> result = answerRequired();

    assertThat(result.getOrElse("nothing")).isEqualTo("nothing");
  }

  // =========================================================================
  // Exercise 3: par(3) with three independent computations
  // =========================================================================

  /**
   * Exercise 3: Combine three independent MaybePath computations.
   *
   * <p>{@code ForPath.par()} also accepts three arguments, combining them with {@code map3}.
   *
   * <p>Task: Use {@code ForPath.par()} with three values and yield their sum.
   */
  @Test
  @DisplayName("Exercise 3: par(3) combines three values")
  void exercise3_parThreeValues() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(Path.just(10), Path.just(20), Path.just(30))
    //           .yield((a, b, c) -> a + b + c)
    // Hint: The yield function receives all three values as separate parameters
    MaybePath<Integer> result = answerRequired();

    assertThat(result.getOrElse(-1)).isEqualTo(60);
  }

  // =========================================================================
  // Exercise 4: EitherPath par() with error propagation
  // =========================================================================

  /**
   * Exercise 4: Combine independent EitherPath computations.
   *
   * <p>For EitherPath, {@code par()} propagates the first error encountered. When all computations
   * succeed, the values are combined.
   *
   * <p>Task: Use {@code ForPath.par()} with two Right values and yield a combined string.
   */
  @Test
  @DisplayName("Exercise 4: EitherPath par(2) combines Right values")
  void exercise4_eitherPathPar() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(Path.<String, String>right("Alice"), Path.<String, Integer>right(42))
    //           .yield((name, age) -> name + " is " + age)
    // Hint: Use Path.<String, String>right(...) for type-safe EitherPath creation
    EitherPath<String, String> result = answerRequired();

    assertThat(result.run().getRight()).isEqualTo("Alice is 42");
  }

  // =========================================================================
  // Exercise 5: IdPath par() with Id.of()
  // =========================================================================

  /**
   * Exercise 5: Combine IdPath values using par().
   *
   * <p>IdPath wraps simple values in the Id monad. Note that {@code Path.idPath()} requires an
   * {@code Id<A>} value, not a raw value.
   *
   * <p>Task: Use {@code ForPath.par()} with three IdPath values and yield their sum.
   */
  @Test
  @DisplayName("Exercise 5: IdPath par(3) with Id.of()")
  void exercise5_idPathPar() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(Path.idPath(Id.of(10)), Path.idPath(Id.of(20)), Path.idPath(Id.of(30)))
    //           .yield((a, b, c) -> a + b + c)
    // Hint: Path.idPath() takes Id<A>, not a raw value — wrap with Id.of()
    IdPath<Integer> result = answerRequired();

    assertThat(result.run().value()).isEqualTo(60);
  }

  // =========================================================================
  // Exercise 6: IOPath par()
  // =========================================================================

  /**
   * Exercise 6: Combine two independent IO computations using par().
   *
   * <p>IOPath computations are lazy; nothing executes until {@code unsafeRun()} is called. The
   * {@code par()} combinator composes two lazy computations into one.
   *
   * <p>Task: Use {@code ForPath.par()} with two IO computations and yield a combined string.
   */
  @Test
  @DisplayName("Exercise 6: IOPath par(2) combines deferred computations")
  void exercise6_ioPathPar() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(Path.io(() -> "hello"), Path.io(() -> "world"))
    //           .yield((a, b) -> a + " " + b)
    // Hint: Path.io(() -> value) creates a lazy IOPath
    IOPath<String> result = answerRequired();

    assertThat(result.unsafeRun()).isEqualTo("hello world");
  }

  // =========================================================================
  // Exercise 7: VTaskPath par() — true parallelism
  // =========================================================================

  /**
   * Exercise 7: Use VTaskPath par() for truly parallel execution.
   *
   * <p>VTaskPath is the only Path type where {@code par()} executes computations concurrently on
   * virtual threads via {@code Par.map2}. This exercise verifies that two computations that each
   * sleep for 100ms complete in approximately 100ms total (not 200ms).
   *
   * <p>Task: Use {@code ForPath.par()} with two VTaskPath computations that each sleep and return a
   * value.
   */
  @Test
  @DisplayName("Exercise 7: VTaskPath par(2) executes in parallel")
  void exercise7_vtaskPathParallel() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(
    //               Path.vtaskPath(() -> { Thread.sleep(100); return "A"; }),
    //               Path.vtaskPath(() -> { Thread.sleep(100); return "B"; }))
    //           .yield((a, b) -> a + b)
    // Hint: Path.vtaskPath(callable) creates a VTaskPath
    VTaskPath<String> result = answerRequired();

    long start = System.nanoTime();
    String value = result.run().run();
    long elapsed = (System.nanoTime() - start) / 1_000_000; // ms

    assertThat(value).isEqualTo("AB");
    // If truly parallel, both complete in ~100ms, not ~200ms
    assertThat(elapsed).isLessThan(180);
  }

  // =========================================================================
  // Exercise 8: Chaining after par()
  // =========================================================================

  /**
   * Exercise 8: Chain sequential operations after par().
   *
   * <p>The result of {@code par()} is a regular comprehension step, so you can chain {@code
   * .let()}, {@code .from()}, or {@code .when()} after it. This allows mixing parallel and
   * sequential composition in a single workflow.
   *
   * <p>Task: Use {@code ForPath.par()} to combine two values, then use {@code .let()} to compute a
   * derived value, and yield the final result.
   */
  @Test
  @DisplayName("Exercise 8: Chain let() after par()")
  void exercise8_chainAfterPar() {
    // TODO: Replace answerRequired() with
    //       ForPath.par(Path.just("Alice"), Path.just(5))
    //           .let(t -> t._1() + " has " + t._2() + " letters")
    //           .yield((name, len, sentence) -> sentence.toUpperCase())
    // Hint: After par(), use .let(t -> ...) where t is a Tuple2 of the par'd values
    MaybePath<String> result = answerRequired();

    assertThat(result.getOrElse("nothing")).isEqualTo("ALICE HAS 5 LETTERS");
  }

  // =========================================================================
  // Exercise 9: par() vs from() — understanding the difference
  // =========================================================================

  /**
   * Exercise 9: Understand the difference between par() and from().
   *
   * <p>With {@code from()}, the second computation can depend on the first value (monadic). With
   * {@code par()}, both computations are independent (applicative). This exercise asks you to use
   * {@code par()} for independent computations and observe that both results are available in
   * yield.
   *
   * <p>Task: Create the same result using par() — combine Path.just(10) and Path.just(20), then
   * yield the product.
   */
  @Test
  @DisplayName("Exercise 9: par() for independent computations")
  void exercise9_parVsFrom() {
    // The sequential version (for comparison):
    MaybePath<Integer> sequential =
        ForPath.from(Path.just(10)).from(a -> Path.just(20)).yield((a, b) -> a * b);

    // TODO: Replace answerRequired() with the parallel version using ForPath.par()
    // Hint: ForPath.par(Path.just(10), Path.just(20)).yield((a, b) -> a * b)
    MaybePath<Integer> parallel = answerRequired();

    // Both should produce the same result
    assertThat(sequential.getOrElse(-1)).isEqualTo(200);
    assertThat(parallel.getOrElse(-1)).isEqualTo(200);
  }

  /**
   * Congratulations! You've completed Tutorial 02: ForPath Parallel Composition 🎉
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use {@code ForPath.par()} to combine independent computations
   *   <li>Short-circuit behaviour when one computation fails
   *   <li>The difference between par(2) and par(3) arities
   *   <li>How VTaskPath achieves true parallel execution on virtual threads
   *   <li>Chaining sequential operations after parallel composition
   *   <li>The semantic difference between {@code par()} (applicative) and {@code from()} (monadic)
   * </ul>
   *
   * <p>Next: Explore the full For comprehension API in the documentation, or try the ForState
   * tutorial for named state workflows.
   */
}
