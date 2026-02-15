// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Objects;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;

/**
 * Property-based tests for VTask Monad and MonadError laws using jQwik.
 *
 * <p>This test class verifies that the VTask monad satisfies:
 *
 * <ul>
 *   <li><b>Functor Laws:</b>
 *       <ul>
 *         <li>Identity: {@code map(id, fa) == fa}
 *         <li>Composition: {@code map(g.f, fa) == map(g, map(f, fa))}
 *       </ul>
 *   <li><b>Monad Laws:</b>
 *       <ul>
 *         <li>Left Identity: {@code flatMap(of(a), f) == f(a)}
 *         <li>Right Identity: {@code flatMap(m, of) == m}
 *         <li>Associativity: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
 *       </ul>
 *   <li><b>MonadError Laws:</b>
 *       <ul>
 *         <li>Left Zero: {@code flatMap(raiseError(e), f) == raiseError(e)}
 *         <li>Recovery: {@code handleErrorWith(raiseError(e), f) == f(e)}
 *         <li>Success Passthrough: {@code handleErrorWith(of(a), f) == of(a)}
 *       </ul>
 * </ul>
 */
class VTaskPropertyTest {

  private final VTaskMonad monad = VTaskMonad.INSTANCE;
  private final VTaskFunctor functor = VTaskFunctor.INSTANCE;

  // ==================== Helper Methods ====================

  /** Safely runs a VTask and returns a Try to capture success or failure. */
  private <A> Try<A> runSafe(VTask<A> task) {
    return task.runSafe();
  }

  /** Compares two VTask results by running them and comparing outcomes. */
  private <A> boolean vtasksEqual(VTask<A> a, VTask<A> b) {
    Try<A> resultA = runSafe(a);
    Try<A> resultB = runSafe(b);

    if (resultA.isSuccess() != resultB.isSuccess()) {
      return false;
    }

    if (resultA.isSuccess()) {
      return Objects.equals(resultA.orElse(null), resultB.orElse(null));
    } else {
      // Both failures - compare exception types and messages
      Throwable causeA = ((Try.Failure<A>) resultA).cause();
      Throwable causeB = ((Try.Failure<A>) resultB).cause();
      return causeA.getClass().equals(causeB.getClass())
          && Objects.equals(causeA.getMessage(), causeB.getMessage());
    }
  }

  // ==================== Arbitrary Providers ====================

  /** Provides arbitrary VTask<Integer> values (both successful and failing). */
  @Provide
  Arbitrary<VTask<Integer>> vtaskInts() {
    return Arbitraries.integers()
        .between(-100, 100)
        .flatMap(
            i -> {
              // 20% chance of failure
              if (i % 5 == 0) {
                return Arbitraries.of(
                        new RuntimeException("error: validation failed"),
                        new IllegalArgumentException("error: invalid input"),
                        new IllegalStateException("error: bad state"))
                    .map(VTask::fail);
              }
              return Arbitraries.just(VTask.succeed(i));
            });
  }

  /** Provides arbitrary successful VTask<Integer> values. */
  @Provide
  Arbitrary<VTask<Integer>> successfulVtaskInts() {
    return Arbitraries.integers().between(-100, 100).map(VTask::succeed);
  }

