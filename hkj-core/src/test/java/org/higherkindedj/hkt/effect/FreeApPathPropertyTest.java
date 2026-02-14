// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Property-based tests for FreeApPath using jQwik.
 *
 * <p>Verifies Functor and Applicative laws hold across a wide range of inputs. FreeApPath
 * represents applicative computations that can be statically analysed before interpretation.
 *
 * <p>Note: FreeApPath implements Composable and Combinable but NOT Chainable, so there are no monad
 * laws to verify - only functor and applicative laws.
 */
@Label("FreeApPath Property-Based Tests")
class FreeApPathPropertyTest {

  private static final Applicative<MaybeKind.Witness> MAYBE_APPLICATIVE = MaybeMonad.INSTANCE;
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  @Provide
  Arbitrary<FreeApPath<MaybeKind.Witness, Integer>> freeApPaths() {
    return Arbitraries.oneOf(
        // Pure values
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> FreeApPath.pure(i, MaybeMonad.INSTANCE)),
        // Lifted values (Just)
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> FreeApPath.liftF(MAYBE.just(i), MaybeMonad.INSTANCE)),
        // Mapped pure values
        Arbitraries.integers()
            .between(-1000, 1000)
            .map(i -> FreeApPath.pure(i, MaybeMonad.INSTANCE).map(x -> x * 2)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        i -> "value:" + i, i -> String.valueOf(i * 2), i -> "n" + i, Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  // Helper to run a FreeApPath and get value
  private <A> A run(FreeApPath<MaybeKind.Witness, A> path) {
    Kind<MaybeKind.Witness, A> result = path.foldMapKind(IDENTITY_NAT, MAYBE_APPLICATIVE);
    return MAYBE.narrow(result).get();
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("freeApPaths") FreeApPath<MaybeKind.Witness, Integer> path) {
    FreeApPath<MaybeKind.Witness, Integer> result = path.map(Function.identity());
    assertThat(run(result)).isEqualTo(run(path));
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("freeApPaths") FreeApPath<MaybeKind.Witness, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    FreeApPath<MaybeKind.Witness, Integer> leftSide = path.map(f).map(g);
    FreeApPath<MaybeKind.Witness, Integer> rightSide = path.map(f.andThen(g));

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  // ===== Applicative Laws =====

  @Property
  @Label("Applicative Identity Law: pure(id) <*> v == v")
  void applicativeIdentityLaw(@ForAll("freeApPaths") FreeApPath<MaybeKind.Witness, Integer> path) {
    // Identity: pure id <*> v = v
    // In terms of zipWith: pure(id).zipWith(v, (f, x) -> f.apply(x)) = v
    FreeApPath<MaybeKind.Witness, Function<Integer, Integer>> pureId =
        FreeApPath.pure(Function.identity(), MaybeMonad.INSTANCE);

    FreeApPath<MaybeKind.Witness, Integer> result = pureId.zipWith(path, (f, x) -> f.apply(x));

    assertThat(run(result)).isEqualTo(run(path));
  }

  @Property
  @Label("Applicative Homomorphism Law: pure(f) <*> pure(x) == pure(f(x))")
  void applicativeHomomorphismLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // pure(f) <*> pure(x) = pure(f(x))
    FreeApPath<MaybeKind.Witness, Function<Integer, String>> pureF =
        FreeApPath.pure(f, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pureX = FreeApPath.pure(value, MaybeMonad.INSTANCE);

    FreeApPath<MaybeKind.Witness, String> leftSide = pureF.zipWith(pureX, Function::apply);
    FreeApPath<MaybeKind.Witness, String> rightSide =
        FreeApPath.pure(f.apply(value), MaybeMonad.INSTANCE);

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  @Property
  @Label("Applicative Interchange Law: u <*> pure(y) == pure($ y) <*> u")
  void applicativeInterchangeLaw(@ForAll @IntRange(min = -100, max = 100) int y) {
    // u <*> pure(y) = pure($ y) <*> u
    // where ($ y) = f -> f(y)
    Function<Integer, String> addPrefix = i -> "val:" + i;
    FreeApPath<MaybeKind.Witness, Function<Integer, String>> u =
        FreeApPath.pure(addPrefix, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pureY = FreeApPath.pure(y, MaybeMonad.INSTANCE);

    // Left side: u <*> pure(y)
    FreeApPath<MaybeKind.Witness, String> leftSide = u.zipWith(pureY, Function::apply);

    // Right side: pure($ y) <*> u, where ($ y) = f -> f.apply(y)
    FreeApPath<MaybeKind.Witness, Function<Function<Integer, String>, String>> dollarY =
        FreeApPath.pure(f -> f.apply(y), MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, String> rightSide = dollarY.zipWith(u, Function::apply);

    assertThat(run(leftSide)).isEqualTo(run(rightSide));
  }

  // ===== Derived Properties =====

  @Property
  @Label("pure creates a value that can be interpreted")
  void pureCreatesInterpretableValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(value, MaybeMonad.INSTANCE);
    assertThat(run(path)).isEqualTo(value);
  }

  @Property
  @Label("liftF creates a value that can be interpreted")
  void liftFCreatesInterpretableValue(@ForAll @IntRange(min = -100, max = 100) int value) {
    Kind<MaybeKind.Witness, Integer> just = MAYBE.just(value);
    FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.liftF(just, MaybeMonad.INSTANCE);
    assertThat(run(path)).isEqualTo(value);
  }

  @Property
  @Label("zipWith combines two free applicative values")
  void zipWithCombinesTwoValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    FreeApPath<MaybeKind.Witness, Integer> pathA = FreeApPath.pure(a, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathB = FreeApPath.pure(b, MaybeMonad.INSTANCE);

    FreeApPath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(run(result)).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith with lifted values")
  void zipWithWithLiftedValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    FreeApPath<MaybeKind.Witness, Integer> pathA =
        FreeApPath.liftF(MAYBE.just(a), MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathB =
        FreeApPath.liftF(MAYBE.just(b), MaybeMonad.INSTANCE);

    FreeApPath<MaybeKind.Witness, Integer> result = pathA.zipWith(pathB, Integer::sum);

    assertThat(run(result)).isEqualTo(a + b);
  }

  @Property
  @Label("zipWith3 combines three values")
  void zipWith3CombinesThreeValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b,
      @ForAll @IntRange(min = -100, max = 100) int c) {

    FreeApPath<MaybeKind.Witness, Integer> pathA = FreeApPath.pure(a, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathB = FreeApPath.pure(b, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathC = FreeApPath.pure(c, MaybeMonad.INSTANCE);

    FreeApPath<MaybeKind.Witness, Integer> result =
        pathA.zipWith3(pathB, pathC, (x, y, z) -> x + y + z);

    assertThat(run(result)).isEqualTo(a + b + c);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("freeApPaths") FreeApPath<MaybeKind.Witness, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    FreeApPath<MaybeKind.Witness, Integer> stepByStep =
        path.map(addOne).map(doubleIt).map(subtract3);
    FreeApPath<MaybeKind.Witness, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(run(stepByStep)).isEqualTo(run(composed));
  }

  @Property
  @Label("toFreeAp returns underlying FreeAp")
  void toFreeApReturnsUnderlyingFreeAp(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(value, MaybeMonad.INSTANCE);

    var freeAp = path.toFreeAp();
    Kind<MaybeKind.Witness, Integer> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);

    assertThat(MAYBE.narrow(result).get()).isEqualTo(value);
  }

  @Property
  @Label("functor returns the functor instance")
  void functorReturnsInstance(@ForAll @IntRange(min = -100, max = 100) int value) {
    FreeApPath<MaybeKind.Witness, Integer> path = FreeApPath.pure(value, MaybeMonad.INSTANCE);

    assertThat(path.functor()).isEqualTo(MaybeMonad.INSTANCE);
  }

  @Property
  @Label("zipWith is associative in result")
  void zipWithAssociativity(
      @ForAll @IntRange(min = -10, max = 10) int a,
      @ForAll @IntRange(min = -10, max = 10) int b,
      @ForAll @IntRange(min = -10, max = 10) int c) {

    FreeApPath<MaybeKind.Witness, Integer> pathA = FreeApPath.pure(a, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathB = FreeApPath.pure(b, MaybeMonad.INSTANCE);
    FreeApPath<MaybeKind.Witness, Integer> pathC = FreeApPath.pure(c, MaybeMonad.INSTANCE);

    // (a + b) + c
    FreeApPath<MaybeKind.Witness, Integer> leftAssoc =
        pathA.zipWith(pathB, Integer::sum).zipWith(pathC, Integer::sum);

    // a + (b + c)
    FreeApPath<MaybeKind.Witness, Integer> rightAssoc =
        pathA.zipWith(pathB.zipWith(pathC, Integer::sum), Integer::sum);

    assertThat(run(leftAssoc)).isEqualTo(run(rightAssoc));
  }
}
