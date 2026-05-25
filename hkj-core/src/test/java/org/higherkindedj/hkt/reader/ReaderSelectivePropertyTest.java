// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.SelectiveLaws;

/**
 * Property-based Selective-law verification for Reader. Readers are functions, so equality is
 * driven by running both sides against a fixed environment.
 */
class ReaderSelectivePropertyTest {

  record TestEnv(String url, int max) {}

  private static final TestEnv ENV = new TestEnv("jdbc:test", 10);

  private final ReaderSelective<TestEnv> selective = ReaderSelective.instance();

  private final BiPredicate<
          Kind<ReaderKind.Witness<TestEnv>, ?>, Kind<ReaderKind.Witness<TestEnv>, ?>>
      eq = (k1, k2) -> Objects.equals(READER.runReader(k1, ENV), READER.runReader(k2, ENV));

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Property(tries = 50)
  @Label("Selective left-pure: select(of(Left(a)), of(f)) == ap(of(f), of(a))")
  void leftPure(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.<ReaderKind.Witness<TestEnv>, Integer, String>assertLeftPure(
        selective, value, selective.of(f), eq);
  }

  @Property(tries = 50)
  @Label("Selective right-pure: select(of(Right(b)), of(f)) == of(b)")
  void rightPure(
      @ForAll @StringLength(max = 5) String value,
      @ForAll("intToString") Function<Integer, String> f) {
    SelectiveLaws.<ReaderKind.Witness<TestEnv>, Integer, String>assertRightPure(
        selective, value, selective.of(f), eq);
  }
}
