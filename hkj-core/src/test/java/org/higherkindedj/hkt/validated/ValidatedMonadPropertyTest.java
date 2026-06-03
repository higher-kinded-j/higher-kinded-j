// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Monad-law verification for Validated, sharing the laws spec with
 * ValidatedMonadTest.
 */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class ValidatedMonadPropertyTest {

  private final MonadError<ValidatedKind.Witness<List<String>>, List<String>> monad =
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
  Arbitrary<Function<Integer, Kind<ValidatedKind.Witness<List<String>>, String>>>
      intToValidatedString() {
    return Arbitraries.of(
        i ->
            VALIDATED.widen(
                i % 2 == 0 ? Validated.valid("even:" + i) : Validated.invalid(List.of("err: odd"))),
        i ->
            VALIDATED.widen(
                i > 0 ? Validated.valid("pos:" + i) : Validated.invalid(List.of("err: nonpos"))),
        i -> VALIDATED.widen(Validated.valid("value:" + i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<ValidatedKind.Witness<List<String>>, String>>>
      stringToValidatedString() {
    return Arbitraries.of(
        s ->
            VALIDATED.widen(
                s.isEmpty()
                    ? Validated.invalid(List.of("err: empty"))
                    : Validated.valid(s.toUpperCase())),
        s ->
            VALIDATED.widen(
                s.length() > 3
                    ? Validated.valid("long:" + s)
                    : Validated.invalid(List.of("err: short"))),
        s -> VALIDATED.widen(Validated.valid("transformed:" + s)));
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToValidatedString")
          Function<Integer, Kind<ValidatedKind.Witness<List<String>>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("validatedKinds") Kind<ValidatedKind.Witness<List<String>>, Integer> m,
      @ForAll("intToValidatedString")
          Function<Integer, Kind<ValidatedKind.Witness<List<String>>, String>> f,
      @ForAll("stringToValidatedString")
          Function<String, Kind<ValidatedKind.Witness<List<String>>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
