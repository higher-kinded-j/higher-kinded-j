// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.io.IOKind;

/**
 * Property-based tests for JavaOptionalContext using jQwik.
 *
 * <p>Verifies Functor and Monad laws hold across a wide range of inputs.
 */
class JavaOptionalContextPropertyTest {

  @Provide
  Arbitrary<JavaOptionalContext<IOKind.Witness, Integer>> javaOptionalContexts() {
    Arbitrary<JavaOptionalContext<IOKind.Witness, Integer>> somes =
        Arbitraries.integers().between(-1000, 1000).map(JavaOptionalContext::some);
    Arbitrary<JavaOptionalContext<IOKind.Witness, Integer>> nones =
        Arbitraries.just(JavaOptionalContext.none());

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
  Arbitrary<Function<Integer, JavaOptionalContext<IOKind.Witness, String>>>
      intToJavaOptionalContextStringFunctions() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? JavaOptionalContext.some("even:" + i) : JavaOptionalContext.none(),
        i -> i > 0 ? JavaOptionalContext.some("positive:" + i) : JavaOptionalContext.none(),
        i -> JavaOptionalContext.some("value:" + i));
  }

  @Provide
  Arbitrary<Function<String, JavaOptionalContext<IOKind.Witness, String>>>
      stringToJavaOptionalContextStringFunctions() {
    return Arbitraries.of(
        s -> s.isEmpty() ? JavaOptionalContext.none() : JavaOptionalContext.some(s.toUpperCase()),
        s -> s.length() > 3 ? JavaOptionalContext.some("long:" + s) : JavaOptionalContext.none(),
        s -> JavaOptionalContext.some("transformed:" + s));
  }

  // ===== Functor Laws =====

  @Property
  @Label("Functor Identity Law: ctx.map(id) == ctx")
  void functorIdentityLaw(
      @ForAll("javaOptionalContexts") JavaOptionalContext<IOKind.Witness, Integer> ctx) {
    JavaOptionalContext<IOKind.Witness, Integer> result = ctx.map(Function.identity());
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Functor Composition Law: ctx.map(f).map(g) == ctx.map(g.compose(f))")
  void functorCompositionLaw(
      @ForAll("javaOptionalContexts") JavaOptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToIntFunctions") Function<String, Integer> g) {

    JavaOptionalContext<IOKind.Witness, Integer> leftSide = ctx.map(f).map(g);
    JavaOptionalContext<IOKind.Witness, Integer> rightSide = ctx.map(f.andThen(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== Monad Laws =====

  @Property
  @Label("Monad Left Identity Law: JavaOptionalContext.some(a).via(f) == f(a)")
  void leftIdentityLaw(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToJavaOptionalContextStringFunctions")
          Function<Integer, JavaOptionalContext<IOKind.Witness, String>> f) {

    JavaOptionalContext<IOKind.Witness, String> leftSide =
        JavaOptionalContext.<Integer>some(value).via(f);
    JavaOptionalContext<IOKind.Witness, String> rightSide = f.apply(value);

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Right Identity Law: ctx.via(JavaOptionalContext::some) == ctx")
  void rightIdentityLaw(
      @ForAll("javaOptionalContexts") JavaOptionalContext<IOKind.Witness, Integer> ctx) {
    JavaOptionalContext<IOKind.Witness, Integer> result = ctx.flatMap(JavaOptionalContext::some);
    assertThat(result.runIO().unsafeRun()).isEqualTo(ctx.runIO().unsafeRun());
  }

  @Property
  @Label("Monad Associativity Law: ctx.via(f).via(g) == ctx.via(x -> f(x).via(g))")
  void associativityLaw(
      @ForAll("javaOptionalContexts") JavaOptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToJavaOptionalContextStringFunctions")
          Function<Integer, JavaOptionalContext<IOKind.Witness, String>> f,
      @ForAll("stringToJavaOptionalContextStringFunctions")
          Function<String, JavaOptionalContext<IOKind.Witness, String>> g) {

    JavaOptionalContext<IOKind.Witness, String> leftSide = ctx.via(f).via(g);
    JavaOptionalContext<IOKind.Witness, String> rightSide = ctx.via(x -> f.apply(x).via(g));

    assertThat(leftSide.runIO().unsafeRun()).isEqualTo(rightSide.runIO().unsafeRun());
  }

  // ===== MonadError Laws (with Unit as error type) =====

  @Property
  @Label("Left Zero Law: none.via(f) == none")
  void leftZeroLaw(
      @ForAll("intToJavaOptionalContextStringFunctions")
          Function<Integer, JavaOptionalContext<IOKind.Witness, String>> f) {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<IOKind.Witness, String> result = none.via(f);

    assertThat(result.runIO().unsafeRun()).isEmpty();
  }

  @Property
  @Label("Recovery Law: none.recover(f) produces some with f(unit)")
  void recoveryLaw(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<IOKind.Witness, Integer> result = none.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(fallbackValue);
  }

  // ===== Derived Properties =====

  @Property
  @Label("map over none preserves empty")
  void mapPreservesEmpty(@ForAll("intToStringFunctions") Function<Integer, String> f) {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<IOKind.Witness, String> result = none.map(f);

    assertThat(result.runIO().unsafeRun()).isEmpty();
  }

  @Property
  @Label("map over some applies the function")
  void mapAppliesFunctionToSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    JavaOptionalContext<IOKind.Witness, Integer> some = JavaOptionalContext.some(value);
    JavaOptionalContext<IOKind.Witness, String> result = some.map(f);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(f.apply(value));
  }

  @Property
  @Label("recover provides fallback for empty")
  void recoverProvidesFallbackForEmpty(@ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<IOKind.Witness, Integer> result = none.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(fallbackValue);
  }

  @Property
  @Label("recover does not change some")
  void recoverDoesNotChangeSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int fallbackValue) {

    JavaOptionalContext<IOKind.Witness, Integer> some = JavaOptionalContext.some(value);
    JavaOptionalContext<IOKind.Witness, Integer> result = some.recover(unit -> fallbackValue);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(value);
  }

  @Property
  @Label("orElseValue provides default for empty")
  void orElseValueProvidesDefaultForEmpty(
      @ForAll @IntRange(min = -100, max = 100) int defaultValue) {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    JavaOptionalContext<IOKind.Witness, Integer> result = none.orElseValue(defaultValue);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(defaultValue);
  }

  @Property
  @Label("orElseValue does not change some")
  void orElseValueDoesNotChangeSome(
      @ForAll @IntRange(min = -100, max = 100) int value,
      @ForAll @IntRange(min = -100, max = 100) int defaultValue) {

    JavaOptionalContext<IOKind.Witness, Integer> some = JavaOptionalContext.some(value);
    JavaOptionalContext<IOKind.Witness, Integer> result = some.orElseValue(defaultValue);

    assertThat(result.runIO().unsafeRun()).isPresent();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(value);
  }

  @Property
  @Label("via and flatMap are equivalent")
  void viaAndFlatMapEquivalent(
      @ForAll("javaOptionalContexts") JavaOptionalContext<IOKind.Witness, Integer> ctx,
      @ForAll("intToJavaOptionalContextStringFunctions")
          Function<Integer, JavaOptionalContext<IOKind.Witness, String>> f) {

    Optional<String> viaResult = ctx.via(f).runIO().unsafeRun();
    Optional<String> flatMapResult = ctx.flatMap(f).runIO().unsafeRun();

    assertThat(viaResult).isEqualTo(flatMapResult);
  }

  @Property
  @Label("fromOptional preserves Optional structure")
  void fromOptionalPreservesStructure(@ForAll @IntRange(min = -100, max = 100) int value) {

    Optional<Integer> presentOptional = Optional.of(value);
    Optional<Integer> emptyOptional = Optional.empty();

    JavaOptionalContext<IOKind.Witness, Integer> fromPresent =
        JavaOptionalContext.fromOptional(presentOptional);
    JavaOptionalContext<IOKind.Witness, Integer> fromEmpty =
        JavaOptionalContext.fromOptional(emptyOptional);

    assertThat(fromPresent.runIO().unsafeRun()).isEqualTo(presentOptional);
    assertThat(fromEmpty.runIO().unsafeRun()).isEqualTo(emptyOptional);
  }

  @Property
  @Label("toErrorContext converts some to success")
  void toErrorContextConvertsSomeToSuccess(@ForAll @IntRange(min = -100, max = 100) int value) {

    JavaOptionalContext<IOKind.Witness, Integer> some = JavaOptionalContext.some(value);
    ErrorContext<IOKind.Witness, String, Integer> result = some.toErrorContext("not found");

    assertThat(result.runIO().unsafeRun().isRight()).isTrue();
    assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(value);
  }

  @Property
  @Label("toErrorContext converts none to failure with provided error")
  void toErrorContextConvertsNoneToFailure() {

    String error = "not found";
    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    ErrorContext<IOKind.Witness, String, Integer> result = none.toErrorContext(error);

    assertThat(result.runIO().unsafeRun().isLeft()).isTrue();
    assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo(error);
  }

  @Property
  @Label("toOptionalContext converts some to Just")
  void toOptionalContextConvertsSomeToJust(@ForAll @IntRange(min = -100, max = 100) int value) {

    JavaOptionalContext<IOKind.Witness, Integer> some = JavaOptionalContext.some(value);
    OptionalContext<IOKind.Witness, Integer> result = some.toOptionalContext();

    assertThat(result.runIO().unsafeRun().isJust()).isTrue();
    assertThat(result.runIO().unsafeRun().get()).isEqualTo(value);
  }

  @Property
  @Label("toOptionalContext converts none to Nothing")
  void toOptionalContextConvertsNoneToNothing() {

    JavaOptionalContext<IOKind.Witness, Integer> none = JavaOptionalContext.none();
    OptionalContext<IOKind.Witness, Integer> result = none.toOptionalContext();

    assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
  }
}
