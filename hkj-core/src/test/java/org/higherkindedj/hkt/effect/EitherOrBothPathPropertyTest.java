// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;

/**
 * Property-based Functor + Monad (short-circuit {@code via}) law verification for {@link
 * EitherOrBothPath}, across {@code Left}/{@code Right}/{@code Both} inputs. Associativity holds
 * because the warning semigroup (string concatenation) is associative.
 */
class EitherOrBothPathPropertyTest {

  private static final Semigroup<String> SG = (a, b) -> a + b;

  @Provide
  Arbitrary<EitherOrBothPath<String, Integer>> paths() {
    Arbitrary<Integer> ints = Arbitraries.integers().between(-500, 500);
    Arbitrary<String> warns = Arbitraries.of("w1", "w2", "warn");
    return Arbitraries.oneOf(
        ints.map(i -> Path.right(i, SG)),
        warns.map(w -> Path.<String, Integer>left(w, SG)),
        Combinators.combine(warns, ints).as((w, i) -> Path.both(w, i, SG)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, EitherOrBothPath<String, String>>> intToPath() {
    return Arbitraries.of(
        i -> Path.right("a" + i, SG),
        i -> Path.left("L" + i, SG),
        i -> Path.both("b" + i, "v" + i, SG));
  }

  @Provide
  Arbitrary<Function<String, EitherOrBothPath<String, String>>> stringToPath() {
    return Arbitraries.of(
        s -> Path.right(s.toUpperCase(), SG),
        s -> Path.left("E" + s, SG),
        s -> Path.both("x" + s, s + "!", SG));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id) preserves the path")
  void functorIdentity(@ForAll("paths") EitherOrBothPath<String, Integer> p) {
    assertThat(p.map(Function.identity()).run()).isEqualTo(p.run());
  }

  @Property(tries = 50)
  @Label("Functor composition: map(f).map(g) == map(f.andThen(g))")
  void functorComposition(
      @ForAll("paths") EitherOrBothPath<String, Integer> p,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    assertThat(p.map(f).map(g).run()).isEqualTo(p.map(f.andThen(g)).run());
  }

  @Property(tries = 50)
  @Label("Monad left identity: Path.right(a).via(f) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int a,
      @ForAll("intToPath") Function<Integer, EitherOrBothPath<String, String>> f) {
    EitherOrBoth<String, String> lhs = Path.right(a, SG).via(f::apply).run();
    assertThat(lhs).isEqualTo(f.apply(a).run());
  }

  @Property(tries = 50)
  @Label("Monad right identity: p.via(Path::right) == p")
  void rightIdentity(@ForAll("paths") EitherOrBothPath<String, Integer> p) {
    assertThat(p.via(x -> Path.right(x, SG)).run()).isEqualTo(p.run());
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("paths") EitherOrBothPath<String, Integer> p,
      @ForAll("intToPath") Function<Integer, EitherOrBothPath<String, String>> f,
      @ForAll("stringToPath") Function<String, EitherOrBothPath<String, String>> g) {
    EitherOrBoth<String, String> lhs = p.via(f::apply).via(g::apply).run();
    EitherOrBoth<String, String> rhs = p.via(x -> f.apply(x).via(g::apply)).run();
    assertThat(lhs).isEqualTo(rhs);
  }
}
