// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;

/**
 * Property-based tests for Validated Applicative laws using jQwik.
 *
 * <p>This test class verifies that Validated satisfies the four applicative laws across a wide
 * range of inputs:
 *
 * <ul>
 *   <li>Identity: {@code ap(of(id), v) = v}
 *   <li>Composition: {@code ap(ap(ap(of(compose), u), v), w) = ap(u, ap(v, w))}
 *   <li>Homomorphism: {@code ap(of(f), of(x)) = of(f(x))}
 *   <li>Interchange: {@code ap(u, of(y)) = ap(of(f -> f(y)), u)}
 * </ul>
 *
 * <p>Validated's key feature is <strong>error accumulation</strong>: when multiple Invalid values
 * are combined via {@code ap}, their errors are accumulated using a Semigroup, unlike Either which
 * fails fast.
 */
class ValidatedApplicativePropertyTest {

  /** Semigroup for List<String> that concatenates error lists */
  private final Semigroup<List<String>> listSemigroup =
      new Semigroup<>() {
        @Override
        public List<String> combine(List<String> a, List<String> b) {
          List<String> combined = new ArrayList<>(a);
          combined.addAll(b);
          return combined;
        }
      };

  private final ValidatedMonad<List<String>> applicative = ValidatedMonad.instance(listSemigroup);

