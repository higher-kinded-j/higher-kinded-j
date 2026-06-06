// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

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
import org.higherkindedj.hkt.typeclass.StringMonoid;

/** Property-based Functor- and Monad-law verification for Writer (String log). */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class WriterMonadPropertyTest {

  private final Monad<WriterKind.Witness<String>> monad = Instances.writer(new StringMonoid());

  @Provide
  Arbitrary<Kind<WriterKind.Witness<String>, Integer>> writerKinds() {
    return WriterArbitraries.writerKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return WriterArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return WriterArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<WriterKind.Witness<String>, String>>> intToWriterString() {
    return WriterArbitraries.intToWriterString();
  }

  @Provide
  Arbitrary<Function<String, Kind<WriterKind.Witness<String>, String>>> stringToWriterString() {
    return WriterArbitraries.stringToWriterString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, WriterLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, WriterLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToWriterString") Function<Integer, Kind<WriterKind.Witness<String>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, WriterLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, WriterLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> m,
      @ForAll("intToWriterString") Function<Integer, Kind<WriterKind.Witness<String>, String>> f,
      @ForAll("stringToWriterString")
          Function<String, Kind<WriterKind.Witness<String>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, WriterLawFixtures.EQ);
  }
}
