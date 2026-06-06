// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
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
 *
 * <p>Functor and Monad laws are driven by the shipped {@link FunctorLaws}/{@link MonadLaws} helpers
 * over the shared {@link VTaskLawFixtures#EQ}; the MonadError laws and supporting properties have
 * no shipped helper and are checked directly against the same shared equality.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class VTaskPropertyTest {

  private final MonadError<VTaskKind.Witness, Throwable> monad = Instances.monadError(vtask());
  private final VTaskFunctor functor = VTaskFunctor.INSTANCE;

  // ==================== Arbitrary Providers (delegate to VTaskArbitraries) ====================

  @Provide
  Arbitrary<Kind<VTaskKind.Witness, Integer>> vtaskKinds() {
    return VTaskArbitraries.vtaskKinds();
  }

  @Provide
  Arbitrary<Kind<VTaskKind.Witness, Integer>> successfulVtaskKinds() {
    return VTaskArbitraries.successfulVtaskKinds();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<VTaskKind.Witness, String>>> intToVTaskString() {
    return VTaskArbitraries.intToVTaskString();
  }

  @Provide
  Arbitrary<Function<String, Kind<VTaskKind.Witness, String>>> stringToVTaskString() {
    return VTaskArbitraries.stringToVTaskString();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return VTaskArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return VTaskArbitraries.stringToInt();
  }

  // ==================== Functor Laws ====================

  /**
   * Property: Functor Identity Law
   *
   * <p>For all values {@code fa}: {@code map(id, fa) == fa}
   */
  @Property(tries = 50)
  @Label("Functor Identity Law: map(id, fa) = fa")
  void functorIdentityLaw(@ForAll("successfulVtaskKinds") Kind<VTaskKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(functor, fa, VTaskLawFixtures.EQ);
  }

  /**
   * Property: Functor Composition Law
   *
   * <p>For all values {@code fa} and functions {@code f}, {@code g}: {@code map(g.f, fa) == map(g,
   * map(f, fa))}
   */
  @Property(tries = 50)
  @Label("Functor Composition Law: map(g.f, fa) = map(g, map(f, fa))")
  void functorCompositionLaw(
      @ForAll("successfulVtaskKinds") Kind<VTaskKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(functor, fa, f, g, VTaskLawFixtures.EQ);
  }

  // ==================== Monad Laws ====================

  /**
   * Property: Monad Left Identity Law
   *
   * <p>For all values {@code a} and functions {@code f}: {@code flatMap(of(a), f) == f(a)}
   */
  @Property(tries = 50)
  @Label("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
  void monadLeftIdentityLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToVTaskString") Function<Integer, Kind<VTaskKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, VTaskLawFixtures.EQ);
  }

  /**
   * Property: Monad Right Identity Law
   *
   * <p>For all monadic values {@code m}: {@code flatMap(m, of) == m}
   */
  @Property(tries = 50)
  @Label("Monad Right Identity Law: flatMap(m, of) = m")
  void monadRightIdentityLaw(@ForAll("vtaskKinds") Kind<VTaskKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, VTaskLawFixtures.EQ);
  }

  /**
   * Property: Monad Associativity Law
   *
   * <p>For all monadic values {@code m} and functions {@code f} and {@code g}: {@code
   * flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   */
  @Property(tries = 50)
  @Label("Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  void monadAssociativityLaw(
      @ForAll("vtaskKinds") Kind<VTaskKind.Witness, Integer> m,
      @ForAll("intToVTaskString") Function<Integer, Kind<VTaskKind.Witness, String>> f,
      @ForAll("stringToVTaskString") Function<String, Kind<VTaskKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, VTaskLawFixtures.EQ);
  }

  // ==================== MonadError Laws ====================

  /**
   * Property: MonadError Left Zero Law
   *
   * <p>For all errors {@code e} and functions {@code f}: {@code flatMap(raiseError(e), f) ==
   * raiseError(e)}
   */
  @Property(tries = 50)
  @Label("MonadError Left Zero Law: flatMap(raiseError(e), f) = raiseError(e)")
  void monadErrorLeftZeroLaw(
      @ForAll("intToVTaskString") Function<Integer, Kind<VTaskKind.Witness, String>> f) {

    Throwable error = new RuntimeException("test error");

    // Left side: flatMap(raiseError(e), f)
    Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
    Kind<VTaskKind.Witness, String> leftSide = monad.flatMap(f, errorKind);

    // Right side: raiseError(e) (with String type)
    Kind<VTaskKind.Witness, String> rightSide = monad.raiseError(error);

    assertThat(VTaskLawFixtures.EQ.test(leftSide, rightSide)).isTrue();
  }

  /**
   * Property: MonadError Recovery Law
   *
   * <p>For all errors {@code e} and handlers {@code f}: {@code handleErrorWith(raiseError(e), f) ==
   * f(e)}
   */
  @Property(tries = 50)
  @Label("MonadError Recovery Law: handleErrorWith(raiseError(e), f) = f(e)")
  void monadErrorRecoveryLaw(@ForAll @IntRange(max = 100) int recoveryValue) {
    Throwable error = new RuntimeException("test error");
    Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler = _ -> monad.of(recoveryValue);

    // Left side: handleErrorWith(raiseError(e), handler)
    Kind<VTaskKind.Witness, Integer> errorKind = monad.raiseError(error);
    Kind<VTaskKind.Witness, Integer> leftSide = monad.handleErrorWith(errorKind, handler);

    // Right side: handler(e)
    Kind<VTaskKind.Witness, Integer> rightSide = handler.apply(error);

    assertThat(VTaskLawFixtures.EQ.test(leftSide, rightSide)).isTrue();
  }

  /**
   * Property: MonadError Success Passthrough Law
   *
   * <p>For all successful values {@code a} and handlers {@code f}: {@code handleErrorWith(of(a), f)
   * == of(a)}
   */
  @Property(tries = 50)
  @Label("MonadError Success Passthrough Law: handleErrorWith(of(a), f) = of(a)")
  void monadErrorSuccessPassthroughLaw(@ForAll @IntRange(min = -50, max = 50) int value) {

    Function<Throwable, Kind<VTaskKind.Witness, Integer>> handler = _ -> monad.of(-999);

    // Left side: handleErrorWith(of(a), handler)
    Kind<VTaskKind.Witness, Integer> successKind = monad.of(value);
    Kind<VTaskKind.Witness, Integer> leftSide = monad.handleErrorWith(successKind, handler);

    // Right side: of(a)
    Kind<VTaskKind.Witness, Integer> rightSide = monad.of(value);

    assertThat(VTaskLawFixtures.EQ.test(leftSide, rightSide)).isTrue();
  }

  // ==================== Additional Properties ====================

  /** Property: flatMap over failure preserves the failure. */
  @Property(tries = 50)
  @Label("FlatMapping over failure preserves the failure")
  void flatMapPreservesFailure(
      @ForAll("intToVTaskString") Function<Integer, Kind<VTaskKind.Witness, String>> f) {

    RuntimeException error = new RuntimeException("preserved error");
    Kind<VTaskKind.Witness, Integer> m = VTASK.widen(VTask.fail(error));

    Kind<VTaskKind.Witness, String> result = monad.flatMap(f, m);

    Try<String> tryResult = VTASK.narrow(result).runSafe();
    assertThat(tryResult.isFailure()).isTrue();
    assertThat(((Try.Failure<String>) tryResult).cause().getMessage()).isEqualTo("preserved error");
  }

  /** Property: Multiple flatMap operations chain correctly. */
  @Property(tries = 50)
  @Label("Multiple flatMap operations chain correctly")
  void multipleFlatMapsChain(@ForAll("vtaskKinds") Kind<VTaskKind.Witness, Integer> m) {
    Function<Integer, Kind<VTaskKind.Witness, Integer>> addOne = i -> monad.of(i + 1);
    Function<Integer, Kind<VTaskKind.Witness, Integer>> double_ = i -> monad.of(i * 2);

    // Apply flatMaps in sequence
    Kind<VTaskKind.Witness, Integer> step1 = monad.flatMap(addOne, m);
    Kind<VTaskKind.Witness, Integer> step2 = monad.flatMap(double_, step1);

    // Compose all operations
    Function<Integer, Kind<VTaskKind.Witness, Integer>> composed =
        i -> monad.flatMap(double_, addOne.apply(i));
    Kind<VTaskKind.Witness, Integer> composedResult = monad.flatMap(composed, m);

    assertThat(VTaskLawFixtures.EQ.test(step2, composedResult)).isTrue();
  }

  /**
   * Property: map is consistent with flatMap.
   *
   * <p>map(f, fa) == flatMap(a -> of(f(a)), fa)
   */
  @Property(tries = 50)
  @Label("map is consistent with flatMap: map(f, fa) = flatMap(a -> of(f(a)), fa)")
  void mapConsistentWithFlatMap(
      @ForAll("successfulVtaskKinds") Kind<VTaskKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f) {

    // Left side: map(f, fa)
    Kind<VTaskKind.Witness, String> mapResult = functor.map(f, fa);

    // Right side: flatMap(a -> of(f(a)), fa)
    Kind<VTaskKind.Witness, String> flatMapResult = monad.flatMap(a -> monad.of(f.apply(a)), fa);

    assertThat(VTaskLawFixtures.EQ.test(mapResult, flatMapResult)).isTrue();
  }
}
