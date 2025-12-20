// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Property-based tests for OptionalContext using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class OptionalContextPropertyTest {

  @Provide
  Arbitrary<OptionalContext<IOKind.Witness, Integer>> optionalContexts() {
    Arbitrary<OptionalContext<IOKind.Witness, Integer>> somes =
        Arbitraries.integers().between(-1000, 1000).map(OptionalContext::some);
    Arbitrary<OptionalContext<IOKind.Witness, Integer>> nones =
        Arbitraries.just(OptionalContext.none());

    return Arbitraries.frequencyOf(Tuple.of(4, somes), Tuple.of(1, nones));
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
  Arbitrary<Function<Integer, OptionalContext<IOKind.Witness, String>>>
      intToOptionalContextStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? OptionalContext.some("even:" + i) : OptionalContext.none(),
        i -> i > 0 ? OptionalContext.some("positive:" + i) : OptionalContext.none(),
        i -> OptionalContext.some("value:" + i));
  }

  @Provide
  Arbitrary<Function<String, OptionalContext<IOKind.Witness, String>>>
      stringToOptionalContextStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? OptionalContext.none() : OptionalContext.some(s.toUpperCase()),
        s -> s.length() > 3 ? OptionalContext.some("long:" + s) : OptionalContext.none(),
        s -> OptionalContext.some("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: ctx.map(id) == ctx")
  void functorIdentityLaw(
      @ForAll("optionalContexts") OptionalContext<IOKind.Witness, Integer> ctx) {
    OptionalContext<IOKind.Witness, Integer> result = ctx.map(Function.identity());
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Functor Composition Law: ctx.map(f).map(g) == ctx.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("optionalContexts") OptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    OptionalContext<IOKind.Witness, Integer> leftSide = ctx.map(f).map(g);
    OptionalContext<IOKind.Witness, Integer> rightSide = ctx.map(f.andThen(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: OptionalContext.some(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToOptionalContextStringFunctions")
          Function<Integer, OptionalContext<IOKind.Witness, String>> f) {

    OptionalContext<IOKind.Witness, String> leftSide = OptionalContext.<Integer>some(value).via(f);
    OptionalContext<IOKind.Witness, String> rightSide = f.apply(value);

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Right Identity Law: ctx.via(OptionalContext::some) == ctx")
  void rightIdentityLaw(@ForAll("optionalContexts") OptionalContext<IOKind.Witness, Integer> ctx) {
    OptionalContext<IOKind.Witness, Integer> result = ctx.flatMap(OptionalContext::some);
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Associativity Law: ctx.via(f).via(g) == ctx.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("optionalContexts") OptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToOptionalContextStringFunctions")
          Function<Integer, OptionalContext<IOKind.Witness, String>> f,
      @ForAll("stringToOptionalContextStringFunctions")
          Function<String, OptionalContext<IOKind.Witness, String>> g) {

    OptionalContext<IOKind.Witness, String> leftSide = ctx.via(f).via(g);
    OptionalContext<IOKind.Witness, String> rightSide = ctx.via(x -> f.apply(x).via(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== MonadError Laws (with Unit as error type) =====

  @Property
  @Label("Left Zero Law: none.via(f) == none")
  void leftZeroLaw(
      @ForAll("intToOptionalContextStringFunctions")
          Function<Integer, OptionalContext<IOKind.Witness, String>> f) {

    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    OptionalContext<IOKind.Witness, String> result = none.via(f);

    assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
  }

  @Property
  @Label("Recovery Law: none.recover(f) produces some with f(unit)")
  void recoveryLaw(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    OptionalContext<IOKind.Witness, Integer> result = none.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(fallbackValue);
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over none preserves nothing")
  void mapPreservesNothing(@ForAll("intToStringFunctions") Function<Integer, String> f) {

    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    OptionalContext<IOKind.Witness, String> result = none.map(f);

    assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
  }

  @Property
  @Label("map over some applies the function")
  void mapAppliesFunctionToSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    OptionalContext<IOKind.Witness, Integer> some = OptionalContext.some(value);
    OptionalContext<IOKind.Witness, String> result = some.map(f);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("recover provides fallback for none")
  void recoverProvidesFallbackForNone(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    OptionalContext<IOKind.Witness, Integer> result = none.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change some")
  void recoverDoesNotChangeSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    OptionalContext<IOKind.Witness, Integer> some = OptionalContext.some(value);
    OptionalContext<IOKind.Witness, Integer> result = some.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(value);
  }

  @Property
  @Label("orElseValue provides default for none")
  void orElseValueProvidesDefaultForNone(
      @ForAll @IntRange(min = -100, max = 100) int defaultValue) {

    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    OptionalContext<IOKind.Witness, Integer> result = none.orElseValue(defaultValue);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(defaultValue);
  }

  @Property
  @Label("orElseValue does not change some")
  void orElseValueDoesNotChangeSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int defaultValue) {

    OptionalContext<IOKind.Witness, Integer> some = OptionalContext.some(value);
    OptionalContext<IOKind.Witness, Integer> result = some.orElseValue(defaultValue);

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(value);
  }

  @Property
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("optionalContexts") OptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToOptionalContextStringFunctions")
          Function<Integer, OptionalContext<IOKind.Witness, String>> f) {

    Maybe<String> viaResult = ctx.via(f).runIO().unsafeRun();
    Maybe<String> flatMapResult = ctx.flatMap(f).runIO().unsafeRun();

    assertThat(viaResult).isEqualTo(flatMapResult);
  }

  @Property
  @Label("fromMaybe preserves Maybe structure")
  void fromMaybePreservesStructure(@ForAll @IntRange(min = -100, max = 100) int value) {

    Maybe<Integer> justMaybe = Maybe.just(value);
    Maybe<Integer> nothingMaybe = Maybe.nothing();

    OptionalContext<IOKind.Witness, Integer> fromJust = OptionalContext.fromMaybe(justMaybe);
    OptionalContext<IOKind.Witness, Integer> fromNothing = OptionalContext.fromMaybe(nothingMaybe);

    assertThat(fromJust.runIO().unsafeRun()).isEqualTo(justMaybe);
    assertThat(fromNothing.runIO().unsafeRun()).isEqualTo(nothingMaybe);
  }

  @Property
  @Label("toErrorContext converts some to success")
  void toErrorContextConvertsSomeToSuccess(@ForAll @IntRange(min = -100, max = 100) int value) {

    OptionalContext<IOKind.Witness, Integer> some = OptionalContext.some(value);
    ErrorContext<IOKind.Witness, String, Integer> result = some.toErrorContext("not found");

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(value);
  }

  @Property
  @Label("toErrorContext converts none to failure with provided error")
  void toErrorContextConvertsNoneToFailure() {

    String error = "not found";
    OptionalContext<IOKind.Witness, Integer> none = OptionalContext.none();
    ErrorContext<IOKind.Witness, String, Integer> result = none.toErrorContext(error);

    assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
    assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(error);
  }
}
