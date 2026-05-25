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
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/**
 * Property-based Functor- and Monad-law verification for Reader. Readers are functions, so we drive
 * equality by running both sides against a fixed environment.
 */
class ReaderMonadPropertyTest {

  record TestEnv(String url, int max) {}

  private static final TestEnv ENV = new TestEnv("jdbc:test", 10);

  private final Monad<ReaderKind.Witness<TestEnv>> monad = new ReaderMonad<>();

  private final BiPredicate<
          Kind<ReaderKind.Witness<TestEnv>, ?>, Kind<ReaderKind.Witness<TestEnv>, ?>>
      eq = (k1, k2) -> Objects.equals(READER.runReader(k1, ENV), READER.runReader(k2, ENV));

  @Provide
  Arbitrary<Kind<ReaderKind.Witness<TestEnv>, Integer>> readerKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(
            i ->
                Arbitraries.of(
                    (Function<TestEnv, Integer>) env -> i,
                    env -> i + env.max(),
                    env -> i ^ env.url().length()))
        .flatMap(arb -> arb.map(fn -> READER.widen(Reader.of(fn))));
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
  Arbitrary<Function<Integer, Kind<ReaderKind.Witness<TestEnv>, String>>> intToReaderString() {
    return Arbitraries.of(
        i -> READER.widen(Reader.of(env -> "v:" + i)),
        i -> READER.widen(Reader.of(env -> i + ":" + env.url())),
        i -> READER.widen(Reader.of(env -> String.valueOf(i + env.max()))));
  }

  @Provide
  Arbitrary<Function<String, Kind<ReaderKind.Witness<TestEnv>, String>>> stringToReaderString() {
    return Arbitraries.of(
        s -> READER.widen(Reader.of(env -> s + "!")),
        s -> READER.widen(Reader.of(env -> s.toUpperCase())),
        s -> READER.widen(Reader.of(env -> s + ":" + env.max())));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("readerKinds") Kind<ReaderKind.Witness<TestEnv>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("readerKinds") Kind<ReaderKind.Witness<TestEnv>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToReaderString") Function<Integer, Kind<ReaderKind.Witness<TestEnv>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("readerKinds") Kind<ReaderKind.Witness<TestEnv>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("readerKinds") Kind<ReaderKind.Witness<TestEnv>, Integer> m,
      @ForAll("intToReaderString") Function<Integer, Kind<ReaderKind.Witness<TestEnv>, String>> f,
      @ForAll("stringToReaderString")
          Function<String, Kind<ReaderKind.Witness<TestEnv>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}
