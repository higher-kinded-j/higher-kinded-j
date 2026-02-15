// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;

/**
 * Property-based tests for Either Monad laws using jQwik.
 *
 * <p>This test class verifies that the Either monad satisfies the three monad laws across a wide
 * range of inputs:
 *
 * <ul>
 *   <li>Left Identity: {@code flatMap(of(a), f) == f(a)}
 *   <li>Right Identity: {@code flatMap(m, of) == m}
 *   <li>Associativity: {@code flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
 * </ul>
 *
 * <p>Either is right-biased and fail-fast: the first Left encountered stops the computation.
 */
class EitherMonadPropertyTest {

  private final EitherMonad<String> monad = EitherMonad.instance();

  /** Provides arbitrary Either<String, Integer> values for testing */
  @Provide
  Arbitrary<Either<String, Integer>> eitherInts() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15) // 15% chance of null -> Left
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(Either.left("null value"));
              }
              // 20% chance of Left
              if (i % 5 == 0) {
                return Arbitraries.of(
                        "error: validation failed", "error: invalid input", "error: out of bounds")
                    .map(Either::left);
              }
              return Arbitraries.just(Either.right(i));
            });
  }

  /** Provides arbitrary flatMap functions (Integer -> Either<String, String>) */
  @Provide
  Arbitrary<Function<Integer, Either<String, String>>> intToEitherStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? Either.right("even:" + i) : Either.left("error: odd number"),
        i -> i > 0 ? Either.right("positive:" + i) : Either.left("error: non-positive number"),
        i -> Either.right("value:" + i),
        i -> i == 0 ? Either.left("error: zero") : Either.right(String.valueOf(i)));
  }

  /** Provides arbitrary flatMap functions (String -> Either<String, String>) */
  @Provide
  Arbitrary<Function<String, Either<String, String>>> stringToEitherStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? Either.left("error: empty") : Either.right(s.toUpperCase()),
        s -> s.length() > 3 ? Either.right("long:" + s) : Either.left("error: too short"),
        s -> Either.right("transformed:" + s));
  }

  /**
   * Property: Monad Left Identity Law
   *
   * <p>For all values {@code a} and functions {@code f}: {@code flatMap(of(a), f) == f(a)}
   *
   * <p>This law states that wrapping a value with {@code of} and then flat-mapping is equivalent to
   * just applying the function.
   */
  @Property
  @Label("Monad Left Identity Law: flatMap(of(a), f) = f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToEitherStringFunctions") Function<Integer, Either<String, String>> f) {

    // Left side: flatMap(of(a), f)
    Kind<EitherKind.Witness<String>, Integer> ofValue = monad.of(value);
    Kind<EitherKind.Witness<String>, String> leftSide =
        monad.flatMap(i -> EITHER.widen(f.apply(i)), ofValue);

    // Right side: f(a)
    Either<String, String> rightSide = f.apply(value);

    assertThat(EITHER.narrow(leftSide)).isEqualTo(rightSide);
  }

  /**
   * Property: Monad Right Identity Law
   *
   * <p>For all monadic values {@code m}: {@code flatMap(m, of) == m}
   *
   * <p>This law states that flat-mapping with {@code of} should return the original value
   * unchanged.
   */
  @Property
  @Label("Monad Right Identity Law: flatMap(m, of) = m")
  void rightIdentityLaw(@ForAll("eitherInts") Either<String, Integer> either) {
    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);

    // flatMap(m, of)
    Kind<EitherKind.Witness<String>, Integer> result = monad.flatMap(monad::of, kindEither);

    assertThat(EITHER.narrow(result)).isEqualTo(either);
  }

  /**
   * Property: Monad Associativity Law
   *
   * <p>For all monadic values {@code m} and functions {@code f} and {@code g}: {@code
   * flatMap(flatMap(m, f), g) == flatMap(m, x -> flatMap(f(x), g))}
   *
   * <p>This law ensures that the order of nested flatMaps doesn't matter.
   */
  @Property
  @Label("Monad Associativity Law: flatMap(flatMap(m, f), g) = flatMap(m, x -> flatMap(f(x), g))")
  void associativityLaw(
      @ForAll("eitherInts") Either<String, Integer> either,
      @ForAll("intToEitherStringFunctions") Function<Integer, Either<String, String>> f,
      @ForAll("stringToEitherStringFunctions") Function<String, Either<String, String>> g) {

    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);

    // Left side: flatMap(flatMap(m, f), g)
    Function<Integer, Kind<EitherKind.Witness<String>, String>> fLifted =
        i -> EITHER.widen(f.apply(i));
    Function<String, Kind<EitherKind.Witness<String>, String>> gLifted =
        s -> EITHER.widen(g.apply(s));

    Kind<EitherKind.Witness<String>, String> innerFlatMap = monad.flatMap(fLifted, kindEither);
    Kind<EitherKind.Witness<String>, String> leftSide = monad.flatMap(gLifted, innerFlatMap);

    // Right side: flatMap(m, x -> flatMap(f(x), g))
    Function<Integer, Kind<EitherKind.Witness<String>, String>> composed =
        x -> monad.flatMap(gLifted, fLifted.apply(x));
    Kind<EitherKind.Witness<String>, String> rightSide = monad.flatMap(composed, kindEither);

    assertThat(EITHER.narrow(leftSide)).isEqualTo(EITHER.narrow(rightSide));
  }

  /**
   * Property: flatMap over Left always returns the same Left
   *
   * <p>This is a derived property that helps verify correct fail-fast implementation.
   */
  @Property
  @Label("FlatMapping over Left always returns the same Left")
  void flatMapPreservesLeft(
      @ForAll("intToEitherStringFunctions") Function<Integer, Either<String, String>> f) {

    Either<String, Integer> left = Either.left("error: test failure");
    Kind<EitherKind.Witness<String>, Integer> kindLeft = EITHER.widen(left);

    Kind<EitherKind.Witness<String>, String> result =
        monad.flatMap(i -> EITHER.widen(f.apply(i)), kindLeft);

    Either<String, String> narrowed = EITHER.narrow(result);
    assertThat(narrowed.isLeft()).isTrue();
    assertThat(narrowed.getLeft()).isEqualTo("error: test failure");
  }

  /**
   * Property: flatMap applies function to Right values and flattens
   *
   * <p>This property verifies that flatMap correctly extracts the value from Right, applies the
   * function, and flattens the result.
   */
  @Property
  @Label("FlatMap applies function and flattens for Right values")
  void flatMapAppliesAndFlattens(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToEitherStringFunctions") Function<Integer, Either<String, String>> f) {

    Either<String, Integer> right = Either.right(value);
    Kind<EitherKind.Witness<String>, Integer> kindRight = EITHER.widen(right);

    Kind<EitherKind.Witness<String>, String> result =
        monad.flatMap(i -> EITHER.widen(f.apply(i)), kindRight);

    // Result should equal f(value) directly
    assertThat(EITHER.narrow(result)).isEqualTo(f.apply(value));
  }

  /**
   * Property: Chaining multiple flatMaps (fail-fast behavior)
   *
   * <p>Demonstrates that the first Left in a chain stops all subsequent computations.
   */
  @Property(tries = 50)
  @Label("Multiple flatMap operations chain correctly (fail-fast)")
  void multipleFlatMapsChain(@ForAll("eitherInts") Either<String, Integer> either) {
    Kind<EitherKind.Witness<String>, Integer> kindEither = EITHER.widen(either);

    Function<Integer, Kind<EitherKind.Witness<String>, Integer>> addOne = i -> monad.of(i + 1);
    Function<Integer, Kind<EitherKind.Witness<String>, Integer>> double_ = i -> monad.of(i * 2);
    Function<Integer, Kind<EitherKind.Witness<String>, Integer>> conditionalLeft =
        i -> i > 100 ? EITHER.widen(Either.left("error: too large")) : monad.of(i);

    // Apply flatMaps in sequence
    Kind<EitherKind.Witness<String>, Integer> step1 = monad.flatMap(addOne, kindEither);
    Kind<EitherKind.Witness<String>, Integer> step2 = monad.flatMap(double_, step1);
    Kind<EitherKind.Witness<String>, Integer> step3 = monad.flatMap(conditionalLeft, step2);

    // Compose all operations
    Function<Integer, Kind<EitherKind.Witness<String>, Integer>> composed =
        i -> monad.flatMap(conditionalLeft, monad.flatMap(double_, addOne.apply(i)));
    Kind<EitherKind.Witness<String>, Integer> composedResult = monad.flatMap(composed, kindEither);

    assertThat(EITHER.narrow(step3)).isEqualTo(EITHER.narrow(composedResult));
  }

  /**
   * Property: raiseError creates a Left value
   *
   * <p>Verifies that the MonadError operation raiseError correctly creates a Left.
   */
  @Property
  @Label("raiseError creates a Left with the specified error")
  void raiseErrorCreatesLeft(@ForAll String errorMessage) {
    Kind<EitherKind.Witness<String>, Integer> error = monad.raiseError(errorMessage);

    Either<String, Integer> narrowed = EITHER.narrow(error);
    assertThat(narrowed.isLeft()).isTrue();
    assertThat(narrowed.getLeft()).isEqualTo(errorMessage);
  }

  /**
   * Property: handleErrorWith can recover from Left values
   *
   * <p>Demonstrates error recovery using handleErrorWith.
   */
  @Property
  @Label("handleErrorWith can recover from Left values")
  void handleErrorWithRecovers(@ForAll @IntRange(min = 0, max = 100) int recoveryValue) {
    Either<String, Integer> left = Either.left("error: failure");
    Kind<EitherKind.Witness<String>, Integer> kindLeft = EITHER.widen(left);

    Kind<EitherKind.Witness<String>, Integer> recovered =
        monad.handleErrorWith(kindLeft, error -> monad.of(recoveryValue));

    Either<String, Integer> narrowed = EITHER.narrow(recovered);
    assertThat(narrowed.isRight()).isTrue();
    assertThat(narrowed.getRight()).isEqualTo(recoveryValue);
  }
}
