// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.higherkindedj.hkt.laws.SelectiveLaws;
import org.higherkindedj.hkt.reader.ReaderTestBase.TestConfig;

/**
 * Property-based Selective-law verification for Reader. Readers are functions, so equality is
 * driven by running both sides against a fixed environment.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ReaderSelectivePropertyTest {

  private final ReaderSelective<TestConfig> selective = ReaderSelective.instance();

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ReaderArbitraries.intToString();
  }

  @Property(tries = 50)
  @Label("Selective left-pure: select(of(Left(a)), of(f)) == ap(of(f), of(a))")
  void leftPure(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.assertLeftPure(selective, value, selective.of(f), ReaderLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Selective right-pure: select(of(Right(b)), of(f)) == of(b)")
  void rightPure(
      @ForAll @StringLength(max = 5) String value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.assertRightPure(selective, value, selective.of(f), ReaderLawFixtures.EQ);
  }
}
