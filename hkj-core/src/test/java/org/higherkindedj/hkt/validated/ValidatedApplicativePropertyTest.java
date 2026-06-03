// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.ApplicativeLaws;

/**
 * Property-based Applicative-law verification for Validated, sharing the {@link ApplicativeLaws}
 * spec with {@code ValidatedApplicativeTest}.
 *
 * <p>Beyond the four applicative laws (identity, homomorphism, interchange, composition — all
 * delegated to the shipped spec), this suite pins down Validated's defining behaviour over a {@code
 * List<String>} error channel: {@code ap} <strong>accumulates</strong> errors via a {@link
 * org.higherkindedj.hkt.Semigroup}, unlike fail-fast Either.
 */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class ValidatedApplicativePropertyTest {

  private final MonadError<ValidatedKind.Witness<List<String>>, List<String>> applicative =
      Instances.validated(ValidatedArbitraries.LIST_SEMIGROUP);

  private final BiPredicate<
          Kind<ValidatedKind.Witness<List<String>>, ?>,
          Kind<ValidatedKind.Witness<List<String>>, ?>>
      eq = KindEquivalence.byEqualsAfter(VALIDATED::narrow);

  @Provide
  Arbitrary<Kind<ValidatedKind.Witness<List<String>>, Integer>> validatedKinds() {
    return ValidatedArbitraries.validatedKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    return ValidatedArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  // ---- Applicative laws (delegated to the shared ApplicativeLaws spec) ----

  @Property(tries = 50)
  @Label("Applicative identity: ap(of(id), v) == v")
  void identity(@ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> v) {
    ApplicativeLaws.assertIdentity(applicative, v, eq);
  }

  @Property(tries = 50)
  @Label("Applicative homomorphism: ap(of(f), of(x)) == of(f(x))")
  void homomorphism(
      @ForAll @IntRange(min = -50, max = 50) int x,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {
    ApplicativeLaws.assertHomomorphism(applicative, x, f, eq);
  }

  @Property(tries = 50)
  @Label("Applicative interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
  void interchange(
      @ForAll @IntRange(min = -50, max = 50) int y,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {
    // Exercise both a Valid and an Invalid function position for u.
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> u =
        y % 2 == 0 ? applicative.of(f) : VALIDATED.widen(Validated.invalid(List.of("err: odd")));
    ApplicativeLaws.assertInterchange(applicative, u, y, eq);
  }

  @Property(tries = 50)
  @Label("Applicative composition: ap(ap(ap(of(compose), u), v), w) == ap(u, ap(v, w))")
  void composition(
      @ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> w,
      @ForAll("intToStringFunctions") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> v = applicative.of(f);
    Kind<ValidatedKind.Witness<List<String>>, Function<String, Integer>> u = applicative.of(g);
    ApplicativeLaws.assertComposition(applicative, u, v, w, eq);
  }

  // ---- Validated-specific behaviour: error accumulation in ap ----

  @Example
  @Label("ap accumulates errors when both function and value are Invalid")
  void apAccumulatesErrors() {
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(Validated.invalid(List.of("error: bad function")));
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue =
        VALIDATED.widen(Validated.invalid(List.of("error: bad value")));

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    assertThatValidated(result)
        .isInvalid()
        .hasError(List.of("error: bad function", "error: bad value"));
  }

  @Example
  @Label("map2 accumulates all errors from Invalid values")
  void map2AccumulatesAllErrors() {
    Kind<ValidatedKind.Witness<List<String>>, Integer> kind1 =
        VALIDATED.widen(Validated.invalid(List.of("error: first")));
    Kind<ValidatedKind.Witness<List<String>>, Integer> kind2 =
        VALIDATED.widen(Validated.invalid(List.of("error: second")));

    Kind<ValidatedKind.Witness<List<String>>, Integer> result =
        applicative.map2(kind1, kind2, Integer::sum);

    assertThatValidated(result).isInvalid().hasError(List.of("error: first", "error: second"));
  }

  @Property(tries = 50)
  @Label("ap applies the function when both function and value are Valid")
  void apAppliesWhenBothValid(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(Validated.valid(f));
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue =
        VALIDATED.widen(Validated.valid(value));

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    assertThatValidated(result).isValid().hasValue(f.apply(value));
  }

  @Property(tries = 50)
  @Label("ap propagates an Invalid function even with a Valid value")
  void apPropagatesInvalidFunction(@ForAll @IntRange(min = -50, max = 50) int value) {
    List<String> errors = List.of("error: invalid function");
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(Validated.invalid(errors));
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue =
        VALIDATED.widen(Validated.valid(value));

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    assertThatValidated(result).isInvalid().hasError(errors);
  }

  @Property(tries = 50)
  @Label("ap propagates an Invalid value even with a Valid function")
  void apPropagatesInvalidValue(@ForAll("intToStringFunctions") Function<Integer, String> f) {
    List<String> errors = List.of("error: invalid value");
    Kind<ValidatedKind.Witness<List<String>>, Function<Integer, String>> kindFunction =
        VALIDATED.widen(Validated.valid(f));
    Kind<ValidatedKind.Witness<List<String>>, Integer> kindValue =
        VALIDATED.widen(Validated.invalid(errors));

    Kind<ValidatedKind.Witness<List<String>>, String> result =
        applicative.ap(kindFunction, kindValue);

    assertThatValidated(result).isInvalid().hasError(errors);
  }
}
