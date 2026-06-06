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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.reader.ReaderTestBase.TestConfig;

/**
 * Property-based Functor- and Monad-law verification for Reader. Readers are functions, so we drive
 * equality by running both sides against a fixed environment.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ReaderMonadPropertyTest {

  private final Monad<ReaderKind.Witness<TestConfig>> monad = new ReaderMonad<>();

  @Provide
  Arbitrary<Kind<ReaderKind.Witness<TestConfig>, Integer>> readerKinds() {
    return ReaderArbitraries.readerKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ReaderArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return ReaderArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>>> intToReaderString() {
    return ReaderArbitraries.intToReaderString();
  }

  @Provide
  Arbitrary<Function<String, Kind<ReaderKind.Witness<TestConfig>, String>>> stringToReaderString() {
    return ReaderArbitraries.stringToReaderString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("readerKinds") Kind<ReaderKind.Witness<TestConfig>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, ReaderLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("readerKinds") Kind<ReaderKind.Witness<TestConfig>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, ReaderLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToReaderString")
          Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, ReaderLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("readerKinds") Kind<ReaderKind.Witness<TestConfig>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, ReaderLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("readerKinds") Kind<ReaderKind.Witness<TestConfig>, Integer> m,
      @ForAll("intToReaderString")
          Function<Integer, Kind<ReaderKind.Witness<TestConfig>, String>> f,
      @ForAll("stringToReaderString")
          Function<String, Kind<ReaderKind.Witness<TestConfig>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, ReaderLawFixtures.EQ);
  }
}
