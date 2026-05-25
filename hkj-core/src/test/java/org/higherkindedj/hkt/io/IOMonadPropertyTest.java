// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor + Monad law verification for IO, sharing the laws spec with IOMonadTest.
 */
class IOMonadPropertyTest {

  private final Monad<IOKind.Witness> monad = Instances.monad(io());

  // IO equality: run both and compare results. Fixtures must be pure for this to make sense.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private final BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> eq =
      (k1, k2) ->
          Objects.equals(
              IO_OP.narrow((Kind<IOKind.Witness, Object>) (Kind) k1).unsafeRunSync(),
              IO_OP.narrow((Kind<IOKind.Witness, Object>) (Kind) k2).unsafeRunSync());

  @Provide
  Arbitrary<Kind<IOKind.Witness, Integer>> ioKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> IO_OP.widen(IO.delay(() -> i)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<IOKind.Witness, String>>> intToIOString() {
    return Arbitraries.of(
        i -> IO_OP.widen(IO.delay(() -> "a:" + i)),
        i -> IO_OP.widen(IO.delay(() -> "b:" + (i * 2))),
        i -> IO_OP.widen(IO.delay(() -> String.valueOf(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<IOKind.Witness, String>>> stringToIOString() {
    return Arbitraries.of(
        s -> IO_OP.widen(IO.delay(s::toUpperCase)),
        s -> IO_OP.widen(IO.delay(() -> "len:" + s.length())),
        s -> IO_OP.widen(IO.delay(() -> "transformed:" + s)));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("ioKinds") Kind<IOKind.Witness, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("ioKinds") Kind<IOKind.Witness, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToIOString") Function<Integer, Kind<IOKind.Witness, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("ioKinds") Kind<IOKind.Witness, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("ioKinds") Kind<IOKind.Witness, Integer> m,
      @ForAll("intToIOString") Function<Integer, Kind<IOKind.Witness, String>> f,
      @ForAll("stringToIOString") Function<String, Kind<IOKind.Witness, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
