// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.id.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 02: ForPath Parallel Composition - Expressing Independent Computations (SOLUTIONS)
 *
 * <p>This file contains the complete solutions for Tutorial 02. Each exercise shows the correct
 * implementation with an explanatory SOLUTION comment.
 */
@DisplayName("Tutorial 02: ForPath Parallel Composition (Solutions)")
public class Tutorial02_ForPathParallel_Solution {

  // =========================================================================
  // Exercise 1: Basic par() with MaybePath
  // =========================================================================

  @Test
  @DisplayName("Exercise 1: Basic par(2) with MaybePath")
  void exercise1_basicParWithMaybePath() {
    // SOLUTION: ForPath.par() takes two independent Path values and combines them.
    // The yield function receives both values as separate parameters.
    MaybePath<String> result =
        ForPath.par(Path.just("Alice"), Path.just(30)).yield((name, age) -> name + " is " + age);

    assertThat(result.getOrElse("nothing")).isEqualTo("Alice is 30");
  }

  // =========================================================================
  // Exercise 2: Short-circuit behaviour
  // =========================================================================

  @Test
  @DisplayName("Exercise 2: par() short-circuits on Nothing")
  void exercise2_parShortCircuits() {
    // SOLUTION: When any computation in par() returns Nothing, the entire result
    // is Nothing. This is applicative semantics: all inputs must succeed.
    MaybePath<String> result =
        ForPath.par(Path.just("Bob"), Path.<Integer>nothing())
            .yield((name, age) -> name + " is " + age);

    assertThat(result.getOrElse("nothing")).isEqualTo("nothing");
  }

  // =========================================================================
  // Exercise 3: par(3) with three independent computations
  // =========================================================================

  @Test
  @DisplayName("Exercise 3: par(3) combines three values")
  void exercise3_parThreeValues() {
    // SOLUTION: par() also accepts three arguments, using map3 under the hood.
    MaybePath<Integer> result =
        ForPath.par(Path.just(10), Path.just(20), Path.just(30)).yield((a, b, c) -> a + b + c);

    assertThat(result.getOrElse(-1)).isEqualTo(60);
  }

  // =========================================================================
  // Exercise 4: EitherPath par() with error propagation
  // =========================================================================

  @Test
  @DisplayName("Exercise 4: EitherPath par(2) combines Right values")
  void exercise4_eitherPathPar() {
    // SOLUTION: Use type witnesses on Path.right() to specify the error type.
    // EitherPath par() propagates the first Left encountered.
    EitherPath<String, String> result =
        ForPath.par(Path.<String, String>right("Alice"), Path.<String, Integer>right(42))
            .yield((name, age) -> name + " is " + age);

    assertThat(result.run().getRight()).isEqualTo("Alice is 42");
  }

  // =========================================================================
  // Exercise 5: IdPath par() with Id.of()
  // =========================================================================

  @Test
  @DisplayName("Exercise 5: IdPath par(3) with Id.of()")
  void exercise5_idPathPar() {
    // SOLUTION: Path.idPath() requires Id<A>, not a raw value. Use Id.of() to wrap.
    IdPath<Integer> result =
        ForPath.par(Path.idPath(Id.of(10)), Path.idPath(Id.of(20)), Path.idPath(Id.of(30)))
            .yield((a, b, c) -> a + b + c);

    assertThat(result.run().value()).isEqualTo(60);
  }

  // =========================================================================
  // Exercise 6: IOPath par()
  // =========================================================================

  @Test
  @DisplayName("Exercise 6: IOPath par(2) combines deferred computations")
  void exercise6_ioPathPar() {
    // SOLUTION: Path.io(() -> value) creates a lazy IOPath. Nothing executes
    // until unsafeRun() is called on the composed result.
    IOPath<String> result =
        ForPath.par(Path.io(() -> "hello"), Path.io(() -> "world")).yield((a, b) -> a + " " + b);

    assertThat(result.unsafeRun()).isEqualTo("hello world");
  }

  // =========================================================================
  // Exercise 7: VTaskPath par() — true parallelism
  // =========================================================================

  @Test
  @DisplayName("Exercise 7: VTaskPath par(2) executes in parallel")
  void exercise7_vtaskPathParallel() {
    // SOLUTION: VTaskPath par() uses Par.map2 which spawns virtual threads
    // via StructuredTaskScope. Both sleeps run concurrently, so total time
    // is ~100ms rather than ~200ms.
    VTaskPath<String> result =
        ForPath.par(
                Path.vtaskPath(
                    () -> {
                      Thread.sleep(100);
                      return "A";
                    }),
                Path.vtaskPath(
                    () -> {
                      Thread.sleep(100);
                      return "B";
                    }))
            .yield((a, b) -> a + b);

    long start = System.nanoTime();
    String value = result.run().run();
    long elapsed = (System.nanoTime() - start) / 1_000_000; // ms

    assertThat(value).isEqualTo("AB");
    assertThat(elapsed).isLessThan(180);
  }

  // =========================================================================
  // Exercise 8: Chaining after par()
  // =========================================================================

  @Test
  @DisplayName("Exercise 8: Chain let() after par()")
  void exercise8_chainAfterPar() {
    // SOLUTION: par() returns a regular step, so .let() chains naturally.
    // The tuple parameter t in let() holds both par'd values as t._1() and t._2().
    MaybePath<String> result =
        ForPath.par(Path.just("Alice"), Path.just(5))
            .let(t -> t._1() + " has " + t._2() + " letters")
            .yield((name, len, sentence) -> sentence.toUpperCase());

    assertThat(result.getOrElse("nothing")).isEqualTo("ALICE HAS 5 LETTERS");
  }

  // =========================================================================
  // Exercise 9: par() vs from() — understanding the difference
  // =========================================================================

  @Test
  @DisplayName("Exercise 9: par() for independent computations")
  void exercise9_parVsFrom() {
    MaybePath<Integer> sequential =
        ForPath.from(Path.just(10)).from(a -> Path.just(20)).yield((a, b) -> a * b);

    // SOLUTION: par() combines independent computations using applicative semantics.
    // The key difference: with from(), the second computation can depend on the first;
    // with par(), both are declared independent.
    MaybePath<Integer> parallel = ForPath.par(Path.just(10), Path.just(20)).yield((a, b) -> a * b);

    assertThat(sequential.getOrElse(-1)).isEqualTo(200);
    assertThat(parallel.getOrElse(-1)).isEqualTo(200);
  }
}
