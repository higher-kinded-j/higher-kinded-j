// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor + Monad law verification for IO, sharing the laws spec with IOMonadTest.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class IOMonadPropertyTest {

  private final Monad<IOKind.Witness> monad = Instances.monad(io());

  @Provide
  Arbitrary<Kind<IOKind.Witness, Integer>> ioKinds() {
    return IOArbitraries.ioKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return IOArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return IOArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<IOKind.Witness, String>>> intToIOString() {
    return IOArbitraries.intToIOString();
  }

  @Provide
  Arbitrary<Function<String, Kind<IOKind.Witness, String>>> stringToIOString() {
    return IOArbitraries.stringToIOString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("ioKinds") Kind<IOKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, IOLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("ioKinds") Kind<IOKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, IOLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToIOString") Function<Integer, Kind<IOKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, IOLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("ioKinds") Kind<IOKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, IOLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("ioKinds") Kind<IOKind.Witness, Integer> m,
      @ForAll("intToIOString") Function<Integer, Kind<IOKind.Witness, String>> f,
      @ForAll("stringToIOString") Function<String, Kind<IOKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, IOLawFixtures.EQ);
  }
}