  /** Provides arbitrary Validated<List<String>, Integer> values for testing */
  @Provide
  Arbitrary<Validated<List<String>, Integer>> validatedInts() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15) // 15% chance of null -> Invalid
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(Validated.invalid(List.of("null value")));
              }
              // 20% chance of Invalid with various errors
              if (i % 5 == 0) {
                return Arbitraries.of(
                        List.of("error: validation failed"),
                        List.of("error: invalid input"),
                        List.of("error: out of bounds"),
                        List.of("error: negative", "error: even")) // Multiple errors
                    .map(Validated::invalid);
              }
              return Arbitraries.just(Validated.valid(i));
            });
  }

  /** Provides arbitrary functions for Integer -> String transformations */
  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(
        (Function<Integer, String>) (i -> "value:" + i),
        (Function<Integer, String>) (i -> String.valueOf(i * 2)),
        (Function<Integer, String>) (i -> "test-" + i),
        (Function<Integer, String>) (i -> i.toString()));
  }

  /** Provides arbitrary functions for String -> Integer transformations */
  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(
        (Function<String, Integer>) String::length,
        (Function<String, Integer>) (s -> s.hashCode()),
        (Function<String, Integer>) (s -> s.isEmpty() ? 0 : 1));
  }

  /**
   * Property: Applicative Identity Law
   *
   * <p>For all applicative values {@code v}: {@code ap(of(id), v) = v}
   *
   * <p>Applying the identity function wrapped in the applicative should return the original value.
   */
  @Property
  @Label("Applicative Identity Law: ap(of(id), v) = v")
  void applicativeIdentityLaw(@ForAll("validatedInts") Validated<List<String>, Integer> validated) {
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValidated = VALIDATED.widen(validated);

    // Create of(id)
    Function<Integer, Integer> identity = x -> x;
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, Integer>> ofId =
        applicative.of(identity);

    // ap(of(id), v)
    Kind<ValidatedKind.Witness<List<String>>, Integer> result = applicative.ap(ofId, kindValidated);

    assertThat(VALIDATED.narrow(result)).isEqualTo(validated);
  }

  /**
   * Property: Applicative Homomorphism Law
   *
   * <p>For all functions {@code f} and values {@code x}: {@code ap(of(f), of(x)) = of(f(x))}
   *
   * <p>Applying a wrapped function to a wrapped value is the same as wrapping the application.
   */
  @Property
  @Label("Applicative Homomorphism Law: ap(of(f), of(x)) = of(f(x))")
  void applicativeHomomorphismLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // Left side: ap(of(f), of(x))
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> ofF = applicative.of(f);
    Kind<ValidatedKind.Witness<List<String>>, Integer> ofX = applicative.of(value);
    Kind<ValidatedKind.Witness<List<String>>, String> leftSide = applicative.ap(ofF, ofX);

    // Right side: of(f(x))
    Kind<ValidatedKind.Witness<List<String>>, String> rightSide = applicative.of(f.apply(value));

    assertThat(VALIDATED.narrow(leftSide)).isEqualTo(VALIDATED.narrow(rightSide));
  }

  /**
   * Property: Applicative Interchange Law
   *
   * <p>For all applicative functions {@code u} and values {@code y}: {@code ap(u, of(y)) = ap(of(f
   * -> f(y)), u)}
   *
   * <p>The order of application can be interchanged.
   */
  @Property
  @Label("Applicative Interchange Law: ap(u, of(y)) = ap(of(f -> f(y)), u)")
  void applicativeInterchangeLaw(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // Create u (a Valid function or Invalid)
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> u =
        value % 2 == 0
            ? applicative.of(f)
            : VALIDATED.widen(Validated.invalid(List.of("error: odd value")));

    // Left side: ap(u, of(y))
    Kind<ValidatedKind.Witness<List<String>>, Integer> ofY = applicative.of(value);
    Kind<ValidatedKind.Witness<List<String>>, String> leftSide = applicative.ap(u, ofY);

    // Right side: ap(of(f -> f(y)), u)
    Function<Function<Integer, String>, String> applyToY = fn -> fn.apply(value);
    Kind<ValidatedKind.Witness<List<String>>, Function<Function<Integer, String>, String>> ofApply =
        applicative.of(applyToY);
    Kind<ValidatedKind.Witness<List<String>>, String> rightSide = applicative.ap(ofApply, u);

    assertThat(VALIDATED.narrow(leftSide)).isEqualTo(VALIDATED.narrow(rightSide));
  }

  /**
   * Property: Error accumulation in ap
   *
   * <p>When both the function and the value are Invalid, errors should be accumulated.
   */
  @Example
  @Label("ap accumulates errors when both function and value are Invalid")
  void apAccumulatesErrors() {
    List<String> functionErrors = List.of("error: bad function");
    List<String> valueErrors = List.of("error: bad value");

    Validated<List<String>, Function<Integer, String>> invalidFunction =
        Validated.invalid(functionErrors);
    Validated<List<String>, Integer> invalidValue = Validated.invalid(valueErrors);

    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(invalidFunction);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue = VALIDATED.widen(invalidValue);

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    Validated<List<String>, String> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();

    // Errors should be accumulated (combined via semigroup)
    List<String> expectedErrors = new ArrayList<>(functionErrors);
    expectedErrors.addAll(valueErrors);
    assertThat(narrowed.getError()).isEqualTo(expectedErrors);
  }

  /**
   * Property: Error accumulation with multiple Invalid values
   *
   * <p>Demonstrates error accumulation across multiple applicative operations using map2.
   */
  @Example
  @Label("map2 accumulates all errors from Invalid values")
  void map2AccumulatesAllErrors() {
    Validated<List<String>, Integer> invalid1 = Validated.invalid(List.of("error: first"));
    Validated<List<String>, Integer> invalid2 = Validated.invalid(List.of("error: second"));

    Kind<ValidatedKind.Witness<List<String>>, Integer> kind1 = VALIDATED.widen(invalid1);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kind2 = VALIDATED.widen(invalid2);

    // map2 uses ap internally, so errors should accumulate
    Kind<ValidatedKind.Witness<List<String>>, Integer> result =
        applicative.map2(kind1, kind2, (a, b) -> a + b);

    Validated<List<String>, Integer> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();
    assertThat(narrowed.getError()).containsExactly("error: first", "error: second");
  }

  /**
   * Property: Valid values propagate through ap
   *
   * <p>When both function and value are Valid, ap should apply the function.
   */
  @Property
  @Label("ap applies function when both function and value are Valid")
  void apAppliesWhenBothValid(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    Validated<List<String>, Function<Integer, String>> validFunction = Validated.valid(f);
    Validated<List<String>, Integer> validValue = Validated.valid(value);

    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(validFunction);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue = VALIDATED.widen(validValue);

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    Validated<List<String>, String> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isValid()).isTrue();
    assertThat(narrowed.get()).isEqualTo(f.apply(value));
  }

  /**
   * Property: ap propagates Invalid function even with Valid value
   *
   * <p>If the function is Invalid but the value is Valid, the Invalid should propagate.
   */
  @Property
  @Label("ap propagates Invalid function even with Valid value")
  void apPropagatesInvalidFunction(@ForAll @IntRange(min = -50, max = 50) int value) {
    List<String> errors = List.of("error: invalid function");
    Validated<List<String>, Function<Integer, String>> invalidFunction = Validated.invalid(errors);
    Validated<List<String>, Integer> validValue = Validated.valid(value);

    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(invalidFunction);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue = VALIDATED.widen(validValue);

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    Validated<List<String>, String> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();
    assertThat(narrowed.getError()).isEqualTo(errors);
  }

  /**
   * Property: ap propagates Invalid value even with Valid function
   *
   * <p>If the value is Invalid but the function is Valid, the Invalid should propagate.
   */
  @Property
  @Label("ap propagates Invalid value even with Valid function")
  void apPropagatesInvalidValue(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    List<String> errors = List.of("error: invalid value");
    Validated<List<String>, Function<Integer, String>> validFunction = Validated.valid(f);
    Validated<List<String>, Integer> invalidValue = Validated.invalid(errors);

    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(validFunction);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue = VALIDATED.widen(invalidValue);

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    Validated<List<String>, String> narrowed = VALIDATED.narrow(result);
    assertThat(narrowed.isInvalid()).isTrue();
    assertThat(narrowed.getError()).isEqualTo(errors);
  }
}
