// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.SelectiveLaws;

/**
 * Property-based Selective-law verification for Either, complementing the unit-level
 * EitherSelective laws spec.
 */
// jqwik invokes the @Provide methods reflectively via @ForAll(name); IntelliJ cannot see those
// uses.
@SuppressWarnings("unused")
class EitherSelectivePropertyTest {

  private final EitherSelective<String> selective = EitherSelective.instance();

  private final BiPredicate<
          Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      eq = KindEquivalence.byEqualsAfter(EITHER::narrow);

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return EitherArbitraries.intToString();
  }

  @Property(tries = 50)
  @Label("Selective left-pure: select(of(Left(a)), of(f)) == ap(of(f), of(a))")
  void leftPure(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.assertLeftPure(selective, value, selective.of(f), eq);
  }

  @Property(tries = 50)
  @Label("Selective right-pure: select(of(Right(b)), of(f)) == of(b)")
  void rightPure(
      @ForAll @StringLength(max = 5) String value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.assertRightPure(selective, value, selective.of(f), eq);
  }
}
