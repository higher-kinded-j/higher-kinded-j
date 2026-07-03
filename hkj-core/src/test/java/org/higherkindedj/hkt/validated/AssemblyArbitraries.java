// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/** Shared jqwik generators for the assembly property tests. */
final class AssemblyArbitraries {

  private AssemblyArbitraries() {}

  static Arbitrary<Integer> values() {
    return Arbitraries.integers().between(-50, 50);
  }

  static Arbitrary<String> labels() {
    return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5);
  }

  private static Arbitrary<NonEmptyList<String>> errorNels() {
    return labels()
        .list()
        .ofMinSize(1)
        .ofMaxSize(3)
        .map(list -> NonEmptyList.of(list.getFirst(), list.subList(1, list.size())));
  }

  /** A valid Integer, or an Invalid carrying one to three errors. */
  static Arbitrary<Validated<NonEmptyList<String>, Integer>> validateds() {
    return Arbitraries.oneOf(
        values().map(Validated::validNel), errorNels().map(Validated::invalid));
  }

  /** Right, Both (one warning), or Left (one warning). */
  static Arbitrary<EitherOrBoth<NonEmptyList<String>, Integer>> eitherOrBoths() {
    Arbitrary<EitherOrBoth<NonEmptyList<String>, Integer>> rights =
        values().map(EitherOrBoth::right);
    Arbitrary<EitherOrBoth<NonEmptyList<String>, Integer>> boths =
        labels().flatMap(w -> values().map(v -> EitherOrBoth.both(NonEmptyList.single(w), v)));
    Arbitrary<EitherOrBoth<NonEmptyList<String>, Integer>> lefts =
        labels().map(w -> EitherOrBoth.left(NonEmptyList.single(w)));
    return Arbitraries.oneOf(rights, boths, lefts);
  }

  /** The errors carried by a Validated, in order; empty when valid. */
  static List<String> errorsOf(Validated<NonEmptyList<String>, ?> validated) {
    return validated.fold(NonEmptyList::toJavaList, _ -> List.<String>of());
  }

  /** The warnings carried by an EitherOrBoth, in order; empty for a pure Right. */
  static List<String> warningsOf(EitherOrBoth<NonEmptyList<String>, ?> value) {
    return value.fold(
        NonEmptyList::toJavaList, _ -> List.<String>of(), (nel, _) -> nel.toJavaList());
  }
}
