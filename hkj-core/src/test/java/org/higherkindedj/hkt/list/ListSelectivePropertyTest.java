// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

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
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.SelectiveLaws;

/** Property-based Selective-law verification for List. */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ListSelectivePropertyTest {

  private final Selective<ListKind.Witness> selective = ListSelective.INSTANCE;

  private final BiPredicate<Kind<ListKind.Witness, ?>, Kind<ListKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(LIST::narrow);

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ListArbitraries.intToString();
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
