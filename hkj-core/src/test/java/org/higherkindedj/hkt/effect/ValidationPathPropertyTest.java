// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.higherkindedj.hkt.Semigroup;

/**
 * Property-based tests for ValidationPath using jQwik.
 *
 * <p>Verifies Functor laws, Monad laws (short-circuit via()), and Applicative laws (accumulating
 * zipWithAccum()) hold across a wide range of inputs.
 */
class ValidationPathPropertyTest {

  private static final Semigroup<String> STRING_SEMIGROUP = (a, b) -> a + ", " + b;

  @Provide
  Arbitrary<ValidationPath<String, Integer>> validationPaths() {
    return Arbitraries.oneOf(
        // Valid paths
        Arbitraries.integers().between(-1000, 1000).map(i -> Path.valid(i, STRING_SEMIGROUP)),
        // Invalid paths
        Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(20)
            .map(s -> Path.invalid(s, STRING_SEMIGROUP)));
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

  @Provide
  Arbitrary<Function<Integer, ValidationPath<String, String>>> intToValidationStringFunctions() {
    return Arbitraries.of(
        i ->
            i % 2 == 0
                ? Path.valid("even:" + i, STRING_SEMIGROUP)
                : Path.invalid("odd", STRING_SEMIGROUP),
        i ->
            i > 0
                ? Path.valid("positive:" + i, STRING_SEMIGROUP)
                : Path.invalid("negative", STRING_SEMIGROUP),
        i -> Path.valid("value:" + i, STRING_SEMIGROUP),
        i ->
            i == 0 ? Path.invalid("zero", STRING_SEMIGROUP) : Path.valid("" + i, STRING_SEMIGROUP));
  }

  @Provide
  Arbitrary<Function<String, ValidationPath<String, String>>> stringToValidationStringFunctions() {
    return Arbitraries.of(
        s ->
            s.isEmpty()
                ? Path.invalid("empty", STRING_SEMIGROUP)
                : Path.valid(s.toUpperCase(), STRING_SEMIGROUP),
        s ->
            s.length() > 3
                ? Path.valid("long:" + s, STRING_SEMIGROUP)
                : Path.invalid("short", STRING_SEMIGROUP),
        s -> Path.valid("transformed:" + s, STRING_SEMIGROUP));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: path.map(id) == path")
  void functorIdentityLaw(@ForAll("validationPaths") ValidationPath<String, Integer> path) {
    ValidationPath<String, Integer> result = path.map(Function.identity());
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Functor Composition Law: path.map(f).map(g) == path.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("validationPaths") ValidationPath<String, Integer> path,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    ValidationPath<String, Integer> leftSide = path.map(f).map(g);
    ValidationPath<String, Integer> rightSide = path.map(f.andThen(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Monad Laws (via short-circuits) =====

  @Property
  @Label("Monad Left Identity Law: Path.valid(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToValidationStringFunctions")
          Function<Integer, ValidationPath<String, String>> f) {

    ValidationPath<String, String> leftSide = Path.valid(value, STRING_SEMIGROUP).via(f);
    ValidationPath<String, String> rightSide = f.apply(value);

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  @Property
  @Label("Monad Right Identity Law: path.via(a -> Path.valid(a)) == path")
  void rightIdentityLaw(@ForAll("validationPaths") ValidationPath<String, Integer> path) {
    ValidationPath<String, Integer> result = path.via(a -> Path.valid(a, STRING_SEMIGROUP));
    assertThat(result.run()).isEqualTo(path.run());
  }

  @Property
  @Label("Monad Associativity Law: path.via(f).via(g) == path.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("validationPaths") ValidationPath<String, Integer> path,
      @ForAll("intToValidationStringFunctions") Function<Integer, ValidationPath<String, String>> f,
      @ForAll("stringToValidationStringFunctions")
          Function<String, ValidationPath<String, String>> g) {

    ValidationPath<String, String> leftSide = path.via(f).via(g);
    ValidationPath<String, String> rightSide = path.via(x -> f.apply(x).via(g));

    assertThat(leftSide.run()).isEqualTo(rightSide.run());
  }

  // ===== Accumulating Properties =====

  @Property
  @Label("zipWithAccum combines Valid values")
  void zipWithAccumCombinesValidValues(
      @ForAll @IntRange(min = -100, max = 100) int a,
      @ForAll @IntRange(min = -100, max = 100) int b) {

    ValidationPath<String, Integer> pathA = Path.valid(a, STRING_SEMIGROUP);
    ValidationPath<String, Integer> pathB = Path.valid(b, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = pathA.zipWithAccum(pathB, Integer::sum);

    assertThat(result.run().isValid()).isTrue();
    assertThat(result.run().get()).isEqualTo(a + b);
  }

  @Property
  @Label("zipWithAccum accumulates errors from both Invalid paths")
  void zipWithAccumAccumulatesErrors(
      @ForAll @StringLength(min = 1, max = 10) String error1,
      @ForAll @StringLength(min = 1, max = 10) String error2) {

    ValidationPath<String, Integer> pathA = Path.invalid(error1, STRING_SEMIGROUP);
    ValidationPath<String, Integer> pathB = Path.invalid(error2, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = pathA.zipWithAccum(pathB, Integer::sum);

    assertThat(result.run().isInvalid()).isTrue();
    assertThat(result.run().getError()).isEqualTo(error1 + ", " + error2);
  }

  @Property
  @Label("zipWithAccum returns error when only first is Invalid")
  void zipWithAccumFirstInvalid(
      @ForAll @StringLength(min = 1, max = 10) String error,
      @ForAll @IntRange(min = -100, max = 100) int value) {

    ValidationPath<String, Integer> pathA = Path.invalid(error, STRING_SEMIGROUP);
    ValidationPath<String, Integer> pathB = Path.valid(value, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = pathA.zipWithAccum(pathB, Integer::sum);

    assertThat(result.run().isInvalid()).isTrue();
    assertThat(result.run().getError()).isEqualTo(error);
  }

  @Property
  @Label("zipWithAccum returns error when only second is Invalid")
  void zipWithAccumSecondInvalid(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @StringLength(min = 1, max = 10) String error) {

    ValidationPath<String, Integer> pathA = Path.valid(value, STRING_SEMIGROUP);
    ValidationPath<String, Integer> pathB = Path.invalid(error, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = pathA.zipWithAccum(pathB, Integer::sum);

    assertThat(result.run().isInvalid()).isTrue();
    assertThat(result.run().getError()).isEqualTo(error);
  }

  // ===== via vs zipWithAccum Semantics =====

  @Property
  @Label("via short-circuits on first Invalid (unlike zipWithAccum)")
  void viaShortCircuitsOnFirstInvalid(@ForAll @StringLength(min = 1, max = 10) String error1) {

    ValidationPath<String, Integer> invalid = Path.invalid(error1, STRING_SEMIGROUP);

    // via should not execute the function, just return the error
    ValidationPath<String, String> result =
        invalid.via(i -> Path.invalid("second error", STRING_SEMIGROUP));

    assertThat(result.run().isInvalid()).isTrue();
    // Should only contain the first error due to short-circuiting
    assertThat(result.run().getError()).isEqualTo(error1);
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over Invalid returns Invalid with same error")
  void mapPreservesInvalid(
      @ForAll @StringLength(min = 1, max = 10) String error,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    ValidationPath<String, Integer> invalid = Path.invalid(error, STRING_SEMIGROUP);
    ValidationPath<String, String> result = invalid.map(f);

    assertThat(result.run().isInvalid()).isTrue();
    assertThat(result.run().getError()).isEqualTo(error);
  }

  @Property
  @Label("map over Valid applies the function")
  void mapAppliesFunctionToValid(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    ValidationPath<String, Integer> valid = Path.valid(value, STRING_SEMIGROUP);
    ValidationPath<String, String> result = valid.map(f);

    assertThat(result.run().isValid()).isTrue();
    assertThat(result.run().get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("recover provides fallback for Invalid")
  void recoverProvidesFallbackForInvalid(
      @ForAll @StringLength(min = 1, max = 10) String error,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    ValidationPath<String, Integer> invalid = Path.invalid(error, STRING_SEMIGROUP);
    ValidationPath<String, Integer> result = invalid.recover(e -> fallbackValue);

    assertThat(result.run().isValid()).isTrue();
    assertThat(result.run().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change Valid")
  void recoverDoesNotChangeValid(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    ValidationPath<String, Integer> valid = Path.valid(value, STRING_SEMIGROUP);
    ValidationPath<String, Integer> result = valid.recover(e -> fallbackValue);

    assertThat(result.run().isValid()).isTrue();
    assertThat(result.run().get()).isEqualTo(value);
  }

  @Property
  @Label("andAlso keeps this value when both Valid")
  void andAlsoKeepsThisValueWhenBothValid(
      @ForAll @IntRange(min = -100, max = 100) int value1,
      @ForAll @IntRange(min = -100, max = 100) int value2) {

    ValidationPath<String, Integer> path1 = Path.valid(value1, STRING_SEMIGROUP);
    ValidationPath<String, Integer> path2 = Path.valid(value2, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = path1.andAlso(path2);

    assertThat(result.run().isValid()).isTrue();
    assertThat(result.run().get()).isEqualTo(value1);
  }

  @Property
  @Label("andAlso accumulates errors from both Invalid")
  void andAlsoAccumulatesErrors(
      @ForAll @StringLength(min = 1, max = 10) String error1,
      @ForAll @StringLength(min = 1, max = 10) String error2) {

    ValidationPath<String, Integer> path1 = Path.invalid(error1, STRING_SEMIGROUP);
    ValidationPath<String, Integer> path2 = Path.invalid(error2, STRING_SEMIGROUP);

    ValidationPath<String, Integer> result = path1.andAlso(path2);

    assertThat(result.run().isInvalid()).isTrue();
    assertThat(result.run().getError()).isEqualTo(error1 + ", " + error2);
  }

  @Property(tries = 50)
  @Label("Multiple maps compose correctly")
  void multipleMapsCompose(@ForAll("validationPaths") ValidationPath<String, Integer> path) {
    Function<Integer, Integer> addOne = x -> x + 1;
    Function<Integer, Integer> doubleIt = x -> x * 2;
    Function<Integer, Integer> subtract3 = x -> x - 3;

    ValidationPath<String, Integer> stepByStep = path.map(addOne).map(doubleIt).map(subtract3);
    ValidationPath<String, Integer> composed =
        path.map(x -> subtract3.apply(doubleIt.apply(addOne.apply(x))));

    assertThat(stepByStep.run()).isEqualTo(composed.run());
  }
}
