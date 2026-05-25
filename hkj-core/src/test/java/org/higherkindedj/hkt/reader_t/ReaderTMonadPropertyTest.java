// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
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
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for ReaderT over Optional. Readers are
 * functions, so equality runs both sides against a fixed environment. Fixtures mix value-only,
 * env-dependent, and empty-outer states.
 */
class ReaderTMonadPropertyTest {

  private static final String ENV = "test-env";

  private final Monad<OptionalKind.Witness> outerMonad = Instances.monadError(optional());
  private final Monad<ReaderTKind.Witness<OptionalKind.Witness, String>> readerTMonad =
      Instances.readerT(outerMonad);

  private <A> Optional<A> unwrapKind(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> kind) {
    if (kind == null) return null;
    var readerT = READER_T.narrow(kind);
    Kind<OptionalKind.Witness, A> outerKind = readerT.run().apply(ENV);
    return OPTIONAL.narrow(outerKind);
  }

  private final BiPredicate<
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>,
          Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, ?>>
      eq = KindEquivalence.byEqualsAfter(this::unwrapKind);

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> readerT(A value) {
    return READER_T.widen(ReaderT.reader(outerMonad, env -> value));
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> fromEnv(
      Function<String, A> f) {
    return READER_T.widen(ReaderT.reader(outerMonad, f));
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> emptyT() {
    Kind<OptionalKind.Witness, A> emptyOuter = OPTIONAL.widen(Optional.empty());
    return READER_T.widen(ReaderT.liftF(outerMonad, emptyOuter));
  }

  @Provide
  Arbitrary<Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer>> readerTKinds() {
    // Mix of constant readers, env-dependent readers, and empty-outer Optional states.
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(this.<Integer>emptyT());
              }
              if (i % 4 == 0) {
                return Arbitraries.just(fromEnv(env -> i + env.length()));
              }
              return Arbitraries.just(readerT(i));
            });
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
  Arbitrary<Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> readerT("v:" + i),
        i -> fromEnv(env -> i + ":" + env),
        i -> i == 0 ? emptyT() : readerT(String.valueOf(i)),
        i -> fromEnv(env -> "len=" + (env.length() + i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> readerT(s + "!"),
        s -> fromEnv(env -> s + ":" + env),
        s -> s.isEmpty() ? emptyT() : readerT(s.toUpperCase()));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa) {
    FunctorLaws.assertIdentity(readerTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(readerTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> f) {
    MonadLaws.assertLeftIdentity(readerTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> m) {
    MonadLaws.assertRightIdentity(readerTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("readerTKinds") Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, String>> g) {
    MonadLaws.assertAssociativity(readerTMonad, m, f, g, eq);
  }
}