  /** Provides arbitrary flatMap functions (Integer -> VTask<String>). */
  @Provide
  Arbitrary<Function<Integer, VTask<String>>> intToVTaskStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? VTask.succeed("even:" + i) : VTask.fail(new RuntimeException("odd")),
        i ->
            i > 0
                ? VTask.succeed("positive:" + i)
                : VTask.fail(new RuntimeException("non-positive")),
        i -> VTask.succeed("value:" + i),
        i -> i == 0 ? VTask.fail(new RuntimeException("zero")) : VTask.succeed(String.valueOf(i)));
  }

  /** Provides arbitrary flatMap functions (String -> VTask<String>). */
  @Provide
  Arbitrary<Function<String, VTask<String>>> stringToVTaskStringFunctions() {
    return Arbitraries.of(
        s ->
            s.isEmpty()
                ? VTask.fail(new RuntimeException("empty"))
                : VTask.succeed(s.toUpperCase()),
        s ->
            s.length() > 3 ? VTask.succeed("long:" + s) : VTask.fail(new RuntimeException("short")),
        s -> VTask.succeed("transformed:" + s));
  }

  /** Provides arbitrary pure functions (Integer -> String). */
  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        Object::toString,
        i -> "val:" + i,
        i -> String.valueOf(i * 2),
        i -> i >= 0 ? "+" + i : String.valueOf(i));
  }

  /** Provides arbitrary pure functions (String -> Integer). */
  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(
        String::length, s -> s.hashCode() % 100, s -> s.isEmpty() ? 0 : (int) s.charAt(0));
  }

  // ==================== Functor Laws ====================

  /**
   * Property: Functor Identity Law
   *
   * <p>For all values {@code fa}: {@code map(id, fa) == fa}
   */
  @Property
  @Label("Functor Identity Law: map(id, fa) = fa")
  void functorIdentityLaw(@ForAll("successfulVtaskInts") VTask<Integer> vtask) {
    Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(vtask);
    Function<Integer, Integer> identity = Function.identity();

    Kind<VTaskKind.Witness, Integer> result = functor.map(identity, fa);

    assertThat(vtasksEqual(VTASK.narrow(result), vtask)).isTrue();
  }

  /**
   * Property: Functor Composition Law
   *
   * <p>For all values {@code fa} and functions {@code f}, {@code g}: {@code map(g.f, fa) == map(g,
   * map(f, fa))}
   */
  @Property
  @Label("Functor Composition Law: map(g.f, fa) = map(g, map(f, fa))")
  void functorCompositionLaw(
      @ForAll("successfulVtaskInts") VTask<Integer> vtask,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(vtask);

    // Left side: map(g.f, fa)
    Function<Integer, Integer> composed = f.andThen(g);
    Kind<VTaskKind.Witness, Integer> leftSide = functor.map(composed, fa);

    // Right side: map(g, map(f, fa))
    Kind<VTaskKind.Witness, String> intermediate = functor.map(f, fa);
    Kind<VTaskKind.Witness, Integer> rightSide = functor.map(g, intermediate);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide))).isTrue();
  }

  // ==================== Monad Laws ====================

  /**
   * Property: Monad Left Identity Law
   *
   * <p>For all values {@code a} and functions {@code f}: {@code flatMap(of(a), f) == f(a)}
   */
  @Property
  @Label("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
  void monadLeftIdentityLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTask<String>> f) {

    // Left side: flatMap(of(a), f)
    Kind<VTaskKind.Witness, Integer> ofValue = monad.of(value);
    Kind<VTaskKind.Witness, String> leftSide = monad.flatMap(i -> VTASK.widen(f.apply(i)), ofValue);

    // Right side: f(a)
    VTask<String> rightSide = f.apply(value);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), rightSide)).isTrue();
  }

  /**
   * Property: Monad Right Identity Law
   *
   * <p>For all monadic values {@code m}: {@code flatMap(m, of) == m}
   */
  @Property
  @Label("Monad Right Identity Law: flatMap(m, of) = m")
  void monadRightIdentityLaw(@ForAll("vtaskInts") VTask<Integer> vtask) {
    Kind<VTaskKind.Witness, Integer> m = VTASK.widen(vtask);

    // flatMap(m, of)
    Kind<VTaskKind.Witness, Integer> result = monad.flatMap(monad::of, m);

    assertThat(vtasksEqual(VTASK.narrow(result), vtask)).isTrue();
  }

  /**
   * Property: Monad Associativity Law
   *
   * <p>For all monadic values {@code m} and functions {@code f} and {@code g}: {@code
   * flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   */
  @Property
  @Label("Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  void monadAssociativityLaw(
      @ForAll("vtaskInts") VTask<Integer> vtask,
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTask<String>> f,
      @ForAll("stringToVTaskStringFunctions") Function<String, VTask<String>> g) {

    Kind<VTaskKind.Witness, Integer> m = VTASK.widen(vtask);

    // Left side: flatMap(flatMap(m, f), g)
    Function<Integer, Kind<VTaskKind.Witness, String>> fLifted = i -> VTASK.widen(f.apply(i));
    Function<String, Kind<VTaskKind.Witness, String>> gLifted = s -> VTASK.widen(g.apply(s));

    Kind<VTaskKind.Witness, String> innerFlatMap = monad.flatMap(fLifted, m);
    Kind<VTaskKind.Witness, String> leftSide = monad.flatMap(gLifted, innerFlatMap);

    // Right side: flatMap(m, x -> flatMap(f(x), g))
    Function<Integer, Kind<VTaskKind.Witness, String>> composed =
        x -> monad.flatMap(gLifted, fLifted.apply(x));
    Kind<VTaskKind.Witness, String> rightSide = monad.flatMap(composed, m);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide))).isTrue();
  }

  // ==================== MonadError Laws ====================

  /**
   * Property: MonadError Left Zero Law
   *
   * <p>For all errors {@code e} and functions {@code f}: {@code flatMap(raiseError(e), f) ==
   * raiseError(e)}
   */
  @Property
  @Label("MonadError Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)")
  void monadErrorLeftZeroLaw(
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTask<String>> f) {

    Throwable error = new RuntimeException("test error");

    // Left side: flatMap(raiseError(e), f)
    Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
    Kind<VTaskKind.Witness, String> leftSide =
        monad.flatMap(i -> VTASK.widen(f.apply(i)), errorKind);

    // Right side: raiseError(e) (with String type)
    Kind<VTaskKind.Witness, String> rightSide = monad.raiseError(error);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide))).isTrue();
  }

  /**
   * Property: MonadError Recovery Law
   *
   * <p>For all errors {@code e} and handlers {@code f}: {@code handleErrorWith(raiseError(e), f) ==
   * f(e)}
   */
  @Property
  @Label("MonadError Recovery Law: handleErrorWith(raiseError(e), f) = f(e)")
  void monadErrorRecoveryLaw(@ForAll @IntRange(min = 0, max = 100) int recoveryValue) {
    Throwable error = new RuntimeException("test error");
    Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler = e -> monad.of(recoveryValue);

    // Left side: handleErrorWith(raiseError(e), handler)
    Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
    Kind<VTaskKind.Witness, Integer> leftSide = monad.handleErrorWith(errorKind, handler);

    // Right side: handler(e)
    Kind<VTaskKind.Witness, Integer> rightSide = handler.apply(error);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide))).isTrue();
  }

  /**
   * Property: MonadError Success Passthrough Law
   *
   * <p>For all successful values {@code a} and handlers {@code f}: {@code handleErrorWith(of(a), f)
   * == of(a)}
   */
  @Property
  @Label("MonadError Success Passthrough Law: handleErrorWith(of(a), f) = of(a)")
  void monadErrorSuccessPassthroughLaw(@ForAll @IntRange(min = -50, max = 50) int value) {

    Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler = e -> monad.of(-999);

    // Left side: handleErrorWith(of(a), handler)
    Kind<VTaskKind.Witness, Integer> successKind = monad.of(value);
    Kind<VTaskKind.Witness, Integer> leftSide = monad.handleErrorWith(successKind, handler);

    // Right side: of(a)
    Kind<VTaskKind.Witness, Integer> rightSide = monad.of(value);

    assertThat(vtasksEqual(VTASK.narrow(leftSide), VTASK.narrow(rightSide))).isTrue();
  }

  // ==================== Additional Properties ====================

  /** Property: flatMap over failure preserves the failure. */
  @Property
  @Label("FlatMapping over failure preserves the failure")
  void flatMapPreservesFailure(
      @ForAll("intToVTaskStringFunctions") Function<Integer, VTask<String>> f) {

    RuntimeException error = new RuntimeException("preserved error");
    VTask<Integer> failedTask = VTask.fail(error);
    Kind<VTaskKind.Witness, Integer> m = VTASK.widen(failedTask);

    Kind<VTaskKind.Witness, String> result = monad.flatMap(i -> VTASK.widen(f.apply(i)), m);

    Try<String> tryResult = VTASK.narrow(result).runSafe();
    assertThat(tryResult.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) tryResult).cause().getMessage()).isEqualTo("preserved error");
  }

  /** Property: Multiple flatMap operations chain correctly. */
  @Property(tries = 50)
  @Label("Multiple flatMap operations chain correctly")
  void multipleFlatMapsChain(@ForAll("vtaskInts") VTask<Integer> vtask) {
    Kind<VTaskKind.Witness, Integer> m = VTASK.widen(vtask);

    Function<Integer, Kind<VTaskKind.Witness, Integer>> addOne = i -> monad.of(i + 1);
    Function<Integer, Kind<VTaskKind.Witness, Integer>> double_ = i -> monad.of(i * 2);

    // Apply flatMaps in sequence
    Kind<VTaskKind.Witness, Integer> step1 = monad.flatMap(addOne, m);
    Kind<VTaskKind.Witness, Integer> step2 = monad.flatMap(double_, step1);

    // Compose all operations
    Function<Integer, Kind<VTaskKind.Witness, Integer>> composed =
        i -> monad.flatMap(double_, addOne.apply(i));
    Kind<VTaskKind.Witness, Integer> composedResult = monad.flatMap(composed, m);

    assertThat(vtasksEqual(VTASK.narrow(step2), VTASK.narrow(composedResult))).isTrue();
  }

  /**
   * Property: map is consistent with flatMap.
   *
   * <p>map(f, fa) == flatMap(a -> of(f(a)), fa)
   */
  @Property
  @Label("map is consistent with flatMap: map(f, fa) = flatMap(a -> of(f(a)), fa)")
  void mapConsistentWithFlatMap(
      @ForAll("successfulVtaskInts") VTask<Integer> vtask,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(vtask);

    // Left side: map(f, fa)
    Kind<VTaskKind.Witness, String> mapResult = functor.map(f, fa);

    // Right side: flatMap(a -> of(f(a)), fa)
    Kind<VTaskKind.Witness, String> flatMapResult = monad.flatMap(a -> monad.of(f.apply(a)), fa);

    assertThat(vtasksEqual(VTASK.narrow(mapResult), VTASK.narrow(flatMapResult))).isTrue();
  }
}
