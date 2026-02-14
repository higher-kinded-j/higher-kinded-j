// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;

/**
 * Property-based tests for ErrorContext using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class ErrorContextPropertyTest {

  @Provide
  Arbitrary<ErrorContext<IOKind.Witness, String, Integer>> errorContexts() {
    Arbitrary<ErrorContext<IOKind.Witness, String, Integer>> successes =
        Arbitraries.integers().between(-1000, 1000).map(ErrorContext::success);
    Arbitrary<ErrorContext<IOKind.Witness, String, Integer>> failures =
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).map(ErrorContext::failure);

    return Arbitraries.frequencyOf(Tuple.of(4, successes), Tuple.of(1, failures));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return Arbitraries.of(i -> "value:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToIntFunctions() {
    return Arbitraries.of(String::length, String::hashCode, s -> s.isEmpty() ? 0 : 1);
  }

  @Provide
  Arbitrary<Function<Integer, ErrorContext<IOKind.Witness, String, String>>>
      intToErrorContextStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? ErrorContext.success("even:" + i) : ErrorContext.failure("odd"),
        i -> i > 0 ? ErrorContext.success("positive:" + i) : ErrorContext.failure("not positive"),
        i -> ErrorContext.success("value:" + i));
  }

  @Provide
  Arbitrary<Function<String, ErrorContext<IOKind.Witness, String, String>>>
      stringToErrorContextStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? ErrorContext.failure("empty") : ErrorContext.success(s.toUpperCase()),
        s -> s.length() > 3 ? ErrorContext.success("long:" + s) : ErrorContext.failure("too short"),
        s -> ErrorContext.success("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: ctx.map(id) == ctx")
  void functorIdentityLaw(
      @ForAll("errorContexts") ErrorContext<IOKind.Witness, String, Integer> ctx) {
    ErrorContext<IOKind.Witness, String, Integer> result = ctx.map(Function.identity());
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Functor Composition Law: ctx.map(f).map(g) == ctx.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("errorContexts") ErrorContext<IOKind.Witness, String, Integer> ctx,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    ErrorContext<IOKind.Witness, String, Integer> leftSide = ctx.map(f).map(g);
    ErrorContext<IOKind.Witness, String, Integer> rightSide = ctx.map(f.andThen(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: ErrorContext.success(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToErrorContextStringFunctions")
          Function<Integer, ErrorContext<IOKind.Witness, String, String>> f) {

    ErrorContext<IOKind.Witness, String, String> leftSide =
        ErrorContext.<String, Integer>success(value).via(f);
    ErrorContext<IOKind.Witness, String, String> rightSide = f.apply(value);

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Right Identity Law: ctx.via(ErrorContext::success) == ctx")
  void rightIdentityLaw(
      @ForAll("errorContexts") ErrorContext<IOKind.Witness, String, Integer> ctx) {
    ErrorContext<IOKind.Witness, String, Integer> result = ctx.flatMap(ErrorContext::success);
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Associativity Law: ctx.via(f).via(g) == ctx.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("errorContexts") ErrorContext<IOKind.Witness, String, Integer> ctx,
      @ForAll("intToErrorContextStringFunctions")
          Function<Integer, ErrorContext<IOKind.Witness, String, String>> f,
      @ForAll("stringToErrorContextStringFunctions")
          Function<String, ErrorContext<IOKind.Witness, String, String>> g) {

    ErrorContext<IOKind.Witness, String, String> leftSide = ctx.via(f).via(g);
    ErrorContext<IOKind.Witness, String, String> rightSide = ctx.via(x -> f.apply(x).via(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== MonadError Laws =====

  @Property
  @Label("Left Zero Law: failure.via(f) == failure")
  void leftZeroLaw(
      @ForAll("intToErrorContextStringFunctions")
          Function<Integer, ErrorContext<IOKind.Witness, String, String>> f) {

    String error = "test error";
    ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure(error);
    ErrorContext<IOKind.Witness, String, String> result = failure.via(f);

    assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
    assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(error);
  }

  @Property
  @Label("Recovery Law: failure.recover(f) produces success with f(error)")
  void recoveryLaw(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    String error = "test error";
    ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure(error);
    ErrorContext<IOKind.Witness, String, Integer> result = failure.recover(e -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(fallbackValue);
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over failure preserves the error")
  void mapPreservesFailure(
      @ForAll @IntRange(min = 1, max = 10) int errorLength,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    String error = "e".repeat(errorLength);
    ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure(error);
    ErrorContext<IOKind.Witness, String, String> result = failure.map(f);

    assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
    assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(error);
  }

  @Property
  @Label("map over success applies the function")
  void mapAppliesFunctionToSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    ErrorContext<IOKind.Witness, String, Integer> success = ErrorContext.success(value);
    ErrorContext<IOKind.Witness, String, String> result = success.map(f);

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("recover provides fallback for failure")
  void recoverProvidesFallbackForFailure(
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure("error");
    ErrorContext<IOKind.Witness, String, Integer> result = failure.recover(e -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change success")
  void recoverDoesNotChangeSuccess(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    ErrorContext<IOKind.Witness, String, Integer> success = ErrorContext.success(value);
    ErrorContext<IOKind.Witness, String, Integer> result = success.recover(e -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(value);
  }

  @Property
  @Label("mapError transforms the error type")
  void mapErrorTransformsError(@ForAll @IntRange(min = 1, max = 10) int errorLength) {

    String error = "e".repeat(errorLength);
    ErrorContext<IOKind.Witness, String, Integer> failure = ErrorContext.failure(error);
    ErrorContext<IOKind.Witness, Integer, Integer> result = failure.mapError(String::length);

    assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
    assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(errorLength);
  }

  @Property
  @Label("mapError preserves success value")
  void mapErrorPreservesSuccess(@ForAll @IntRange(min = -100, max = 100) int value) {

    ErrorContext<IOKind.Witness, String, Integer> success = ErrorContext.success(value);
    ErrorContext<IOKind.Witness, Integer, Integer> result = success.mapError(String::length);

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(value);
  }

  @Property
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("errorContexts") ErrorContext<IOKind.Witness, String, Integer> ctx,
      @ForAll("intToErrorContextStringFunctions")
          Function<Integer, ErrorContext<IOKind.Witness, String, String>> f) {

    Either<String, String> viaResult = ctx.via(f).runIO().unsafeRun();
    Either<String, String> flatMapResult = ctx.flatMap(f).runIO().unsafeRun();

    assertThat(viaResult).isEqualTo(flatMapResult);
  }

  @Property
  @Label("fromEither preserves Either structure")
  void fromEitherPreservesStructure(@ForAll @IntRange(min = -100, max = 100) int value) {

    Either<String, Integer> rightEither = Either.right(value);
    Either<String, Integer> leftEither = Either.left("error");

    ErrorContext<IOKind.Witness, String, Integer> fromRight = ErrorContext.fromEither(rightEither);
    ErrorContext<IOKind.Witness, String, Integer> fromLeft = ErrorContext.fromEither(leftEither);

    assertThat(fromRight.runIO().unsafeRun()).isEqualTo(rightEither);
    assertThat(fromLeft.runIO().unsafeRun()).isEqualTo(leftEither);
  }
}
