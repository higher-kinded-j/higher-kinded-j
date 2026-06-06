// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import static org.higherkindedj.hkt.instances.Witnesses.context;

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
 * Property-based Functor- and Monad-law verification for {@code Context}, sharing the laws spec
 * with {@link ContextFunctorTest} and {@link ContextMonadTest}. Equality runs both sides under the
 * same scoped-value binding via {@link ContextLawFixtures#EQ}.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class ContextMonadPropertyTest {

  private final Monad<ContextKind.Witness<String>> monad = Instances.monad(context());

  @Provide
  Arbitrary<Kind<ContextKind.Witness<String>, Integer>> contextKinds() {
    return ContextArbitraries.contextKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return ContextArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return ContextArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<ContextKind.Witness<String>, String>>> intToContextString() {
    return ContextArbitraries.intToContextString();
  }

  @Provide
  Arbitrary<Function<String, Kind<ContextKind.Witness<String>, String>>> stringToContextString() {
    return ContextArbitraries.stringToContextString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("contextKinds") Kind<ContextKind.Witness<String>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, ContextLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("contextKinds") Kind<ContextKind.Witness<String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, ContextLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToContextString")
          Function<Integer, Kind<ContextKind.Witness<String>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, ContextLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("contextKinds") Kind<ContextKind.Witness<String>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, ContextLawFixtures.EQ);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("contextKinds") Kind<ContextKind.Witness<String>, Integer> m,
      @ForAll("intToContextString") Function<Integer, Kind<ContextKind.Witness<String>, String>> f,
      @ForAll("stringToContextString")
          Function<String, Kind<ContextKind.Witness<String>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, ContextLawFixtures.EQ);
  }
}
