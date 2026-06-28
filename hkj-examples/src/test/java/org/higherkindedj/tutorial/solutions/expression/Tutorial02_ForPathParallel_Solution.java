// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

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
 * Solution for Tutorial02 ForPathParallel — teaching-solution format.
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
@DisplayName("Tutorial 02: ForPath Parallel Composition (Solutions)")
public class Tutorial02_ForPathParallel_Solution {

  // =========================================================================
  // Exercise 1: Basic par() with MaybePath
  // =========================================================================

  /**
   * Why this is idiomatic: {@code ForPath.par(a, b).yield((x, y) -> ...)} declares two independent
   * path values and combines their successes in one step. The applicative shape means an
   * interpreter is free to run them concurrently.
   *
   * <p>Alternative: {@code a.flatMap(x -> b.map(y -> x + " is " + y))}. Same outcome; loses the
   * independence signal — a sequential interpreter cannot tell whether the second step depends on
   * the first.
   *
   * <p>Common wrong attempt: pre-extract values with {@code getOrElse} and combine plain strings.
   * The empty case is now handled outside the path, so the rest of the pipeline cannot keep
   * short-circuiting on absence.
   */
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

  /**
   * Why this is idiomatic: with applicative semantics, every input must succeed for the combined
   * result to succeed. A single {@code Path.nothing()} collapses the whole comprehension to {@code
   * Nothing} — exactly the contract the type signals.
   *
   * <p>Alternative: chain with {@code from} so the failure short-circuits sequentially. Same result
   * for the present-or-absent question; {@code par} preserves the potentially-parallel structure
   * for the interpreter.
   *
   * <p>Common wrong attempt: assume the {@code yield} lambda runs with a default value when the
   * second path is empty. It does not — applicative {@code par} only invokes the lambda when both
   * inputs are present.
   */
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

  /**
   * Why this is idiomatic: {@code par(a, b, c)} extends the applicative pattern to three inputs
   * without nesting — the {@code yield} lambda receives all three values at once.
   *
   * <p>Alternative: chain two {@code par(2)} calls. Same answer; the three-arg overload is the
   * named, less-noisy spelling for a flat fan-in.
   *
   * <p>Common wrong attempt: try to pass a list of paths to a hypothetical {@code par(List)}
   * overload. The applicative API is arity-typed because each input may carry a different value
   * type; use the matching {@code par(2)}, {@code par(3)}, or fold via {@code traverse} when the
   * inputs are uniform.
   */
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

  /**
   * Why this is idiomatic: {@code par} on {@code EitherPath} preserves the typed error channel —
   * every input is an {@code EitherPath<E, ?>} for the same {@code E}, so the combined result has
   * somewhere to put a failure.
   *
   * <p>Alternative: a {@code from} chain on the {@code EitherPath} monad. Equivalent on success; on
   * failure the chain short-circuits at the first {@code Left} and so does {@code par}.
   *
   * <p>Common wrong attempt: omit the type witnesses on {@code Path.right} and let inference pick
   * {@code Object}. The combined error type is now {@code Object} too, and downstream
   * pattern-matching against the real error class fails.
   */
  @Test
  @DisplayName("Exercise 4: EitherPath par(2) combines Right values")
  void exercise4_eitherPathPar() {
    // SOLUTION: Use type witnesses on Path.right() to specify the error type.
    // EitherPath par() propagates the first Left encountered.
    EitherPath<String, String> result =
        ForPath.par(Path.<String, String>right("Alice"), Path.<String, Integer>right(42))
            .yield((name, age) -> name + " is " + age);

    assertThatEither(result.run()).hasRight("Alice is 42");
  }

  // =========================================================================
  // Exercise 5: IdPath par() with Id.of()
  // =========================================================================

  /**
   * Why this is idiomatic: {@code IdPath} is the trivial path — no failure, no async — and still
   * composes through {@code par}. The exercise proves the comprehension shape works uniformly
   * across every path type.
   *
   * <p>Alternative: skip {@code IdPath} and operate on the values directly. Pointless once the rest
   * of the pipeline is plain {@code Integer}; useful when an existing comprehension elsewhere
   * expects a {@code Path}.
   *
   * <p>Common wrong attempt: pass a raw {@code int} to {@code Path.idPath}. The constructor expects
   * an {@code Id<A>} wrapper; use {@code Id.of(value)} so the type matches.
   */
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

  /**
   * Why this is idiomatic: {@code Path.io(supplier)} defers the computation; nothing runs until
   * {@code unsafeRun}. The {@code par} combinator still composes the work, but the description
   * stays a value until the boundary asks for the result.
   *
   * <p>Alternative: run both suppliers eagerly and pass plain strings. Simpler in this exercise;
   * loses the laziness, so the same code in production would always pay the I/O cost even if the
   * result is later discarded.
   *
   * <p>Common wrong attempt: call {@code unsafeRun} on each input before {@code par}. The
   * combinator now sees plain values rather than path descriptions; the interpreter has nothing to
   * optimise or interleave.
   */
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

  /**
   * Why this is idiomatic: {@code VTaskPath.par} dispatches each input to a virtual thread via
   * {@code StructuredTaskScope} and joins their results — applicative semantics manifest as real
   * concurrency. Two 100&nbsp;ms sleeps complete in roughly 100&nbsp;ms total.
   *
   * <p>Alternative: a {@code from} chain. The chain must wait for the first task before starting
   * the second, so the wall-clock cost is the sum of the sleeps; {@code par} runs them
   * concurrently.
   *
   * <p>Common wrong attempt: spawn raw {@code Thread}s by hand and {@code join} them. The scoped
   * {@code par} cancels in-flight siblings on failure and propagates exceptions cleanly; ad-hoc
   * threads leave that work to you.
   */
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

  /**
   * Why this is idiomatic: {@code par} is just another step, so {@code let} (and any other
   * comprehension combinator) chains off it. The tuple parameter holds both par'd values, keeping
   * each accessible without re-reading the inputs.
   *
   * <p>Alternative: extract both par'd values into named locals before composing the sentence. Same
   * idea; the inline {@code let} keeps the comprehension expression-shaped.
   *
   * <p>Common wrong attempt: try to use the par'd values by name in the next {@code par} — they
   * live inside a tuple binding. Reach for {@code t._1()} / {@code t._2()} (or destructure in
   * {@code let}) instead.
   */
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

  /**
   * Why this is idiomatic: the side-by-side comparison shows that {@code par} and {@code from}
   * agree on independent inputs but communicate different intent. {@code from} says "the next step
   * may need the previous result"; {@code par} says "they cannot".
   *
   * <p>Alternative: use {@code from} everywhere and rely on the optimiser to detect independence.
   * Modern monads do not in general; expressing independence at the API level is the only signal
   * interpreters can act on.
   *
   * <p>Common wrong attempt: assume {@code par} is always faster. For pure synchronous paths there
   * is no concurrency to exploit; the speedup only materialises with effect-bearing paths like
   * {@code VTaskPath} (see Exercise 7).
   */
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
